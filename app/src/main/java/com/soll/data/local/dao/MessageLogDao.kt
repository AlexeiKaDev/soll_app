package com.soll.data.local.dao

import androidx.room.*
import com.soll.data.local.entity.MessageLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageLogDao {

    @Query("SELECT * FROM message_logs ORDER BY received_at DESC")
    fun getAllLogs(): Flow<List<MessageLogEntity>>

    @Query("SELECT * FROM message_logs ORDER BY received_at DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<MessageLogEntity>>

    @Query("SELECT * FROM message_logs WHERE chat_id = :chatId ORDER BY received_at DESC")
    fun getLogsByChatId(chatId: Long): Flow<List<MessageLogEntity>>

    @Query("SELECT * FROM message_logs WHERE update_id = :updateId LIMIT 1")
    suspend fun getByUpdateId(updateId: Long): MessageLogEntity?

    @Query("SELECT COUNT(*) FROM message_logs")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM message_logs")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT chat_id FROM message_logs ORDER BY received_at DESC LIMIT 1")
    suspend fun getLastChatId(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: MessageLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<MessageLogEntity>)

    @Delete
    suspend fun delete(log: MessageLogEntity)

    @Query("DELETE FROM message_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM message_logs WHERE received_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Keep only the last N logs
     */
    @Query("DELETE FROM message_logs WHERE id NOT IN (SELECT id FROM message_logs ORDER BY received_at DESC LIMIT :keepCount)")
    suspend fun keepOnly(keepCount: Int)
}
