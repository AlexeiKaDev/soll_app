package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.soll.data.local.entity.BreathingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreathingSessionDao {

    @Insert
    suspend fun insert(session: BreathingSessionEntity): Long

    @Query(
        """
        SELECT * FROM breathing_sessions
        ORDER BY endedAtMillis DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<BreathingSessionEntity>>

    @Query(
        """
        SELECT * FROM breathing_sessions
        WHERE endedAtMillis >= :sinceMillis
        ORDER BY endedAtMillis ASC
        """
    )
    suspend fun sessionsEndedSince(sinceMillis: Long): List<BreathingSessionEntity>
}
