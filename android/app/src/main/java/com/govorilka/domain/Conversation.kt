package com.govorilka.domain

import kotlinx.coroutines.flow.Flow

const val NEW_CONVERSATION_TITLE = "Новый диалог"

private const val TITLE_MAX_LENGTH = 48

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class MessageRole { User, Assistant }

enum class MessageSource { Voice, Text }

data class Message(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val text: String,
    val source: MessageSource,
    val startMs: Long?,
    val endMs: Long?,
    val liveSessionId: String?,
    val createdAt: Long,
)

fun conversationTitle(text: String): String =
    text.trim().take(TITLE_MAX_LENGTH).ifEmpty { NEW_CONVERSATION_TITLE }

interface ConversationStore {
    fun observeAll(): Flow<List<Conversation>>

    fun observe(id: String): Flow<Conversation?>

    suspend fun create(): Conversation

    suspend fun delete(id: String)

    fun observeMessages(conversationId: String): Flow<List<Message>>

    /** Ignores a repeated id. [titleIfStillNew] replaces the title only while it is [NEW_CONVERSATION_TITLE]. */
    suspend fun appendMessage(message: Message, titleIfStillNew: String?)

    /** Stores all of [messages] or none of them; otherwise behaves like [appendMessage]. */
    suspend fun appendMessages(messages: List<Message>, titleIfStillNew: String?)
}
