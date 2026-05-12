package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.soll.domain.notes.NoteSyncStatus
import java.util.UUID

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["updated_at"]),
        Index(value = ["created_at"]),
        Index(value = ["sync_status"]),
        Index(value = ["pinned"]),
        Index(value = ["archived"]),
        Index(value = ["deleted"]),
    ],
)
data class NoteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val title: String,
    val content: String,

    @ColumnInfo(name = "tags_json")
    val tagsJson: String = "[]",

    @ColumnInfo(name = "color_key")
    val colorKey: String = "default",

    val pinned: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = NoteSyncStatus.DRAFT.storageKey,

    @ColumnInfo(name = "sync_attempts")
    val syncAttempts: Int = 0,

    @ColumnInfo(name = "next_sync_attempt_at")
    val nextSyncAttemptAt: Long = 0L,

    @ColumnInfo(name = "synced_filename")
    val syncedFilename: String? = null,

    @ColumnInfo(name = "synced_path")
    val syncedPath: String? = null,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "source")
    val source: String = SOURCE_MANUAL,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_at")
    val syncedAt: Long? = null,
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_TELEGRAM = "telegram"
        const val SOURCE_VOICE = "voice"
    }
}

@Entity(
    tableName = "note_attachments",
    indices = [
        Index(value = ["note_id"]),
        Index(value = ["sync_status"]),
        Index(value = ["created_at"]),
    ],
)
data class NoteAttachmentEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "note_id")
    val noteId: String,

    @ColumnInfo(name = "local_path")
    val localPath: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long? = null,

    @ColumnInfo(name = "sync_status")
    val syncStatus: String = NoteSyncStatus.QUEUED.storageKey,

    @ColumnInfo(name = "sync_attempts")
    val syncAttempts: Int = 0,

    @ColumnInfo(name = "next_sync_attempt_at")
    val nextSyncAttemptAt: Long = 0L,

    @ColumnInfo(name = "uploaded_filename")
    val uploadedFilename: String? = null,

    @ColumnInfo(name = "uploaded_path")
    val uploadedPath: String? = null,

    @ColumnInfo(name = "last_error")
    val lastError: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
