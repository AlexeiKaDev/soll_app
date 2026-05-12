package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Log of received messages
 */
@Entity(
    tableName = "message_logs",
    indices = [
        Index(value = ["update_id"]),
        Index(value = ["received_at"]),
        Index(value = ["chat_id", "received_at"]),
    ],
)
data class MessageLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "update_id")
    val updateId: Long,

    @ColumnInfo(name = "message_id")
    val messageId: Long,

    @ColumnInfo(name = "chat_id")
    val chatId: Long,

    @ColumnInfo(name = "chat_type")
    val chatType: String,

    @ColumnInfo(name = "chat_title")
    val chatTitle: String?,

    @ColumnInfo(name = "user_id")
    val userId: Long?,

    @ColumnInfo(name = "username")
    val username: String?,

    @ColumnInfo(name = "user_full_name")
    val userFullName: String?,

    @ColumnInfo(name = "text")
    val text: String?,

    @ColumnInfo(name = "has_document")
    val hasDocument: Boolean = false,

    @ColumnInfo(name = "has_photo")
    val hasPhoto: Boolean = false,

    @ColumnInfo(name = "has_location")
    val hasLocation: Boolean = false,

    @ColumnInfo(name = "message_date")
    val messageDate: Long,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long = System.currentTimeMillis()
)
