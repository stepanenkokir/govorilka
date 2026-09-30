package com.govorilka.data

import androidx.room3.ColumnTypeConverter
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.govorilka.domain.Conversation
import com.govorilka.domain.Message
import com.govorilka.domain.MessageRole
import com.govorilka.domain.MessageSource

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: MessageRole,
    val text: String,
    val source: MessageSource,
    val startMs: Long?,
    val endMs: Long?,
    val liveSessionId: String?,
    val createdAt: Long,
)

class Converters {
    @ColumnTypeConverter
    fun roleToString(role: MessageRole): String = when (role) {
        MessageRole.User -> "user"
        MessageRole.Assistant -> "assistant"
    }

    @ColumnTypeConverter
    fun stringToRole(value: String): MessageRole = when (value) {
        "user" -> MessageRole.User
        "assistant" -> MessageRole.Assistant
        else -> error("Unknown message role: $value")
    }

    @ColumnTypeConverter
    fun sourceToString(source: MessageSource): String = when (source) {
        MessageSource.Voice -> "voice"
        MessageSource.Text -> "text"
    }

    @ColumnTypeConverter
    fun stringToSource(value: String): MessageSource = when (value) {
        "voice" -> MessageSource.Voice
        "text" -> MessageSource.Text
        else -> error("Unknown message source: $value")
    }
}

fun ConversationEntity.toDomain() = Conversation(id, title, createdAt, updatedAt)

fun Conversation.toEntity() = ConversationEntity(id, title, createdAt, updatedAt)

fun MessageEntity.toDomain() =
    Message(id, conversationId, role, text, source, startMs, endMs, liveSessionId, createdAt)

fun Message.toEntity() =
    MessageEntity(id, conversationId, role, text, source, startMs, endMs, liveSessionId, createdAt)
