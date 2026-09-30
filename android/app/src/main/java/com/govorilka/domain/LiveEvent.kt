package com.govorilka.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface ParsedLiveEvent {
    data class Event(val event: VoiceEvent) : ParsedLiveEvent

    data object Skip : ParsedLiveEvent

    data class Malformed(val reason: String) : ParsedLiveEvent
}

private val TRANSCRIPT_ROLES = mapOf(
    "session.input_transcript.delta" to MessageRole.User,
    "session.output_transcript.delta" to MessageRole.Assistant,
)

fun parseLiveEvent(json: String): ParsedLiveEvent {
    val root = try {
        Json.parseToJsonElement(json) as? JsonObject
    } catch (_: SerializationException) {
        null
    } ?: return ParsedLiveEvent.Malformed("not a JSON object")

    val type = root.string("type") ?: return ParsedLiveEvent.Malformed("no type")
    val role = TRANSCRIPT_ROLES[type]
    val event = when {
        role != null -> root.transcript(role)
        type == "session.started" -> (root["session"] as? JsonObject)?.string("id")?.let { VoiceEvent.Connected(it) }
        type == "session.closed" -> VoiceEvent.Closed
        else -> return ParsedLiveEvent.Skip
    }
    return event?.let { ParsedLiveEvent.Event(it) } ?: ParsedLiveEvent.Malformed("$type without required fields")
}

private fun JsonObject.transcript(role: MessageRole): VoiceEvent.Transcript? {
    val delta = string("delta") ?: return null
    val startMs = long("start_ms") ?: return null
    val endMs = long("end_ms") ?: return null
    return VoiceEvent.Transcript(role, delta, startMs, endMs)
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
