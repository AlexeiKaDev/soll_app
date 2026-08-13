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
import com.soll.domain.soll.SOLL_FEED_IMPORT_CLIENT_ID_MAX_LENGTH
import com.soll.domain.soll.SOLL_DURABLE_CLIENT_ID_MAX_LENGTH
import com.soll.domain.soll.SollFeedbackCommandResult
import com.soll.domain.soll.SollFeedImportResult
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException

data class SyncRetrySummary(
    val retried: Int,
    val succeeded: Int,
    val failed: Int,
    val terminal: Int = 0,
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

    fun observeItem(id: String): Flow<SyncQueueEntity?> = syncQueueDao.observeById(id)

    suspend fun enqueueFeedImport(
        url: String,
        title: String,
        sharedText: String,
        clientId: String,
    ): String {
        val cleanClientId = clientId.trim()
            .take(SOLL_FEED_IMPORT_CLIENT_ID_MAX_LENGTH)
            .ifBlank { UUID.randomUUID().toString() }
        val queueId = "feed-import:$cleanClientId"
        val payload = JSONObject()
            .put("url", url.trim())
            .put("title", title.trim().take(240))
            .put("shared_text", sharedText.trim().take(16_000))
            .put("client_id", cleanClientId)
        val existing = syncQueueDao.getById(queueId)
        if (existing != null) {
            require(existing.kind == SyncQueueEntity.KIND_FEED_IMPORT) {
                "Идентификатор очереди уже используется другим действием"
            }
            val existingPayload = JSONObject(existing.payloadJson)
            require(
                existingPayload.optString("url") == payload.optString("url") &&
                    existingPayload.optString("client_id") == cleanClientId
            ) { "Идентификатор отправки уже используется для другой ссылки" }
            if (existing.status == SyncQueueEntity.STATUS_FAILED) {
                syncQueueDao.update(
                    existing.copy(
                        status = SyncQueueEntity.STATUS_PENDING,
                        lastError = null,
                        updatedAt = System.currentTimeMillis(),
                        nextAttemptAt = 0L,
                    )
                )
            }
            if (existing.status != SyncQueueEntity.STATUS_DONE) enqueueRetryWorker()
            return queueId
        }

        val now = System.currentTimeMillis()
        syncQueueDao.insert(
            SyncQueueEntity(
                id = queueId,
                kind = SyncQueueEntity.KIND_FEED_IMPORT,
                status = SyncQueueEntity.STATUS_PENDING,
                payloadJson = payload.toString(),
                attempts = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
                nextAttemptAt = 0L,
            )
        )
        enqueueRetryWorker()
        return queueId
    }

    suspend fun enqueueFeedFeedback(
        entityId: String,
        decision: String,
        topic: String,
        source: String,
        note: String = "",
        clientId: String = UUID.randomUUID().toString(),
    ): String {
        val cleanEntityId = entityId.trim()
        require(cleanEntityId.isNotBlank()) { "ID материала не задан" }
        val cleanDecision = decision.trim().lowercase()
        require(cleanDecision.isNotBlank()) { "Решение обратной связи не задано" }
        val cleanClientId = durableClientId(clientId)
        return enqueueDurableCommand(
            queueId = "feed-feedback:$cleanClientId",
            kind = SyncQueueEntity.KIND_FEED_FEEDBACK,
            payload = JSONObject()
                .put("entity_id", cleanEntityId)
                .put("decision", cleanDecision)
                .put("topic", topic.trim())
                .put("source", source.trim())
                .put("note", note.trim().take(2_000))
                .put("client_id", cleanClientId),
        )
    }

    suspend fun enqueueAssistantFeedback(
        entityType: String,
        entityId: String,
        decision: String,
        note: String = "",
        clientId: String = UUID.randomUUID().toString(),
    ): String {
        val cleanEntityType = entityType.trim().lowercase()
        require(cleanEntityType in setOf("initiative", "notification")) { "Неизвестный тип обратной связи" }
        val cleanEntityId = entityId.trim()
        require(cleanEntityId.isNotBlank()) { "ID объекта обратной связи не задан" }
        val cleanDecision = decision.trim().lowercase()
        require(cleanDecision.isNotBlank()) { "Решение обратной связи не задано" }
        val cleanClientId = durableClientId(clientId)
        return enqueueDurableCommand(
            queueId = "assistant-feedback:$cleanClientId",
            kind = SyncQueueEntity.KIND_ASSISTANT_FEEDBACK,
            payload = JSONObject()
                .put("entity_type", cleanEntityType)
                .put("entity_id", cleanEntityId)
                .put("decision", cleanDecision)
                .put("note", note.trim().take(2_000))
                .put("client_id", cleanClientId),
        )
    }

    suspend fun enqueueNotificationReceipt(
        eventId: String,
        state: String,
        occurredAt: String,
    ): String {
        val cleanEventId = eventId.trim().take(200)
        require(cleanEventId.isNotBlank()) { "event_id уведомления не задан" }
        val cleanState = state.trim().lowercase()
        require(cleanState in setOf("received", "opened")) { "Неизвестное состояние уведомления" }
        val clientId = notificationReceiptClientId(cleanEventId, cleanState)
        return enqueueDurableCommand(
            queueId = "notification-receipt:$clientId",
            kind = SyncQueueEntity.KIND_NOTIFICATION_RECEIPT,
            payload = JSONObject()
                .put("event_id", cleanEventId)
                .put("state", cleanState)
                .put("occurred_at", occurredAt.trim())
                .put("client_id", clientId),
        )
    }

    private suspend fun enqueueDurableCommand(
        queueId: String,
        kind: String,
        payload: JSONObject,
    ): String {
        val existing = syncQueueDao.getById(queueId)
        if (existing != null) {
            require(existing.kind == kind) { "Идентификатор очереди уже используется другим действием" }
            require(sameDurableCommand(JSONObject(existing.payloadJson), payload, kind)) {
                "Идентификатор отправки уже используется для другого действия"
            }
            if (existing.status == SyncQueueEntity.STATUS_FAILED) {
                syncQueueDao.update(
                    existing.copy(
                        status = SyncQueueEntity.STATUS_PENDING,
                        lastError = null,
                        updatedAt = System.currentTimeMillis(),
                        nextAttemptAt = 0L,
                    )
                )
            }
            if (existing.status !in setOf(SyncQueueEntity.STATUS_DONE, SyncQueueEntity.STATUS_REJECTED)) {
                enqueueRetryWorker()
            }
            return queueId
        }

        val now = System.currentTimeMillis()
        syncQueueDao.insert(
            SyncQueueEntity(
                id = queueId,
                kind = kind,
                status = SyncQueueEntity.STATUS_PENDING,
                payloadJson = payload.toString(),
                attempts = 0,
                lastError = null,
                createdAt = now,
                updatedAt = now,
                nextAttemptAt = 0L,
            )
        )
        enqueueRetryWorker()
        return queueId
    }

    suspend fun retryNow(id: String) {
        val item = syncQueueDao.getById(id) ?: return
        if (item.status == SyncQueueEntity.STATUS_DONE) return
        syncQueueDao.update(
            item.copy(
                status = SyncQueueEntity.STATUS_PENDING,
                lastError = null,
                updatedAt = System.currentTimeMillis(),
                nextAttemptAt = 0L,
            )
        )
        enqueueRetryWorker()
    }

    fun feedImportResult(item: SyncQueueEntity): SollFeedImportResult? = runCatching {
        if (item.kind != SyncQueueEntity.KIND_FEED_IMPORT) return@runCatching null
        val result = JSONObject(item.payloadJson).optJSONObject("result") ?: return@runCatching null
        SollFeedImportResult(
            success = result.optBoolean("success"),
            status = result.optString("status"),
            message = result.optString("message"),
            entityId = result.optString("entity_id"),
            duplicate = result.optBoolean("duplicate"),
            url = result.optString("url"),
            title = result.optString("title"),
            sourceId = result.optString("source_id"),
            clusterId = result.optString("cluster_id"),
        )
    }.getOrNull()

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

    suspend fun enqueueTaskAction(
        taskId: String,
        taskTitle: String,
        action: String,
        targetStatus: String?,
        reason: String?,
    ) {
        val cleanTaskId = taskId.trim()
        require(cleanTaskId.isNotBlank()) { "ID задачи не задан" }
        require(action in TASK_ACTIONS) { "Неизвестное действие задачи: $action" }

        val now = System.currentTimeMillis()
        val payload = JSONObject()
            .put("task_id", cleanTaskId)
            .put("task_title", taskTitle)
            .put("action", action)
        targetStatus?.takeIf { it.isNotBlank() }?.let { payload.put("target_status", it) }

        syncQueueDao.insert(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                kind = SyncQueueEntity.KIND_TASK_ACTION,
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

    suspend fun getPendingTaskActionStatuses(): Map<String, String> =
        syncQueueDao.getOpenItemsByKind(SyncQueueEntity.KIND_TASK_ACTION)
            .mapNotNull { it.taskActionStatusOrNull() }
            .toMap()

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
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    suspend fun retryReady(limit: Int = 10): SyncRetrySummary {
        val now = System.currentTimeMillis()
        syncQueueDao.getStaleRunningDurableDeliveries(now - STALE_RUNNING_TIMEOUT_MS).forEach { stale ->
            interruptedDurableDeliveryRecovery(stale, now)?.let { recovered ->
                syncQueueDao.update(recovered)
            }
        }
        val ready = syncQueueDao.getReadyItems(now, limit)
        var succeeded = 0
        var failed = 0
        var terminal = 0

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
                interruptedDurableDeliveryRecovery(running, System.currentTimeMillis())?.let { recovered ->
                    withContext(NonCancellable) {
                        syncQueueDao.update(recovered)
                    }
                }
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }

            if (result.isSuccess) {
                markDone(syncQueueDao.getById(running.id) ?: running)
                succeeded++
            } else {
                val error = result.exceptionOrNull()
                if (error is TerminalDurableCommandException) {
                    markRejected(running, error.message ?: "Soll отклонил действие")
                    terminal++
                } else {
                    markFailed(running, error?.message ?: "Ошибка синхронизации")
                    failed++
                }
            }
        }

        syncQueueDao.deleteCompletedOlderThan(System.currentTimeMillis() - COMPLETED_RETENTION_MS)
        return SyncRetrySummary(
            retried = ready.size,
            succeeded = succeeded,
            failed = failed,
            terminal = terminal,
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

            SyncQueueEntity.KIND_TASK_ACTION -> retryTaskAction(payload)

            SyncQueueEntity.KIND_FEED_IMPORT -> retryFeedImport(item, payload)

            SyncQueueEntity.KIND_FEED_FEEDBACK -> retryFeedback(item) {
                sollGateway.sendFeedFeedback(
                    entityId = payload.getString("entity_id"),
                    decision = payload.getString("decision"),
                    topic = payload.optString("topic"),
                    source = payload.optString("source"),
                    note = payload.optString("note"),
                    clientId = payload.getString("client_id"),
                )
            }

            SyncQueueEntity.KIND_ASSISTANT_FEEDBACK -> retryFeedback(item) {
                sollGateway.sendAssistantFeedback(
                    entityType = payload.getString("entity_type"),
                    entityId = payload.getString("entity_id"),
                    decision = payload.getString("decision"),
                    note = payload.optString("note"),
                    clientId = payload.getString("client_id"),
                )
            }

            SyncQueueEntity.KIND_NOTIFICATION_RECEIPT -> retryFeedback(item) {
                sollGateway.sendNotificationReceipt(
                    eventId = payload.getString("event_id"),
                    state = payload.getString("state"),
                    occurredAt = payload.getString("occurred_at"),
                    clientId = payload.getString("client_id"),
                )
            }

            else -> Result.failure(IllegalStateException("Неизвестный тип очереди: ${item.kind}"))
        }
    }

    private suspend fun retryFeedImport(item: SyncQueueEntity, payload: JSONObject): Result<Unit> =
        sollGateway.importFeedLink(
            url = payload.getString("url"),
            title = payload.optString("title"),
            sharedText = payload.optString("shared_text"),
            clientId = payload.getString("client_id"),
        ).fold(
            onSuccess = { result ->
                if (!result.success) {
                    Result.failure(
                        TerminalDurableCommandException(
                            result.message.ifBlank { "Soll не принял ссылку" }
                        )
                    )
                } else {
                    val resultJson = JSONObject()
                        .put("success", result.success)
                        .put("status", result.status)
                        .put("message", result.message)
                        .put("entity_id", result.entityId)
                        .put("duplicate", result.duplicate)
                        .put("url", result.url)
                        .put("title", result.title)
                        .put("source_id", result.sourceId)
                        .put("cluster_id", result.clusterId)
                    syncQueueDao.update(
                        item.copy(
                            payloadJson = JSONObject(item.payloadJson).put("result", resultJson).toString(),
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    Result.success(Unit)
                }
            },
            onFailure = { error ->
                if (feedImportFailureDisposition(error) == FeedImportFailureDisposition.TERMINAL) {
                    Result.failure(TerminalDurableCommandException(error.message ?: "Soll отклонил ссылку", error))
                } else {
                    Result.failure(error)
                }
            },
        )

    private suspend fun retryFeedback(
        item: SyncQueueEntity,
        request: suspend () -> Result<SollFeedbackCommandResult>,
    ): Result<Unit> = request().fold(
        onSuccess = { result ->
            if (!result.accepted && !result.duplicate) {
                Result.failure(TerminalDurableCommandException("Soll не принял действие"))
            } else {
                val resultJson = JSONObject()
                    .put("accepted", result.accepted)
                    .put("duplicate", result.duplicate)
                    .put("action_id", result.actionId)
                    .put("status", result.status)
                syncQueueDao.update(
                    item.copy(
                        payloadJson = JSONObject(item.payloadJson).put("result", resultJson).toString(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                Result.success(Unit)
            }
        },
        onFailure = { error ->
            if (durableCommandFailureDisposition(error) == FeedImportFailureDisposition.TERMINAL) {
                Result.failure(TerminalDurableCommandException(error.message ?: "Soll отклонил действие", error))
            } else {
                Result.failure(error)
            }
        },
    )

    private suspend fun retryTaskAction(payload: JSONObject): Result<Unit> {
        val taskId = payload.getString("task_id")
        return when (val action = payload.getString("action")) {
            TASK_ACTION_MOVE_TO_TODAY -> sollGateway.moveTaskToToday(taskId).map { Unit }
            TASK_ACTION_SET_STATUS -> sollGateway.setTaskStatus(
                taskId = taskId,
                status = payload.getString("target_status"),
            ).map { Unit }
            TASK_ACTION_COMPLETE -> sollGateway.completeTask(taskId).map { Unit }
            TASK_ACTION_DEFER -> sollGateway.deferTask(taskId).map { Unit }
            TASK_ACTION_REJECT -> sollGateway.rejectTask(taskId).map { Unit }
            else -> Result.failure(IllegalStateException("Неизвестное действие задачи: $action"))
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

    private suspend fun markRejected(item: SyncQueueEntity, error: String) {
        syncQueueDao.update(
            item.copy(
                status = SyncQueueEntity.STATUS_REJECTED,
                lastError = error,
                updatedAt = System.currentTimeMillis(),
                nextAttemptAt = 0L,
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

    private fun SyncQueueEntity.taskActionStatusOrNull(): Pair<String, String>? =
        runCatching {
            val payload = JSONObject(payloadJson)
            val taskId = payload.optString("task_id").takeIf { it.isNotBlank() } ?: return@runCatching null
            val action = payload.optString("action")
            val status = taskActionTargetStatus(action, payload.optString("target_status"))
                ?: return@runCatching null
            taskId to status
        }.getOrNull()

    private fun taskActionTargetStatus(action: String, targetStatus: String?): String? =
        when (action) {
            TASK_ACTION_MOVE_TO_TODAY -> "today"
            TASK_ACTION_SET_STATUS -> targetStatus?.takeIf { it.isNotBlank() }
            TASK_ACTION_COMPLETE -> "done"
            TASK_ACTION_DEFER -> "deferred"
            TASK_ACTION_REJECT -> "rejected"
            else -> null
        }

    private fun String.sanitizeFilename(): String =
        replace(Regex("""[\\/:*?"<>|]+"""), "_").ifBlank { "upload.bin" }

    private fun Cursor.stringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    companion object {
        const val TASK_ACTION_MOVE_TO_TODAY = "MOVE_TO_TODAY"
        const val TASK_ACTION_SET_STATUS = "SET_STATUS"
        const val TASK_ACTION_COMPLETE = "COMPLETE"
        const val TASK_ACTION_DEFER = "DEFER"
        const val TASK_ACTION_REJECT = "REJECT"

        private val TASK_ACTIONS = setOf(
            TASK_ACTION_MOVE_TO_TODAY,
            TASK_ACTION_SET_STATUS,
            TASK_ACTION_COMPLETE,
            TASK_ACTION_DEFER,
            TASK_ACTION_REJECT,
        )

        private const val BASE_RETRY_DELAY_MS = 60_000L
        private const val MAX_RETRY_DELAY_MS = 30 * 60_000L
        private const val COMPLETED_RETENTION_MS = 7 * 24 * 60 * 60_000L
        private const val STALE_RUNNING_TIMEOUT_MS = 5 * 60_000L
        private const val UNIQUE_WORK_NAME = "soll_sync_queue"
        private const val WORK_TAG = "soll_sync_queue"
    }
}

internal fun notificationReceiptClientId(eventId: String, state: String): String =
    UUID.nameUUIDFromBytes("notification-receipt|${eventId.trim()}|${state.trim().lowercase()}".toByteArray()).toString()

private fun durableClientId(value: String): String =
    value.trim().take(SOLL_DURABLE_CLIENT_ID_MAX_LENGTH).ifBlank { UUID.randomUUID().toString() }

private fun sameDurableCommand(existing: JSONObject, proposed: JSONObject, kind: String): Boolean {
    if (existing.optString("client_id") != proposed.optString("client_id")) return false
    return when (kind) {
        SyncQueueEntity.KIND_FEED_FEEDBACK ->
            existing.optString("entity_id") == proposed.optString("entity_id") &&
                existing.optString("decision") == proposed.optString("decision")
        SyncQueueEntity.KIND_ASSISTANT_FEEDBACK ->
            existing.optString("entity_type") == proposed.optString("entity_type") &&
                existing.optString("entity_id") == proposed.optString("entity_id") &&
                existing.optString("decision") == proposed.optString("decision")
        SyncQueueEntity.KIND_NOTIFICATION_RECEIPT ->
            existing.optString("event_id") == proposed.optString("event_id") &&
                existing.optString("state") == proposed.optString("state")
        else -> false
    }
}

internal enum class FeedImportFailureDisposition {
    RETRYABLE,
    TERMINAL,
}

internal fun feedImportFailureDisposition(error: Throwable): FeedImportFailureDisposition =
    when (error) {
        is HttpException -> feedImportHttpFailureDisposition(error.code())
        is IllegalArgumentException, is SecurityException -> FeedImportFailureDisposition.TERMINAL
        else -> FeedImportFailureDisposition.RETRYABLE
    }

internal fun feedImportHttpFailureDisposition(statusCode: Int): FeedImportFailureDisposition =
    if (statusCode in 400..499 && statusCode !in setOf(408, 425, 429)) {
        FeedImportFailureDisposition.TERMINAL
    } else {
        FeedImportFailureDisposition.RETRYABLE
    }

internal fun durableCommandFailureDisposition(error: Throwable): FeedImportFailureDisposition =
    when (error) {
        is HttpException -> durableCommandHttpFailureDisposition(error.code())
        is IllegalArgumentException, is SecurityException -> FeedImportFailureDisposition.TERMINAL
        else -> FeedImportFailureDisposition.RETRYABLE
    }

internal fun durableCommandHttpFailureDisposition(statusCode: Int): FeedImportFailureDisposition =
    if (statusCode in setOf(401, 408, 425, 429) || statusCode >= 500) {
        FeedImportFailureDisposition.RETRYABLE
    } else {
        FeedImportFailureDisposition.TERMINAL
    }

private class TerminalDurableCommandException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

internal fun interruptedDurableDeliveryRecovery(
    item: SyncQueueEntity,
    recoveredAt: Long,
): SyncQueueEntity? {
    if (item.kind !in setOf(
            SyncQueueEntity.KIND_FEED_IMPORT,
            SyncQueueEntity.KIND_FEED_FEEDBACK,
            SyncQueueEntity.KIND_ASSISTANT_FEEDBACK,
            SyncQueueEntity.KIND_NOTIFICATION_RECEIPT,
        ) || item.status != SyncQueueEntity.STATUS_RUNNING
    ) {
        return null
    }
    return item.copy(
        status = SyncQueueEntity.STATUS_PENDING,
        lastError = "Предыдущая отправка была прервана и поставлена в очередь повторно",
        updatedAt = recoveredAt,
        nextAttemptAt = 0L,
    )
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
