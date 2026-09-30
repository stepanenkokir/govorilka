package com.govorilka.data

import androidx.room3.ColumnTypeConverters
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.RoomDatabase
import kotlinx.coroutines.flow.Flow

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
}

@Database(entities = [ConversationEntity::class, MessageEntity::class], version = 1, exportSchema = false)
@ColumnTypeConverters(Converters::class)
abstract class GovorilkaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}
