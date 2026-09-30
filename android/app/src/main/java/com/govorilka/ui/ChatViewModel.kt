package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govorilka.domain.ConversationStore
import com.govorilka.domain.Message
import com.govorilka.domain.MessageRole
import com.govorilka.domain.MessageSource
import com.govorilka.domain.SettingsStore
import com.govorilka.domain.TranscriptAssembler
import com.govorilka.domain.Utterance
import com.govorilka.domain.VoiceCall
import com.govorilka.domain.VoiceEvent
import com.govorilka.domain.conversationTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface CallPhase {
    data object Idle : CallPhase

    data object Connecting : CallPhase

    data object Listening : CallPhase

    data object Closing : CallPhase

    data object MissingSettings : CallPhase

    data object MicDenied : CallPhase

    data class Failed(val message: String) : CallPhase
}

val CallPhase.isActive: Boolean
    get() = this == CallPhase.Connecting || this == CallPhase.Listening

class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val store: ConversationStore,
    private val settingsStore: SettingsStore,
    private val voiceCall: VoiceCall,
    private val writeScope: CoroutineScope,
) : ViewModel() {
    private val conversationId: String = checkNotNull(savedStateHandle[Routes.CONVERSATION_ID])

    private val _title = mutableStateOf<String?>(null)
    private val _phase = mutableStateOf<CallPhase>(CallPhase.Idle)
    private val _muted = mutableStateOf(false)
    private val _speakerOn = mutableStateOf(true)
    private val _transcript = mutableStateOf(TranscriptAssembler())
    private val _saved = mutableStateOf(emptyList<Message>())
    private val savedIds = derivedStateOf { _saved.value.mapTo(HashSet()) { it.id } }

    /** Ids handed to [writeScope] and not yet seen in [_saved]; keeps one insert per utterance. */
    private val inFlight = mutableSetOf<String>()
    private var liveSessionId: String? = null

    /** The shared [VoiceCall] may still be finishing another screen's call; its events are not ours. */
    private var awaitingCall = false
    private var ownsCall = false

    val title: State<String?> = _title
    val draft: State<String> = mutableStateOf("")
    val inputEnabled: State<Boolean> = mutableStateOf(false)
    val micEnabled: State<Boolean> = derivedStateOf { _phase.value != CallPhase.Closing }
    val phase: State<CallPhase> = _phase
    val muted: State<Boolean> = _muted
    val speakerOn: State<Boolean> = _speakerOn
    val feed: State<List<FeedItem>> = derivedStateOf {
        _saved.value.map { FeedItem(it.id, it.role, it.text) } +
            _transcript.value.feedTail(savedIds.value).map { FeedItem(it.id, it.role, it.text) }
    }

    init {
        viewModelScope.launch {
            store.observe(conversationId).collect { _title.value = it?.title }
        }
        viewModelScope.launch {
            store.observeMessages(conversationId).collect {
                _saved.value = it
                inFlight -= savedIds.value
            }
        }
        viewModelScope.launch {
            voiceCall.events.collect(::onVoiceEvent)
        }
    }

    fun onDraftChange(value: String) = Unit

    fun onSend() = Unit

    fun onMicClick() {
        when {
            _phase.value == CallPhase.Closing -> Unit
            _phase.value.isActive && (awaitingCall || ownsCall) -> {
                _phase.value = CallPhase.Closing
                voiceCall.close()
            }
            _phase.value.isActive -> _phase.value = CallPhase.Idle
            else -> startCall()
        }
    }

    fun onMicPermissionDenied() {
        _phase.value = CallPhase.MicDenied
    }

    fun onMuteToggle() {
        _muted.value = !_muted.value
        voiceCall.setMuted(_muted.value)
    }

    fun onSpeakerToggle() {
        _speakerOn.value = !_speakerOn.value
        voiceCall.setSpeakerOn(_speakerOn.value)
    }

    override fun onCleared() {
        if (awaitingCall || ownsCall) voiceCall.close()
        _transcript.value = _transcript.value.finish()
        persistUnsaved()
    }

    private fun startCall() {
        _phase.value = CallPhase.Connecting
        liveSessionId = null
        _muted.value = false
        _speakerOn.value = true
        viewModelScope.launch {
            val settings = settingsStore.settings.first()
            if (_phase.value != CallPhase.Connecting) return@launch
            if (settings.serverBaseUrl.isBlank() || settings.appSecret.isBlank()) {
                _phase.value = CallPhase.MissingSettings
                return@launch
            }
            awaitingCall = true
            voiceCall.start(settings.serverBaseUrl, settings.appSecret, settings.instructions)
        }
    }

    private fun onVoiceEvent(event: VoiceEvent) {
        if (event == VoiceEvent.Connecting && awaitingCall) {
            awaitingCall = false
            ownsCall = true
        }
        if (!ownsCall) return
        when (event) {
            VoiceEvent.Connecting -> if (_phase.value == CallPhase.Closing) voiceCall.close()
            is VoiceEvent.Connected -> {
                liveSessionId = event.sessionId
                if (_phase.value != CallPhase.Closing) _phase.value = CallPhase.Listening
            }
            is VoiceEvent.Transcript -> {
                _transcript.value = _transcript.value.append(event)
                persistUnsaved()
            }
            is VoiceEvent.Failed -> endCall(CallPhase.Failed(event.message))
            VoiceEvent.Closed -> endCall(CallPhase.Idle)
        }
    }

    private fun endCall(phase: CallPhase) {
        ownsCall = false
        _transcript.value = _transcript.value.finish()
        _phase.value = phase
        persistUnsaved()
    }

    /** A failed insert keeps the utterance on the feed and is retried on the next call of this function. */
    private fun persistUnsaved() {
        val pending = _transcript.value.unsavedClosed(savedIds.value).filter { it.id !in inFlight }
        if (pending.isEmpty()) return
        val ids = pending.map { it.id }
        inFlight += ids
        val closedAt = System.currentTimeMillis()
        val messages = pending.mapIndexed { index, utterance -> utterance.toMessage(closedAt + index) }
        writeScope.launch {
            try {
                messages.forEach { store.appendMessage(it, it.titleCandidate()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                inFlight -= ids
            }
        }
    }

    private fun Utterance.toMessage(createdAt: Long) = Message(
        id = id,
        conversationId = conversationId,
        role = role,
        text = text,
        source = MessageSource.Voice,
        startMs = startMs,
        endMs = endMs,
        liveSessionId = liveSessionId,
        createdAt = createdAt,
    )

    private fun Message.titleCandidate(): String? =
        if (role == MessageRole.User) conversationTitle(text) else null
}
