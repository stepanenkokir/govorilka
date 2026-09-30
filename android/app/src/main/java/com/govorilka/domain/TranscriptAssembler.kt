package com.govorilka.domain

import java.util.UUID

private const val PAUSE_MS = 1000L

data class Utterance(
    val id: String,
    val role: MessageRole,
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/** Immutable: every call returns a new assembler. Each role has its own open utterance. */
data class TranscriptAssembler(
    val closed: List<Utterance> = emptyList(),
    val open: Map<MessageRole, Utterance> = emptyMap(),
) {
    /** Closed and non-blank open utterances ordered by start time. */
    val utterances: List<Utterance>
        get() = (closed + open.values.nonBlank()).sortedBy { it.startMs }

    fun append(fragment: VoiceEvent.Transcript, newId: () -> String = ::randomId): TranscriptAssembler {
        if (fragment.delta.isEmpty()) return this
        val current = open[fragment.role]
        if (current != null && fragment.startMs - current.endMs < PAUSE_MS) {
            val merged = current.copy(
                text = current.text + fragment.delta,
                startMs = minOf(current.startMs, fragment.startMs),
                endMs = maxOf(current.endMs, fragment.endMs),
            )
            return copy(open = open + (fragment.role to merged))
        }
        val started = Utterance(newId(), fragment.role, fragment.delta, fragment.startMs, fragment.endMs)
        return copy(closed = closed + listOfNotNull(current).nonBlank(), open = open + (fragment.role to started))
    }

    fun finish(): TranscriptAssembler = copy(closed = closed + open.values.nonBlank(), open = emptyMap())
}

private fun Collection<Utterance>.nonBlank() = filter { it.text.isNotBlank() }

private fun randomId() = UUID.randomUUID().toString()
