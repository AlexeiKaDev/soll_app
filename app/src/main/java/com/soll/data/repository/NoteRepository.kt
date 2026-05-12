package com.soll.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.soll.data.local.dao.NoteDao
import com.soll.data.local.entity.NoteAttachmentEntity
import com.soll.data.local.entity.NoteEntity
import com.soll.domain.notes.NoteFilter
import com.soll.domain.notes.NoteSettings
import com.soll.domain.notes.NoteSort
import com.soll.domain.notes.NoteSyncStatus
import com.soll.domain.notes.NoteTextNormalizer
import com.soll.domain.soll.SollGateway
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class NoteListItem(
    val id: String,
    val title: String,
    val content: String,
    val snippet: String,
    val tags: List<String>,
    val colorKey: String,
    val pinned: Boolean,
    val archived: Boolean,
    val syncStatus: NoteSyncStatus,
    val syncedFilename: String?,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class NoteCaptureResult(
    val noteId: String,
    val syncStatus: NoteSyncStatus,
    val filename: String? = null,
    val errorMessage: String? = null,
) {
    val queued: Boolean
        get() = syncStatus == NoteSyncStatus.QUEUED || syncStatus == NoteSyncStatus.ERROR
}

data class NoteSyncSummary(
    val processed: Int,
    val succeeded: Int,
    val failed: Int,
) {
    companion object {
        val Empty = NoteSyncSummary(processed = 0, succeeded = 0, failed = 0)
    }
}

@Singleton
class NoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val settingsRepository: SettingsRepository,
    private val sollGateway: SollGateway,
) {
    fun observeNotes(
        filter: NoteFilter,
        query: String,
        sort: NoteSort,
    ): Flow<List<NoteListItem>> {
        val flags = filter.flags()
        return noteDao.observeNotes(
            query = query.trim(),
            sortKey = sort.storageKey,
            includeArchived = flags.includeArchived,
            archivedOnly = flags.archivedOnly,
            pinnedOnly = flags.pinnedOnly,
            unsentOnly = flags.unsentOnly,
            errorsOnly = flags.errorsOnly,
        ).map { notes -> notes.map { it.toListItem() } }
    }

    fun observeOpenSyncCount(): Flow<Int> =
        noteDao.observeOpenSyncCount()

    suspend fun getNote(id: String): NoteListItem? = withContext(Dispatchers.IO) {
        noteDao.getNote(id)?.toListItem()
    }

    suspend fun upsertNote(
        id: String?,
        title: String,
        content: String,
        tagsInput: String,
        pinned: Boolean,
        archived: Boolean,
        colorKey: String = "default",
        source: String = NoteEntity.SOURCE_MANUAL,
        queueForSync: Boolean = settingsRepository.getNoteSettings().autoSync,
    ): NoteCaptureResult = withContext(Dispatchers.IO) {
        val cleanContent = content.trim()
        require(cleanContent.isNotBlank()) { "Введите текст заметки" }

        val existing = id?.let { noteDao.getNote(it) }
        val now = System.currentTimeMillis()
        val resolvedTitle = NoteTextNormalizer.deriveTitle(title, cleanContent)
        val tags = mergedTags(tagsInput, cleanContent, settingsRepository.getNoteSettings())
        val nextStatus = if (queueForSync) {
            NoteSyncStatus.QUEUED
        } else {
            NoteSyncStatus.DRAFT
        }

        val note = existing?.copy(
            title = resolvedTitle,
            content = cleanContent,
            tagsJson = tags.toTagsJson(),
            colorKey = colorKey,
            pinned = pinned,
            archived = archived,
            syncStatus = nextStatus.storageKey,
            nextSyncAttemptAt = 0L,
            lastError = null,
            updatedAt = now,
        ) ?: NoteEntity(
            id = id ?: UUID.randomUUID().toString(),
            title = resolvedTitle,
            content = cleanContent,
            tagsJson = tags.toTagsJson(),
            colorKey = colorKey,
            pinned = pinned,
            archived = archived,
            syncStatus = nextStatus.storageKey,
            source = source,
            createdAt = now,
            updatedAt = now,
        )

        if (existing == null) {
            noteDao.insertNote(note)
        } else {
            noteDao.updateNote(note)
        }

        if (queueForSync) enqueueSyncWorker()

        NoteCaptureResult(
            noteId = note.id,
            syncStatus = nextStatus,
        )
    }

    suspend fun captureAndSend(
        title: String,
        content: String,
        tags: List<String>,
        source: String,
    ): NoteCaptureResult {
        val result = upsertNote(
            id = null,
            title = title,
            content = content,
            tagsInput = tags.joinToString(", "),
            pinned = false,
            archived = false,
            source = source,
            queueForSync = true,
        )
        return sendNoteNow(result.noteId)
    }

    suspend fun sendNoteNow(id: String): NoteCaptureResult = withContext(Dispatchers.IO) {
        val note = noteDao.getNote(id) ?: error("Заметка не найдена")
        noteDao.updateNote(
            note.copy(
                syncStatus = NoteSyncStatus.QUEUED.storageKey,
                nextSyncAttemptAt = 0L,
                lastError = null,
                updatedAt = System.currentTimeMillis(),
            )
        )
        syncNote(noteDao.getNote(id) ?: note)
    }

    suspend fun retryAllReady(limit: Int = 20): NoteSyncSummary = withContext(Dispatchers.IO) {
        syncPending(limit)
    }

    suspend fun syncPending(limit: Int = 20): NoteSyncSummary {
        val now = System.currentTimeMillis()
        val notes = noteDao.getReadyNotes(now, limit)
        val noteSummary = notes.fold(NoteSyncSummary.Empty) { summary, note ->
            val result = syncNote(note)
            summary.copy(
                processed = summary.processed + 1,
                succeeded = summary.succeeded + if (result.syncStatus == NoteSyncStatus.SYNCED) 1 else 0,
                failed = summary.failed + if (result.syncStatus == NoteSyncStatus.ERROR) 1 else 0,
            )
        }

        val attachments = noteDao.getReadyAttachments(now, limit)
        val attachmentSummary = attachments.fold(NoteSyncSummary.Empty) { summary, attachment ->
            val success = syncAttachment(attachment)
            summary.copy(
                processed = summary.processed + 1,
                succeeded = summary.succeeded + if (success) 1 else 0,
                failed = summary.failed + if (success) 0 else 1,
            )
        }

        return NoteSyncSummary(
            processed = noteSummary.processed + attachmentSummary.processed,
            succeeded = noteSummary.succeeded + attachmentSummary.succeeded,
            failed = noteSummary.failed + attachmentSummary.failed,
        )
    }

    suspend fun addAttachment(noteId: String, uri: Uri): NoteAttachmentEntity = withContext(Dispatchers.IO) {
        val note = noteDao.getNote(noteId) ?: error("Сначала сохраните заметку")
        val metadata = resolveAttachmentMetadata(uri)
        val localFile = copyAttachmentToLocalFile(note.id, uri, metadata.displayName)
        val now = System.currentTimeMillis()
        val attachment = NoteAttachmentEntity(
            noteId = note.id,
            localPath = localFile.absolutePath,
            displayName = metadata.displayName,
            mimeType = metadata.mimeType,
            sizeBytes = metadata.size,
            createdAt = now,
            updatedAt = now,
        )
        noteDao.insertAttachment(attachment)
        noteDao.updateNote(
            note.copy(
                updatedAt = now,
                syncStatus = NoteSyncStatus.QUEUED.storageKey,
                nextSyncAttemptAt = 0L,
            )
        )
        enqueueSyncWorker()
        attachment
    }

    suspend fun setPinned(id: String, pinned: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setPinned(id, pinned)
    }

    suspend fun setArchived(id: String, archived: Boolean) = withContext(Dispatchers.IO) {
        noteDao.setArchived(id, archived)
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        noteDao.softDeleteNote(id)
    }

    fun enqueueSyncWorker() {
        val settings = settingsRepository.getNoteSettings()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<NoteSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BASE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    private suspend fun syncNote(note: NoteEntity): NoteCaptureResult {
        val running = note.copy(
            syncStatus = NoteSyncStatus.SYNCING.storageKey,
            syncAttempts = note.syncAttempts + 1,
            lastError = null,
            updatedAt = System.currentTimeMillis(),
        )
        noteDao.updateNote(running)

        return sollGateway.createRawNote(
            title = running.title,
            content = running.content,
            tags = running.tags() + listOf("notes", running.source),
        ).fold(
            onSuccess = { raw ->
                val keepLocal = settingsRepository.getNoteSettings().keepLocalAfterSync
                noteDao.updateNote(
                    running.copy(
                        deleted = !keepLocal,
                        syncStatus = NoteSyncStatus.SYNCED.storageKey,
                        syncedFilename = raw.filename,
                        syncedPath = raw.path,
                        syncedAt = System.currentTimeMillis(),
                        lastError = null,
                        nextSyncAttemptAt = 0L,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                NoteCaptureResult(
                    noteId = running.id,
                    syncStatus = NoteSyncStatus.SYNCED,
                    filename = raw.filename,
                )
            },
            onFailure = { error ->
                val message = error.message ?: "Ошибка отправки"
                noteDao.updateNote(
                    running.copy(
                        syncStatus = NoteSyncStatus.ERROR.storageKey,
                        lastError = message,
                        nextSyncAttemptAt = nextAttemptAt(running.syncAttempts),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                enqueueSyncWorker()
                NoteCaptureResult(
                    noteId = running.id,
                    syncStatus = NoteSyncStatus.ERROR,
                    errorMessage = message,
                )
            }
        )
    }

    private suspend fun syncAttachment(attachment: NoteAttachmentEntity): Boolean {
        val file = File(attachment.localPath)
        if (!file.exists()) {
            noteDao.updateAttachment(
                attachment.copy(
                    syncStatus = NoteSyncStatus.ERROR.storageKey,
                    lastError = "Файл вложения не найден",
                    nextSyncAttemptAt = nextAttemptAt(attachment.syncAttempts + 1),
                    updatedAt = System.currentTimeMillis(),
                )
            )
            return false
        }

        val running = attachment.copy(
            syncStatus = NoteSyncStatus.SYNCING.storageKey,
            syncAttempts = attachment.syncAttempts + 1,
            lastError = null,
            updatedAt = System.currentTimeMillis(),
        )
        noteDao.updateAttachment(running)

        return sollGateway.uploadRawFile(Uri.fromFile(file)).fold(
            onSuccess = { upload ->
                noteDao.updateAttachment(
                    running.copy(
                        syncStatus = NoteSyncStatus.SYNCED.storageKey,
                        uploadedFilename = upload.filename,
                        uploadedPath = upload.path,
                        lastError = null,
                        nextSyncAttemptAt = 0L,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                true
            },
            onFailure = { error ->
                noteDao.updateAttachment(
                    running.copy(
                        syncStatus = NoteSyncStatus.ERROR.storageKey,
                        lastError = error.message ?: "Ошибка отправки вложения",
                        nextSyncAttemptAt = nextAttemptAt(running.syncAttempts),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                enqueueSyncWorker()
                false
            },
        )
    }

    private fun mergedTags(tagsInput: String, content: String, settings: NoteSettings): List<String> =
        NoteTextNormalizer.normalizeTags(tagsInput, content, settings.defaultTags)

    private fun nextAttemptAt(attempts: Int): Long {
        val delay = minOf(BASE_RETRY_DELAY_MS * attempts.coerceAtLeast(1), MAX_RETRY_DELAY_MS)
        return System.currentTimeMillis() + delay
    }

    private fun NoteEntity.toListItem(): NoteListItem =
        NoteListItem(
            id = id,
            title = title,
            content = content,
            snippet = NoteTextNormalizer.buildSnippet(content),
            tags = tags(),
            colorKey = colorKey,
            pinned = pinned,
            archived = archived,
            syncStatus = NoteSyncStatus.fromStorage(syncStatus),
            syncedFilename = syncedFilename,
            lastError = lastError,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun NoteEntity.tags(): List<String> =
        tagsJson.toTagList()

    private fun List<String>.toTagsJson(): String {
        val array = JSONArray()
        forEach { array.put(it) }
        return array.toString()
    }

    private fun String.toTagList(): List<String> = runCatching {
        val array = JSONArray(this)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private fun resolveAttachmentMetadata(uri: Uri): AttachmentMetadata {
        val resolver = context.contentResolver
        var displayName: String? = null
        var size: Long? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)
                    size = cursor.longOrNull(OpenableColumns.SIZE)
                }
            }

        val fallback = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
            ?: "attachment.bin"

        return AttachmentMetadata(
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallback,
            mimeType = resolver.getType(uri),
            size = size,
        )
    }

    private fun copyAttachmentToLocalFile(noteId: String, uri: Uri, displayName: String): File {
        val dir = File(context.filesDir, "note_attachments/$noteId").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}-${displayName.sanitizeFilename()}")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть вложение")
        inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun String.sanitizeFilename(): String =
        replace(Regex("""[\\/:*?"<>|]+"""), "_").ifBlank { "attachment.bin" }

    private fun Cursor.stringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longOrNull(columnName: String): Long? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private data class AttachmentMetadata(
        val displayName: String,
        val mimeType: String?,
        val size: Long?,
    )

    private data class FilterFlags(
        val includeArchived: Boolean = false,
        val archivedOnly: Boolean = false,
        val pinnedOnly: Boolean = false,
        val unsentOnly: Boolean = false,
        val errorsOnly: Boolean = false,
    )

    private fun NoteFilter.flags(): FilterFlags = when (this) {
        NoteFilter.ALL -> FilterFlags()
        NoteFilter.PINNED -> FilterFlags(pinnedOnly = true)
        NoteFilter.UNSENT -> FilterFlags(unsentOnly = true)
        NoteFilter.ERRORS -> FilterFlags(errorsOnly = true)
        NoteFilter.ARCHIVED -> FilterFlags(includeArchived = true, archivedOnly = true)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "note_sync"
        const val WORK_TAG = "notes"
        const val BASE_RETRY_DELAY_MS = 60_000L
        const val MAX_RETRY_DELAY_MS = 30 * 60_000L
    }
}
