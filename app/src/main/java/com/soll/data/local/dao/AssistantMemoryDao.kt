package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.soll.data.local.entity.AssistantMemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssistantMemoryDao {
    @Query("SELECT * FROM assistant_memories ORDER BY pinned DESC, updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AssistantMemoryEntity>>

    @Query("SELECT * FROM assistant_memories ORDER BY pinned DESC, updated_at DESC")
    suspend fun getAllForExport(): List<AssistantMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: AssistantMemoryEntity)

    @Query("DELETE FROM assistant_memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM assistant_memories")
    suspend fun deleteAll()
}
