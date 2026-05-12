package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.soll.data.local.entity.ToolJobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolJobDao {
    @Query("SELECT * FROM tool_jobs ORDER BY created_at DESC LIMIT :limit")
    fun getRecentJobs(limit: Int = 100): Flow<List<ToolJobEntity>>

    @Query("SELECT * FROM tool_jobs WHERE status = :status ORDER BY updated_at DESC")
    fun getJobsByStatus(status: String): Flow<List<ToolJobEntity>>

    @Query("SELECT * FROM tool_jobs WHERE id = :id")
    suspend fun getJob(id: String): ToolJobEntity?

    @Query("SELECT COUNT(*) FROM tool_jobs WHERE status IN ('QUEUED', 'RUNNING', 'WAITING_FOR_CONFIRMATION')")
    suspend fun countActiveJobs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: ToolJobEntity)

    @Update
    suspend fun update(job: ToolJobEntity)

    @Query("DELETE FROM tool_jobs WHERE created_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM tool_jobs WHERE finished_at IS NOT NULL")
    suspend fun deleteFinishedJobs()

    @Query("DELETE FROM tool_jobs WHERE id NOT IN (SELECT id FROM tool_jobs ORDER BY created_at DESC LIMIT :keepCount)")
    suspend fun keepOnly(keepCount: Int)
}
