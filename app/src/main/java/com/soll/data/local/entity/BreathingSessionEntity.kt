package com.soll.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "breathing_sessions")
data class BreathingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSeconds: Int,
    val completedFully: Boolean,
    /** Сколько полных раундов засчитано на момент окончания (см. логику во ViewModel). */
    val roundsCompleted: Int,
    val holdRecordsCsv: String,
)
