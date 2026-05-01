package com.soll.data.local.dao

import androidx.room.*
import com.soll.data.local.entity.CommandLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandLogDao {

    @Query("SELECT * FROM command_logs ORDER BY executed_at DESC")
    fun getAllLogs(): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_logs ORDER BY executed_at DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_logs WHERE command = :command ORDER BY executed_at DESC")
    fun getLogsByCommand(command: String): Flow<List<CommandLogEntity>>

    @Query("SELECT * FROM command_logs WHERE status = :status ORDER BY executed_at DESC")
    fun getLogsByStatus(status: String): Flow<List<CommandLogEntity>>

    @Query("SELECT COUNT(*) FROM command_logs")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM command_logs")
    fun getCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM command_logs WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM command_logs WHERE status = 'success'")
    fun getSuccessCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM command_logs WHERE status = 'error'")
    fun getErrorCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CommandLogEntity): Long

    @Update
    suspend fun update(log: CommandLogEntity)

    @Delete
    suspend fun delete(log: CommandLogEntity)

    @Query("DELETE FROM command_logs")
    suspend fun deleteAll()

    @Query("DELETE FROM command_logs WHERE executed_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Keep only the last N logs
     */
    @Query("DELETE FROM command_logs WHERE id NOT IN (SELECT id FROM command_logs ORDER BY executed_at DESC LIMIT :keepCount)")
    suspend fun keepOnly(keepCount: Int)
}
