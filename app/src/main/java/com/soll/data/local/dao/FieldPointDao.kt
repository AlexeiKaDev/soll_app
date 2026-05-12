package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.soll.data.local.entity.FieldPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldPointDao {
    @Query(
        """
        SELECT * FROM field_points
        ORDER BY
            CASE status
                WHEN 'active' THEN 0
                WHEN 'planned' THEN 1
                WHEN 'done' THEN 2
                ELSE 3
            END,
            updated_at DESC
        """
    )
    fun observeAll(): Flow<List<FieldPointEntity>>

    @Query("SELECT * FROM field_points WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FieldPointEntity?

    @Query("SELECT COUNT(*) FROM field_points")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(point: FieldPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(points: List<FieldPointEntity>)

    @Query(
        """
        UPDATE field_points
        SET status = :status,
            updated_at = :updatedAt,
            visited_at = :visitedAt
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: Long,
        visitedAt: Long?,
    )

    @Query("DELETE FROM field_points WHERE id = :id")
    suspend fun delete(id: String)
}
