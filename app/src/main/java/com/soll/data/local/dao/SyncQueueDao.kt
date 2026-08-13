package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.soll.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED', 'RUNNING')")
    fun observeOpenCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'FAILED', 'RUNNING')")
    suspend fun countOpenItems(): Int

    @Query(
        "SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') AND next_attempt_at <= :now " +
            "ORDER BY CASE kind WHEN 'NOTIFICATION_RECEIPT' THEN 0 ELSE 1 END, created_at ASC LIMIT :limit"
    )
    suspend fun getReadyItems(now: Long, limit: Int): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE kind = :kind AND status IN ('PENDING', 'FAILED', 'RUNNING') ORDER BY created_at ASC")
    suspend fun getOpenItemsByKind(kind: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecentItems(limit: Int): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SyncQueueEntity?>

    @Query("SELECT * FROM sync_queue WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SyncQueueEntity?

    @Query(
        "SELECT * FROM sync_queue " +
        "WHERE kind IN ('FEED_IMPORT', 'FEED_FEEDBACK', 'ASSISTANT_FEEDBACK', 'NOTIFICATION_RECEIPT') " +
            "AND status = 'RUNNING' AND updated_at <= :staleBefore " +
            "ORDER BY updated_at ASC"
    )
    suspend fun getStaleRunningDurableDeliveries(staleBefore: Long): List<SyncQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity)

    @Update
    suspend fun update(item: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE status = 'DONE' AND updated_at < :timestamp")
    suspend fun deleteCompletedOlderThan(timestamp: Long)
}
