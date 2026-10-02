package com.govorilka.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.govorilka.domain.ApiResult
import com.govorilka.domain.GovorilkaApi
import com.govorilka.domain.ParsedLiveEvent
import com.govorilka.domain.Voice
import com.govorilka.domain.VoiceCall
import com.govorilka.domain.VoiceEvent
import com.govorilka.domain.parseLiveEvent
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

private const val EVENTS_CHANNEL = "oai-events"
private const val STUN_SERVER = "stun:stun.l.google.com:19302"
private const val ICE_TIMEOUT_MS = 10_000L
private const val CLOSE_TIMEOUT_MS = 15_000L
private const val SESSION_CLOSE = """{"type":"session.close"}"""
private const val STREAM_ID = "govorilka"

private data class CallRequest(
    val baseUrl: String,
    val appSecret: String,
    val instructions: String,
    val voice: Voice,
    val webSearch: Boolean,
)

private const val CONNECTION_FAILED = "Не удалось установить голосовое соединение"
private const val CONNECTION_LOST = "Голосовое соединение прервалось"
private const val CLOSE_TIMEOUT = "Сервер не подтвердил завершение, микрофон освобождён"

/** Call once per process, after [PeerConnectionFactory.initialize]. */
fun createPeerConnectionFactory(context: Context): PeerConnectionFactory {
    val audioDeviceModule = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseHardwareNoiseSuppressor(true)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .createAudioDeviceModule()
    return PeerConnectionFactory.builder()
        .setAudioDeviceModule(audioDeviceModule)
        .createPeerConnectionFactory()
}

/**
 * All WebRTC calls and all mutable state live on one thread. WebRTC callbacks only hand data
 * over through channels and deferreds.
 */
class WebRtcVoiceCall(
    context: Context,
    private val factory: PeerConnectionFactory,
    private val api: GovorilkaApi,
) : VoiceCall {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val _events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 64)

    private var callJob: Job? = null
    private var hangUp: CompletableDeferred<Unit>? = null
    private var localTrack: AudioTrack? = null

    override val events: Flow<VoiceEvent> = _events.asSharedFlow()

    override fun start(baseUrl: String, appSecret: String, instructions: String, voice: Voice, webSearch: Boolean) {
        val request = CallRequest(baseUrl, appSecret, instructions, voice, webSearch)
        scope.launch {
            hangUp?.complete(Unit)
            callJob?.join()
            val signal = CompletableDeferred<Unit>()
            hangUp = signal
            callJob = scope.launch { runCall(request, signal) }
        }
    }

    override fun setMuted(muted: Boolean) {
        scope.launch { localTrack?.setEnabled(!muted) }
    }

    override fun setSpeakerOn(speakerOn: Boolean) {
        scope.launch { if (localTrack != null) routeToSpeaker(speakerOn) }
    }

    override fun close() {
        scope.launch { hangUp?.complete(Unit) }
    }

    private suspend fun runCall(request: CallRequest, hangUp: CompletableDeferred<Unit>) {
        _events.emit(VoiceEvent.Connecting)
        val inbox = Channel<String>(Channel.UNLIMITED)
        val iceGathered = CompletableDeferred<Unit>()
        val restoreAudio = takeAudioFocus()
        val source = factory.createAudioSource(MediaConstraints())
        val track = factory.createAudioTrack("mic", source)
        val peer = factory.createPeerConnection(rtcConfiguration(), PeerObserver(iceGathered, inbox))
        var channel: DataChannel? = null
        val outcome = try {
            if (peer == null) throw CallFailure(CONNECTION_FAILED)
            peer.addTrack(track, listOf(STREAM_ID))
            localTrack = track
            val events: DataChannel = peer.createDataChannel(EVENTS_CHANNEL, DataChannel.Init())
                ?: throw CallFailure(CONNECTION_FAILED)
            events.registerObserver(ChannelObserver(events, inbox))
            channel = events
            val negotiated = hangUp.raceWith { negotiate(peer, iceGathered, request) }
            if (negotiated == null) VoiceEvent.Closed else listen(inbox, events, hangUp)
        } catch (failure: CallFailure) {
            VoiceEvent.Failed(failure.message)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            // Native WebRTC reports failures as unchecked exceptions; the call must still end with an event.
            VoiceEvent.Failed(CONNECTION_FAILED)
        } finally {
            localTrack = null
            channel?.unregisterObserver()
            channel?.dispose()
            peer?.dispose()
            track.dispose()
            source.dispose()
            restoreAudio()
        }
        _events.emit(outcome)
    }

    /** Throws [CallFailure]; the connection is not live until `session.started` arrives. */
    private suspend fun negotiate(
        peer: PeerConnection,
        iceGathered: CompletableDeferred<Unit>,
        request: CallRequest,
    ) {
        val offer = peer.awaitOffer()
        awaitSet { peer.setLocalDescription(it, offer) }
        withTimeoutOrNull(ICE_TIMEOUT_MS) { iceGathered.await() }
        val sdp = peer.localDescription?.description ?: throw CallFailure(CONNECTION_FAILED)
        val result = with(request) { api.createSession(baseUrl, appSecret, sdp, instructions, voice, webSearch) }
        when (result) {
            is ApiResult.Failure -> throw CallFailure(result.message)
            is ApiResult.Success -> {
                val answer = SessionDescription(SessionDescription.Type.ANSWER, result.value.sdp)
                awaitSet { peer.setRemoteDescription(it, answer) }
            }
        }
    }

    private suspend fun listen(
        inbox: ReceiveChannel<String>,
        channel: DataChannel,
        hangUp: CompletableDeferred<Unit>,
    ): VoiceEvent {
        var started = false
        when (hangUp.raceWith { receiveUntilClosed(inbox) { started = true } }) {
            true -> return VoiceEvent.Closed
            false -> return VoiceEvent.Failed(CONNECTION_LOST)
            null -> if (!started || channel.state() != DataChannel.State.OPEN) return VoiceEvent.Closed
        }
        channel.send(DataChannel.Buffer(ByteBuffer.wrap(SESSION_CLOSE.toByteArray()), false))
        val confirmed = withTimeoutOrNull(CLOSE_TIMEOUT_MS) { receiveUntilClosed(inbox) {} }
        return if (confirmed == null) VoiceEvent.Failed(CLOSE_TIMEOUT) else VoiceEvent.Closed
    }

    /** True on `session.closed`, false when the connection drops first. */
    private suspend fun receiveUntilClosed(inbox: ReceiveChannel<String>, onStarted: () -> Unit): Boolean {
        for (text in inbox) {
            val event = (parseLiveEvent(text) as? ParsedLiveEvent.Event)?.event ?: continue
            if (event == VoiceEvent.Closed) return true
            if (event is VoiceEvent.Connected) onStarted()
            _events.emit(event)
        }
        return false
    }

    private fun takeAudioFocus(): () -> Unit {
        val previousMode = audioManager.mode
        @Suppress("DEPRECATION")
        val previousSpeaker = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeToSpeaker(true)
        return {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = previousSpeaker
            }
            audioManager.mode = previousMode
        }
    }

    private fun routeToSpeaker(speakerOn: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val type = if (speakerOn) AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            audioManager.availableCommunicationDevices.firstOrNull { it.type == type }
                ?.let(audioManager::setCommunicationDevice)
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerOn
        }
    }
}

private class CallFailure(override val message: String) : Exception(message)

private fun rtcConfiguration() = PeerConnection.RTCConfiguration(
    listOf(PeerConnection.IceServer.builder(STUN_SERVER).createIceServer()),
)

/** Runs [block] until it finishes or this deferred completes; null means the hang-up won. */
private suspend fun <T> CompletableDeferred<Unit>.raceWith(block: suspend () -> T): T? = coroutineScope {
    val work = async { block() }
    select {
        work.onAwait { it }
        onAwait {
            work.cancel()
            null
        }
    }
}

private suspend fun PeerConnection.awaitOffer(): SessionDescription = suspendCancellableCoroutine { continuation ->
    createOffer(
        object : SdpCallbacks() {
            override fun onCreateSuccess(description: SessionDescription) = continuation.resume(description)

            override fun onCreateFailure(error: String?) =
                continuation.resumeWithException(CallFailure(CONNECTION_FAILED))
        },
        MediaConstraints(),
    )
}

private suspend fun awaitSet(set: (SdpObserver) -> Unit): Unit = suspendCancellableCoroutine { continuation ->
    set(
        object : SdpCallbacks() {
            override fun onSetSuccess() = continuation.resume(Unit)

            override fun onSetFailure(error: String?) =
                continuation.resumeWithException(CallFailure(CONNECTION_FAILED))
        },
    )
}

private abstract class SdpCallbacks : SdpObserver {
    override fun onCreateSuccess(description: SessionDescription) = Unit

    override fun onSetSuccess() = Unit

    override fun onCreateFailure(error: String?) = Unit

    override fun onSetFailure(error: String?) = Unit
}

/** Closing [inbox] tells the call that the connection is gone. */
private class PeerObserver(
    private val iceGathered: CompletableDeferred<Unit>,
    private val inbox: Channel<String>,
) : PeerConnection.Observer {
    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
        if (state == PeerConnection.IceGatheringState.COMPLETE) iceGathered.complete(Unit)
    }

    override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
        if (state == PeerConnection.PeerConnectionState.FAILED) inbox.close()
    }

    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit

    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

    override fun onIceCandidate(candidate: IceCandidate) = Unit

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit

    override fun onAddStream(stream: MediaStream) = Unit

    override fun onRemoveStream(stream: MediaStream) = Unit

    override fun onDataChannel(channel: DataChannel) = Unit

    override fun onRenegotiationNeeded() = Unit
}

private class ChannelObserver(
    private val channel: DataChannel,
    private val inbox: Channel<String>,
) : DataChannel.Observer {
    override fun onMessage(buffer: DataChannel.Buffer) {
        if (buffer.binary) return
        val bytes = ByteArray(buffer.data.remaining()).also { buffer.data.get(it) }
        inbox.trySend(bytes.decodeToString())
    }

    override fun onStateChange() {
        if (channel.state() == DataChannel.State.CLOSED) inbox.close()
    }

    override fun onBufferedAmountChange(previousAmount: Long) = Unit
}
