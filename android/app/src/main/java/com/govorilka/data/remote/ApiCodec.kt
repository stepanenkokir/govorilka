package com.govorilka.data.remote

import com.govorilka.domain.ApiResult
import com.govorilka.domain.ChatLine
import com.govorilka.domain.LiveSession
import com.govorilka.domain.MessageRole
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal const val SESSION_PATH = "/api/session"
internal const val CHAT_PATH = "/api/chat"

internal const val MISSING_BASE_URL = "Адрес сервера не задан"
internal const val INVALID_BASE_URL = "Некорректный адрес сервера"
internal const val INVALID_SECRET = "Недопустимые символы в секрете"
internal const val NETWORK_FAILURE = "Нет связи с сервером"
internal const val UNEXPECTED_RESPONSE = "Неожиданный ответ сервера"

internal fun serverFailure(code: Int) = "Ошибка сервера ($code)"

private const val WEBRTC_TRANSPORT = "webrtc"

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
private data class SessionRequest(val sdp: String, val instructions: String?)

@Serializable
private data class ChatRequest(val messages: List<ChatMessageDto>, val instructions: String?)

@Serializable
private data class ChatMessageDto(val role: String, val content: String)

@Serializable
private data class SessionResponse(val session: SessionDto, val transport: TransportDto)

@Serializable
private data class SessionDto(val id: String)

@Serializable
private data class TransportDto(val type: String, val sdp: String)

@Serializable
private data class ChatResponse(val text: String)

@Serializable
private data class ErrorResponse(val error: String?)

internal fun endpoint(baseUrl: String, path: String): HttpUrl? =
    "${baseUrl.trim().trimEnd('/')}$path".toHttpUrlOrNull()

/** Throws [IllegalArgumentException] when [appSecret] is not a valid header value. */
internal fun buildRequest(url: HttpUrl, appSecret: String, body: String): Request =
    Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $appSecret")
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .build()

internal fun sessionBody(sdp: String, instructions: String): String =
    json.encodeToString(SessionRequest(sdp, instructions.orNullIfBlank()))

internal fun chatBody(messages: List<ChatLine>, instructions: String): String =
    json.encodeToString(
        ChatRequest(
            messages = messages.map { ChatMessageDto(it.role.wireName(), it.content) },
            instructions = instructions.orNullIfBlank(),
        ),
    )

internal fun parseSession(code: Int, body: String): ApiResult<LiveSession> =
    parse<SessionResponse, LiveSession>(code, body) { response ->
        response.takeIf { it.transport.type == WEBRTC_TRANSPORT }
            ?.let { LiveSession(it.session.id, it.transport.sdp) }
    }

internal fun parseChat(code: Int, body: String): ApiResult<String> =
    parse<ChatResponse, String>(code, body) { it.text }

private inline fun <reified R, T : Any> parse(code: Int, body: String, map: (R) -> T?): ApiResult<T> {
    if (code !in 200..299) {
        val message = decodeOrNull<ErrorResponse>(body)?.error?.takeIf { it.isNotBlank() }
        return ApiResult.Failure(message ?: serverFailure(code))
    }
    val value = decodeOrNull<R>(body)?.let(map) ?: return ApiResult.Failure(UNEXPECTED_RESPONSE)
    return ApiResult.Success(value)
}

private inline fun <reified R> decodeOrNull(body: String): R? =
    try {
        json.decodeFromString<R>(body)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun String.orNullIfBlank(): String? = ifBlank { null }

private fun MessageRole.wireName(): String = when (this) {
    MessageRole.User -> "user"
    MessageRole.Assistant -> "assistant"
}
