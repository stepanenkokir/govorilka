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

fun conversationTitle(text: String): String =
    text.trim().take(TITLE_MAX_LENGTH).ifEmpty { NEW_CONVERSATION_TITLE }

interface ConversationStore {
    fun observeAll(): Flow<List<Conversation>>

    fun observe(id: String): Flow<Conversation?>

    suspend fun create(): Conversation

    suspend fun delete(id: String)
}
