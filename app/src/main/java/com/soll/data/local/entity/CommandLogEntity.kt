package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Log of executed commands
 */
@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "command")
    val command: String,

    @ColumnInfo(name = "args")
    val args: String?,

    @ColumnInfo(name = "chat_id")
    val chatId: Long,

    @ColumnInfo(name = "user_id")
    val userId: Long?,

    @ColumnInfo(name = "username")
    val username: String?,

    @ColumnInfo(name = "status")
    val status: String, // "success", "error", "pending"

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,

    @ColumnInfo(name = "response_text")
    val responseText: String? = null,

    @ColumnInfo(name = "execution_time_ms")
    val executionTimeMs: Long? = null,

    @ColumnInfo(name = "executed_at")
    val executedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_ERROR = "error"
        const val STATUS_PENDING = "pending"
    }
}
