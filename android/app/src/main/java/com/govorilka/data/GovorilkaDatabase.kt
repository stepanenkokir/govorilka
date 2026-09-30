package com.govorilka.data

import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

private const val NOT_INSERTED = -1L

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Insert
    suspend fun insert(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query(
        """
        UPDATE conversations
        SET updatedAt = :updatedAt,
            title = CASE WHEN title = :placeholder AND :newTitle IS NOT NULL THEN :newTitle ELSE title END
        WHERE id = :id
        """,
    )
    suspend fun touch(id: String, updatedAt: Long, newTitle: String?, placeholder: String)

    @Transaction
    suspend fun appendMessage(message: MessageEntity, newTitle: String?, placeholder: String) {
        if (insertMessage(message) == NOT_INSERTED) return
        touch(message.conversationId, message.createdAt, newTitle, placeholder)
    }
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1, exportSchema = false)
@ColumnTypeConverters(Converters::class)
abstract class GovorilkaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}
