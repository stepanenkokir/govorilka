package com.govorilka.data

import com.govorilka.domain.Conversation
import com.govorilka.domain.ConversationStore
import com.govorilka.domain.NEW_CONVERSATION_TITLE
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomConversationStore(private val dao: ConversationDao) : ConversationStore {
    override fun observeAll(): Flow<List<Conversation>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: String): Flow<Conversation?> =
        dao.observe(id).map { it?.toDomain() }

    override suspend fun create(): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(UUID.randomUUID().toString(), NEW_CONVERSATION_TITLE, now, now)
        dao.insert(conversation.toEntity())
        return conversation
    }

    override suspend fun delete(id: String) = dao.delete(id)
}
