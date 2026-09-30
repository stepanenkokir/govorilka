package com.govorilka.domain

data class LiveSession(val id: String, val sdp: String)

data class ChatLine(val role: MessageRole, val content: String)

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
