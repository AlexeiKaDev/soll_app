package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["kind"]),
        Index(value = ["next_attempt_at"]),
        Index(value = ["created_at"]),
        Index(value = ["updated_at"]),
        Index(value = ["status", "next_attempt_at", "created_at"]),
    ],
)
data class SyncQueueEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "kind")
    val kind: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "attempts")
    val attempts: Int,

    @ColumnInfo(name = "last_error")
    val lastError: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "next_attempt_at")
    val nextAttemptAt: Long,
) {
    companion object {
        const val KIND_RAW_NOTE = "RAW_NOTE"
        const val KIND_RAW_FILE = "RAW_FILE"
        const val KIND_TASK_ACTION = "TASK_ACTION"
        const val KIND_FEED_IMPORT = "FEED_IMPORT"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_REJECTED = "REJECTED"
    }
}
