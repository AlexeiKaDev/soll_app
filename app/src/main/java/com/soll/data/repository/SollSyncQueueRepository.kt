package com.soll.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soll.data.local.dao.SyncQueueDao
import com.soll.data.local.entity.SyncQueueEntity
import com.soll.domain.soll.SollGateway
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

data class SyncRetrySummary(
    val retried: Int,
    val succeeded: Int,
    val failed: Int,
    val remainingOpen: Int = 0,
)

@Singleton
class SollSyncQueueRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncQueueDao: SyncQueueDao,
    private val sollGateway: SollGateway,
) {
    fun observeOpenCount(): Flow<Int> = syncQueueDao.observeOpenCount()

    fun observeRecentItems(limit: Int = 10): Flow<List<SyncQueueEntity>> =
        syncQueueDao.observeRecentItems(limit)

    suspend fun enqueueRawNote(
        title: String,
        content: String,
        tags: List<String>,
        reason: String?,
        taskId: String? = null,
        taskTitle: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val payload = JSONObject()
            .put("title", title)
            .put("content", content)
            .put("tags", JSONArray(tags))
        taskId?.takeIf { it.isNotBlank() }?.let { payload.put("task_id", it) }
        taskTitle?.takeIf { it.isNotBlank() }?.let { payload.put("task_title", it) }
        syncQueueDao.insert(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                kind = SyncQueueEntity.KIND_RAW_NOTE,
                status = SyncQueueEntity.STATUS_PENDING,
                payloadJson = payload.toString(),
                attempts = 0,
                lastError = reason,
                createdAt = now,
                updatedAt = now,
                nextAttemptAt = 0L,
            )
        )
        enqueueRetryWorker()
    }

    suspend fun enqueueRawFile(
        uri: Uri,
        reason: String?,
        taskId: String? = null,
        taskTitle: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val displayName = resolveDisplayName(uri)
        val queueFile = copyToQueueFile(uri, id, displayName)
        val payload = JSONObject()
            .put("local_path", queueFile.absolutePath)
            .put("display_name", displayName)
        taskId?.takeIf { it.isNotBlank() }?.let { payload.put("task_id", it) }
        taskTitle?.takeIf { it.isNotBlank() }?.let { payload.put("task_title", it) }

        syncQueueDao.insert(
            SyncQueueEntity(
                id = id,
                kind = SyncQueueEntity.KIND_RAW_FILE,
                status = SyncQueueEntity.STATUS_PENDING,
                payloadJson = payload.toString(),
                attempts = 0,
                lastError = reason,
                createdAt = now,
                updatedAt = now,
                nextAttemptAt = 0L,
            )
        )
        enqueueRetryWorker()
    }

    fun enqueueRetryWorker(initialDelayMs: Long = 0L) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SollSyncQueueWorker>()
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BASE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    suspend fun retryReady(limit: Int = 10): SyncRetrySummary {
        val ready = syncQueueDao.getReadyItems(System.currentTimeMillis(), limit)
        var succeeded = 0
        var failed = 0

        ready.forEach { item ->
            val running = item.copy(
                status = SyncQueueEntity.STATUS_RUNNING,
                attempts = item.attempts + 1,
                updatedAt = System.currentTimeMillis(),
            )
            syncQueueDao.update(running)

            val result = try {
                retryItem(running)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }

            if (result.isSuccess) {
                markDone(running)
                succeeded++
            } else {
                markFailed(running, result.exceptionOrNull()?.message ?: "Ошибка синхронизации")
                failed++
            }
        }

        syncQueueDao.deleteCompletedOlderThan(System.currentTimeMillis() - COMPLETED_RETENTION_MS)
        return SyncRetrySummary(
            retried = ready.size,
            succeeded = succeeded,
            failed = failed,
            remainingOpen = syncQueueDao.countOpenItems(),
        )
    }

    private suspend fun retryItem(item: SyncQueueEntity): Result<Unit> {
        val payload = JSONObject(item.payloadJson)
        return when (item.kind) {
            SyncQueueEntity.KIND_RAW_NOTE -> {
                val tags = payload.optJSONArray("tags").toStringList()
                sollGateway.createRawNote(
                    title = payload.getString("title"),
                    content = payload.getString("content"),
                    tags = tags,
                ).map { Unit }
            }

            SyncQueueEntity.KIND_RAW_FILE -> {
                val file = File(payload.getString("local_path"))
                if (!file.exists()) {
                    Result.failure(IllegalStateException("Локальный файл очереди не найден"))
                } else {
                    sollGateway.uploadRawFile(Uri.fromFile(file)).map { Unit }
                }
            }

            else -> Result.failure(IllegalStateException("Неизвестный тип очереди: ${item.kind}"))
        }
    }

    private suspend fun markDone(item: SyncQueueEntity) {
        if (item.kind == SyncQueueEntity.KIND_RAW_FILE) {
            runCatching {
                val payload = JSONObject(item.payloadJson)
                File(payload.getString("local_path")).delete()
            }
        }

        syncQueueDao.update(
            item.copy(
                status = SyncQueueEntity.STATUS_DONE,
                lastError = null,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    private suspend fun markFailed(item: SyncQueueEntity, error: String) {
        val now = System.currentTimeMillis()
        val delayMs = minOf(BASE_RETRY_DELAY_MS * item.attempts.coerceAtLeast(1), MAX_RETRY_DELAY_MS)
        syncQueueDao.update(
            item.copy(
                status = SyncQueueEntity.STATUS_FAILED,
                lastError = error,
                updatedAt = now,
                nextAttemptAt = now + delayMs,
            )
        )
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)?.let { return it }
                }
            }

        return uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
            ?: "upload.bin"
    }

    private fun copyToQueueFile(uri: Uri, id: String, displayName: String): File {
        val queueDir = File(context.filesDir, "soll_sync_queue").apply { mkdirs() }
        val target = File(queueDir, "$id-${displayName.sanitizeFilename()}")
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("Не удалось открыть выбранный файл")
        inputStream.use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return target
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
    }

    private fun String.sanitizeFilename(): String =
        replace(Regex("""[\\/:*?"<>|]+"""), "_").ifBlank { "upload.bin" }

    private fun Cursor.stringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private companion object {
        const val BASE_RETRY_DELAY_MS = 60_000L
        const val MAX_RETRY_DELAY_MS = 30 * 60_000L
        const val COMPLETED_RETENTION_MS = 7 * 24 * 60 * 60_000L
        const val UNIQUE_WORK_NAME = "soll_sync_queue"
        const val WORK_TAG = "soll_sync_queue"
    }
}

class SollSyncQueueWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SollSyncQueueWorkerEntryPoint::class.java,
        )
        val summary = entryPoint.syncQueueRepository().retryReady(limit = 10)
        return syncQueueWorkDecision(summary).toWorkerResult()
    }
}

internal fun syncQueueWorkDecision(summary: SyncRetrySummary): SyncWorkDecision =
    if (summary.failed > 0 || summary.remainingOpen > 0) {
        SyncWorkDecision.RETRY
    } else {
        SyncWorkDecision.SUCCESS
    }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SollSyncQueueWorkerEntryPoint {
    fun syncQueueRepository(): SollSyncQueueRepository
}
