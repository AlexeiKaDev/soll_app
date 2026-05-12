package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.soll.data.local.entity.ScanItemEntity
import com.soll.data.local.entity.ScanSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_sessions ORDER BY updated_at DESC")
    fun observeSessions(): Flow<List<ScanSessionEntity>>

    @Query("SELECT * FROM scan_sessions ORDER BY updated_at DESC LIMIT 1")
    suspend fun getLatestSession(): ScanSessionEntity?

    @Query("SELECT * FROM scan_items WHERE session_id = :sessionId ORDER BY last_scanned_at DESC")
    fun observeItems(sessionId: String): Flow<List<ScanItemEntity>>

    @Query("SELECT * FROM scan_items WHERE id IN (:ids)")
    suspend fun getItemsByIds(ids: List<String>): List<ScanItemEntity>

    @Query(
        """
        SELECT * FROM scan_items
        WHERE session_id = :sessionId AND format = :format AND normalized_value = :normalizedValue
        LIMIT 1
        """
    )
    suspend fun getDuplicate(
        sessionId: String,
        format: String,
        normalizedValue: String,
    ): ScanItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ScanSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ScanItemEntity)

    @Update
    suspend fun updateItem(item: ScanItemEntity)

    @Query("UPDATE scan_sessions SET updated_at = :updatedAt WHERE id = :sessionId")
    suspend fun touchSession(sessionId: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE scan_items SET exported_at = :exportedAt WHERE id IN (:ids)")
    suspend fun markExported(ids: List<String>, exportedAt: Long = System.currentTimeMillis())
}
