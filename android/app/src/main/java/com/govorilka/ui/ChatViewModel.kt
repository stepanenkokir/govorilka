package com.govorilka.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.govorilka.domain.ConversationStore
import com.govorilka.domain.SettingsStore
import com.govorilka.domain.TranscriptAssembler
import com.govorilka.domain.VoiceCall
import com.govorilka.domain.VoiceEvent
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
    store: ConversationStore,
    private val settingsStore: SettingsStore,
    private val voiceCall: VoiceCall,
) : ViewModel() {
    private val conversationId: String = checkNotNull(savedStateHandle[Routes.CONVERSATION_ID])

    private val _title = mutableStateOf<String?>(null)
    private val _phase = mutableStateOf<CallPhase>(CallPhase.Idle)
    private val _muted = mutableStateOf(false)
    private val _speakerOn = mutableStateOf(true)
    private val _transcript = mutableStateOf(TranscriptAssembler())

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
        _transcript.value.utterances.map { FeedItem(it.id, it.role, it.text) }
    }

    init {
        viewModelScope.launch {
            store.observe(conversationId).collect { _title.value = it?.title }
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
    }

    private fun startCall() {
        _phase.value = CallPhase.Connecting
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
            is VoiceEvent.Connected -> if (_phase.value != CallPhase.Closing) _phase.value = CallPhase.Listening
            is VoiceEvent.Transcript -> _transcript.value = _transcript.value.append(event)
            is VoiceEvent.Failed -> endCall(CallPhase.Failed(event.message))
            VoiceEvent.Closed -> endCall(CallPhase.Idle)
        }
    }

    private fun endCall(phase: CallPhase) {
        ownsCall = false
        _transcript.value = _transcript.value.finish()
        _phase.value = phase
    }
}
