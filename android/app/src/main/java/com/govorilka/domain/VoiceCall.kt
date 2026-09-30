package com.govorilka.domain

import kotlinx.coroutines.flow.Flow

sealed interface VoiceEvent {
    data object Connecting : VoiceEvent

    data class Connected(val sessionId: String) : VoiceEvent

    data class Transcript(val role: MessageRole, val delta: String, val startMs: Long, val endMs: Long) : VoiceEvent

    data class Failed(val message: String) : VoiceEvent

    data object Closed : VoiceEvent
}

/** One call at a time. Every call ends with exactly one [VoiceEvent.Closed] or [VoiceEvent.Failed]. */
interface VoiceCall {
    val events: Flow<VoiceEvent>

    fun start(baseUrl: String, appSecret: String, instructions: String)

    fun setMuted(muted: Boolean)

    fun setSpeakerOn(speakerOn: Boolean)

    /** Idempotent. After `session.started` it waits up to 15 s for `session.closed`, then frees the microphone. */
    fun close()
}
