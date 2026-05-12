package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.soll.data.local.entity.NoteAttachmentEntity
import com.soll.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        """
        SELECT * FROM notes
        WHERE deleted = 0
            AND (:archivedOnly = 0 OR archived = 1)
            AND (:includeArchived = 1 OR archived = 0)
            AND (:pinnedOnly = 0 OR pinned = 1)
            AND (:unsentOnly = 0 OR sync_status IN ('draft', 'queued', 'syncing', 'error'))
            AND (:errorsOnly = 0 OR sync_status = 'error')
            AND (
                :query = ''
                OR title LIKE '%' || :query || '%'
                OR content LIKE '%' || :query || '%'
                OR tags_json LIKE '%' || :query || '%'
            )
        ORDER BY
            pinned DESC,
            CASE WHEN :sortKey = 'created' THEN created_at ELSE updated_at END DESC
        """
    )
    fun observeNotes(
        query: String,
        sortKey: String,
        includeArchived: Boolean,
        archivedOnly: Boolean,
        pinnedOnly: Boolean,
        unsentOnly: Boolean,
        errorsOnly: Boolean,
    ): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id AND deleted = 0 LIMIT 1")
    fun observeNote(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id AND deleted = 0 LIMIT 1")
    suspend fun getNote(id: String): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE deleted = 0
            AND sync_status IN ('queued', 'error')
            AND next_sync_attempt_at <= :now
        ORDER BY updated_at ASC
        LIMIT :limit
        """
    )
    suspend fun getReadyNotes(now: Long, limit: Int): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0 AND sync_status IN ('draft', 'queued', 'syncing', 'error')")
    fun observeOpenSyncCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDeleteNote(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET archived = :archived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET pinned = :pinned, updated_at = :updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM note_attachments WHERE note_id = :noteId ORDER BY created_at ASC")
    fun observeAttachments(noteId: String): Flow<List<NoteAttachmentEntity>>

    @Query("SELECT * FROM note_attachments WHERE id = :id LIMIT 1")
    suspend fun getAttachment(id: String): NoteAttachmentEntity?

    @Query(
        """
        SELECT * FROM note_attachments
        WHERE sync_status IN ('queued', 'error')
            AND next_sync_attempt_at <= :now
        ORDER BY created_at ASC
        LIMIT :limit
        """
    )
    suspend fun getReadyAttachments(now: Long, limit: Int): List<NoteAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: NoteAttachmentEntity)

    @Update
    suspend fun updateAttachment(attachment: NoteAttachmentEntity)
}
