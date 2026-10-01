package com.govorilka.domain

data class LiveSession(val id: String, val sdp: String)

data class ChatLine(val role: MessageRole, val content: String)

const val CHAT_HISTORY_LIMIT = 40

/** Saved voice and text messages in feed order, then [prompt]; the server requires the last line from the user. */
fun chatHistory(saved: List<Message>, prompt: String): List<ChatLine> =
    (saved.filter { it.text.isNotBlank() }.map { ChatLine(it.role, it.text.trim()) } +
        ChatLine(MessageRole.User, prompt)).takeLast(CHAT_HISTORY_LIMIT)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val message: String) : ApiResult<Nothing>
}

interface GovorilkaApi {
    suspend fun createSession(
        baseUrl: String,
        appSecret: String,
        sdp: String,
        instructions: String,
    ): ApiResult<LiveSession>

    suspend fun sendChat(
        baseUrl: String,
        appSecret: String,
        messages: List<ChatLine>,
        instructions: String,
    ): ApiResult<String>
}
