package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govorilka.domain.ApiResult
import com.govorilka.domain.ConversationStore
import com.govorilka.domain.GovorilkaApi
import com.govorilka.domain.Message
import com.govorilka.domain.MessageRole
import com.govorilka.domain.MessageSource
import com.govorilka.domain.SettingsStore
import com.govorilka.domain.TranscriptAssembler
import com.govorilka.domain.Utterance
import com.govorilka.domain.VoiceCall
import com.govorilka.domain.VoiceEvent
import com.govorilka.domain.chatHistory
import com.govorilka.domain.conversationTitle
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SAVE_FAILURE = "Не удалось сохранить сообщение"

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

sealed interface SendError {
    data object MissingSettings : SendError

    data class Failed(val message: String) : SendError
}

class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    private val store: ConversationStore,
    private val settingsStore: SettingsStore,
    private val voiceCall: VoiceCall,
    private val api: GovorilkaApi,
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

    private val _draft = mutableStateOf("")
    private val _sending = mutableStateOf(false)
    private val _sendError = mutableStateOf<SendError?>(null)

    val title: State<String?> = _title
    val draft: State<String> = _draft
    val sending: State<Boolean> = _sending
    val sendError: State<SendError?> = _sendError
    val canSend: State<Boolean> = derivedStateOf { !_sending.value && _draft.value.isNotBlank() }
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

    fun onDraftChange(value: String) {
        if (_sending.value) return
        _draft.value = value
        _sendError.value = null
    }

    fun onSend() {
        if (!canSend.value) return
        val prompt = _draft.value.trim()
        _sending.value = true
        _sendError.value = null
        viewModelScope.launch {
            try {
                _sendError.value = send(prompt)
                if (_sendError.value == null) _draft.value = ""
            } finally {
                _sending.value = false
            }
        }
    }

    /** Returns null once the question and the reply are both stored. */
    private suspend fun send(prompt: String): SendError? {
        val settings = settingsStore.settings.first()
        if (settings.serverBaseUrl.isBlank() || settings.appSecret.isBlank()) return SendError.MissingSettings
        val history = chatHistory(_saved.value, prompt)
        val reply = when (val result = api.sendChat(settings.serverBaseUrl, settings.appSecret, history, settings.instructions)) {
            is ApiResult.Failure -> return SendError.Failed(result.message)
            is ApiResult.Success -> result.value
        }
        val askedAt = System.currentTimeMillis()
        val messages = listOf(
            textMessage(MessageRole.User, prompt, askedAt),
            textMessage(MessageRole.Assistant, reply, askedAt + 1),
        )
        return try {
            store.appendMessages(messages, conversationTitle(prompt))
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SendError.Failed(SAVE_FAILURE)
        }
    }

    private fun textMessage(role: MessageRole, text: String, createdAt: Long) = Message(
        id = UUID.randomUUID().toString(),
        conversationId = conversationId,
        role = role,
        text = text,
        source = MessageSource.Text,
        startMs = null,
        endMs = null,
        liveSessionId = null,
        createdAt = createdAt,
    )

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
