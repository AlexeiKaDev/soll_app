package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "scan_items",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "format", "normalized_value"], unique = true),
        Index(value = ["last_scanned_at"]),
    ],
)
data class ScanItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "raw_value")
    val rawValue: String,
    @ColumnInfo(name = "normalized_value")
    val normalizedValue: String,
    val format: String,
    val count: Int = 1,
    @ColumnInfo(name = "first_scanned_at")
    val firstScannedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "exported_at")
    val exportedAt: Long? = null,
)
