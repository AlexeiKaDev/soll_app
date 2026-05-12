package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.soll.data.local.entity.AssistantEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantEventDao {
    @Query("SELECT * FROM assistant_events ORDER BY created_at DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<AssistantEventEntity>>

    @Query("SELECT * FROM assistant_events ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentEventsSnapshot(limit: Int = 100): List<AssistantEventEntity>

    @Query("SELECT COUNT(*) FROM assistant_events")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AssistantEventEntity): Long

    @Query("DELETE FROM assistant_events")
    suspend fun deleteAll()

    @Query("DELETE FROM assistant_events WHERE created_at < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
