package com.soll.data.repository

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.soll.BuildConfig
import com.soll.data.notification.AppForegroundState
import com.soll.data.notification.SollNotificationChannels
import com.soll.data.service.AndroidPushTokenRegistrar
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import com.soll.domain.soll.SollAndroidSyncStatus
import com.soll.domain.soll.SollChatMessage
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoard
import com.soll.data.service.SollServerSyncAlarmScheduler
import com.soll.presentation.navigation.AppLaunchTargets
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import timber.log.Timber

class SollServerSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SollServerSyncWorkerEntryPoint::class.java,
        )
        val settings = entryPoint.settingsRepository()
        if (settings.sollServerUrl.isBlank()) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
            return Result.success()
        }

        return try {
            val appInForeground = AppForegroundState.isUserFacing()
            val status = entryPoint.sollGateway().getAndroidSyncStatus().getOrThrow()
            recoverAndroidPushRegistrationIfNeeded(status)
            syncTaskCache(
                status = status,
                taskCacheRepository = entryPoint.taskCacheRepository(),
                syncQueueRepository = entryPoint.syncQueueRepository(),
            )
            postChatNotifications(
                status = status,
                settings = settings,
                notificationCenter = entryPoint.notificationCenter(),
                appInForeground = appInForeground,
            )
            postTaskBoardNotification(
                board = status.tasks,
                settings = settings,
                notificationCenter = entryPoint.notificationCenter(),
                appInForeground = appInForeground,
            )
            SollServerSyncAlarmScheduler.scheduleNext(applicationContext)
            SollServerSyncScheduler.schedule(applicationContext, settings)
            Result.success()
        } catch (error: Throwable) {
            Timber.w(error, "Soll server sync failed")
            SollServerSyncAlarmScheduler.scheduleNext(applicationContext)
            SollServerSyncScheduler.schedule(applicationContext, settings)
            Result.retry()
        }
    }

    private suspend fun syncTaskCache(
        status: SollAndroidSyncStatus,
        taskCacheRepository: TaskCacheRepository,
        syncQueueRepository: SollSyncQueueRepository,
    ) {
        val pendingStatuses = syncQueueRepository.getPendingTaskActionStatuses()
        taskCacheRepository.replaceWith(status.tasks, pendingStatuses)
    }

    private suspend fun postChatNotifications(
        status: SollAndroidSyncStatus,
        settings: SettingsRepository,
        notificationCenter: SollNotificationCenter,
        appInForeground: Boolean,
    ) {
        val lastSeen = settings.sollChatLastSeenMessageId
        val plan = planChatNotificationsForSync(
            messages = status.chat.recentMessages,
            lastSeenMessageId = lastSeen,
            latestMessageId = status.chat.lastMessageId,
            appInForeground = appInForeground,
        )
        logSyncDiagnostic(
            "Soll server sync chat notifications: foreground=%s lastSeen=%d latest=%d planned=%d next=%d",
            appInForeground,
            lastSeen,
            plan.latestMessageId,
            plan.messagesToNotify.size,
            plan.nextLastSeenMessageId,
        )

        plan.messagesToNotify.forEach { message ->
            notificationCenter.post(
                SollNotificationRequest(
                    channel = SollNotificationChannel.CHAT,
                    type = "server_chat_message",
                    source = "soll_server_poll",
                    title = message.chatNotificationTitle(),
                    message = message.content.trim().take(MAX_NOTIFICATION_MESSAGE_LENGTH),
                    payloadJson = JSONObject()
                        .put("message_id", message.id)
                        .put("session_id", message.sessionId)
                        .put("created_at", message.createdAt)
                        .toString(),
                    priority = message.notificationPriority(),
                    showSystem = true,
                    onlyAlertOnce = true,
                    systemNotificationId = stableChatNotificationId(message),
                    launchSection = AppLaunchTargets.SECTION_CHAT,
                    dedupeKey = chatNotificationDedupeKey(message.sessionId, message.id),
                )
            )
        }

        if (plan.nextLastSeenMessageId > lastSeen) {
            settings.sollChatLastSeenMessageId = plan.nextLastSeenMessageId
        }
    }

    private suspend fun postTaskBoardNotification(
        board: SollTaskBoard,
        settings: SettingsRepository,
        notificationCenter: SollNotificationCenter,
        appInForeground: Boolean,
    ) {
        val signature = taskBoardSignature(board)
        val previous = settings.sollTaskBoardSignature
        if (signature.isBlank()) return
        if (appInForeground) {
            Timber.d("Soll server sync task notification deferred while app is foreground")
            return
        }

        if (previous.isNotBlank() && previous != signature && board.openCount > 0) {
            notificationCenter.post(
                SollNotificationRequest(
                    channel = SollNotificationChannel.SERVER_SYNC,
                    type = "server_task_board_changed",
                    source = "soll_server_poll",
                    title = "Задачи Soll обновлены",
                    message = "Открытых: ${board.openCount}. A/B/C/D задачи обновлены на сервере.",
                    payloadJson = JSONObject()
                        .put("open_count", board.openCount)
                        .put("signature", signature)
                        .toString(),
                    priority = SollNotificationPriority.LOW,
                    showSystem = false,
                    onlyAlertOnce = true,
                    systemNotificationId = SollNotificationChannels.CHAT_NOTIFICATION_ID + 41,
                    launchSection = AppLaunchTargets.SECTION_TASKS,
                    dedupeKey = "task_board:$signature",
                )
            )
        }

        settings.sollTaskBoardSignature = signature
    }

    private fun recoverAndroidPushRegistrationIfNeeded(status: SollAndroidSyncStatus) {
        if (shouldRecoverAndroidPushRegistration(status)) {
            AndroidPushTokenRegistrar.registerCurrentToken(
                applicationContext,
                reason = "server_token_count_zero",
                force = true,
            )
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "soll_server_chat_task_sync"
        private const val MAX_NOTIFICATION_MESSAGE_LENGTH = 320
    }
}

object SollServerSyncScheduler {
    fun schedule(
        context: Context,
        settingsRepository: SettingsRepository,
        initialDelayMs: Long = nextDelayMs(settingsRepository),
        replaceExisting: Boolean = initialDelayMs <= 0L,
    ) {
        if (settingsRepository.sollServerUrl.isBlank()) {
            WorkManager.getInstance(context).cancelUniqueWork(SollServerSyncWorker.UNIQUE_WORK_NAME)
            SollServerSyncAlarmScheduler.cancel(context)
            return
        }

        val networkType = if (settingsRepository.sollWifiOnlyUpload) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val requestBuilder = OneTimeWorkRequestBuilder<SollServerSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60_000L, TimeUnit.MILLISECONDS)
        if (initialDelayMs > 0L) {
            requestBuilder.setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
        } else {
            requestBuilder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val request = requestBuilder.build()

        val policy = if (replaceExisting) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.APPEND_OR_REPLACE
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            SollServerSyncWorker.UNIQUE_WORK_NAME,
            policy,
            request,
        )
    }

    private fun nextDelayMs(settingsRepository: SettingsRepository): Long =
        settingsRepository.sollSyncIntervalMinutes
            .coerceIn(1, 60)
            .coerceAtMost(5)
            .toLong() * 60_000L
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SollServerSyncWorkerEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun sollGateway(): SollGateway
    fun taskCacheRepository(): TaskCacheRepository
    fun syncQueueRepository(): SollSyncQueueRepository
    fun notificationCenter(): SollNotificationCenter
}

internal fun newChatMessagesForNotification(
    messages: List<SollChatMessage>,
    lastSeenMessageId: Long,
): List<SollChatMessage> =
    messages
        .asSequence()
        .filter { it.id > lastSeenMessageId }
        .filter { it.role != "user" }
        .filter { it.content.isNotBlank() }
        .filterNot { it.content.looksLikePlaceholderNoise() }
        .filterNot { it.isSilentForSystemNotification() }
        .sortedBy { it.id }
        .toList()

internal data class ChatNotificationSyncPlan(
    val latestMessageId: Long,
    val messagesToNotify: List<SollChatMessage>,
    val nextLastSeenMessageId: Long,
)

internal fun planChatNotificationsForSync(
    messages: List<SollChatMessage>,
    lastSeenMessageId: Long,
    latestMessageId: Long?,
    appInForeground: Boolean,
): ChatNotificationSyncPlan {
    val latest = latestMessageId
        ?: messages.maxOfOrNull { it.id }
        ?: lastSeenMessageId
    if (appInForeground) {
        return ChatNotificationSyncPlan(
            latestMessageId = latest,
            messagesToNotify = emptyList(),
            nextLastSeenMessageId = lastSeenMessageId,
        )
    }
    if (lastSeenMessageId <= 0L) {
        return ChatNotificationSyncPlan(
            latestMessageId = latest,
            messagesToNotify = emptyList(),
            nextLastSeenMessageId = maxOf(latest, lastSeenMessageId),
        )
    }
    val latestFetchedMessageId = messages.maxOfOrNull { it.id } ?: lastSeenMessageId
    return ChatNotificationSyncPlan(
        latestMessageId = latest,
        messagesToNotify = newChatMessagesForNotification(messages, lastSeenMessageId),
        nextLastSeenMessageId = maxOf(latestFetchedMessageId, lastSeenMessageId),
    )
}

internal fun taskBoardSignature(board: SollTaskBoard): String =
    allTasks(board)
        .sortedWith(compareBy<SollTask> { it.id }.thenBy { it.status })
        .joinToString("|") { task ->
            listOf(
                task.id,
                task.status,
                task.priority,
                task.title,
                task.executionState,
                task.valueMetric,
            ).joinToString(":")
        }

internal fun shouldRecoverAndroidPushRegistration(status: SollAndroidSyncStatus): Boolean =
    !status.fromCache &&
        status.health.androidPush.enabled &&
        status.health.androidPush.configured &&
        status.health.androidPush.tokenCount <= 0

private fun allTasks(board: SollTaskBoard): List<SollTask> =
    board.today + board.blocked + board.inbox + board.stale + board.deferred + board.doneRecent

private fun SollChatMessage.chatNotificationTitle(): String =
    metadata["title"]?.toString()?.takeIf { it.isNotBlank() }
        ?: metadata["source"]?.toString()?.takeIf { it.isNotBlank() }?.let { "Soll: $it" }
        ?: "Сообщение Soll"

private fun SollChatMessage.notificationPriority(): SollNotificationPriority =
    when (metadata["priority"]?.toString()?.lowercase()) {
        "high", "alert" -> SollNotificationPriority.HIGH
        "low" -> SollNotificationPriority.LOW
        else -> SollNotificationPriority.DEFAULT
    }

private fun stableChatNotificationId(message: SollChatMessage): Int =
    chatNotificationDedupeKey(message.sessionId, message.id).hashCode() and Int.MAX_VALUE

internal fun chatNotificationDedupeKey(sessionId: String, messageId: Long): String =
    "chat:${sessionId.ifBlank { "soll-main" }}:$messageId"

private fun SollChatMessage.isSilentForSystemNotification(): Boolean {
    if (metadata.booleanValue("silent") || metadata.booleanValue("android_silent")) return true
    val policy = metadata.mapValue("notification_policy")
    if (policy.booleanValue("silent") || policy.stringValue("decision") in setOf("silent", "suppress")) {
        return true
    }
    val extra = metadata.mapValue("extra")
    if (extra.booleanValue("silent") || extra.booleanValue("android_silent")) return true
    val entityType = extra.stringValue("entity_type").ifBlank { metadata.stringValue("entity_type") }
    val eventType = extra.stringValue("event_type").ifBlank { metadata.stringValue("event_type") }
    if (entityType in setOf("source_monitor", "task_digest")) return true
    if (entityType == "tool_job" && eventType == "job_completed") return true
    return false
}

private fun Map<String, Any?>.mapValue(key: String): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    return this[key] as? Map<String, Any?> ?: emptyMap()
}

private fun Map<String, Any?>.stringValue(key: String): String =
    this[key]?.toString()?.trim().orEmpty()

private fun Map<String, Any?>.booleanValue(key: String): Boolean =
    when (val value = this[key]) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.trim().lowercase() in setOf("1", "true", "yes", "y", "on", "silent")
        else -> false
    }

private fun logSyncDiagnostic(message: String, vararg args: Any?) {
    if (BuildConfig.DEBUG) {
        Log.i("SollServerSyncWorker", message.format(*args))
    }
}

private fun String.looksLikePlaceholderNoise(): Boolean {
    val compact = filterNot { it.isWhitespace() }
    if (compact.length < 80) return false
    val mostCommon = compact.groupingBy { it }.eachCount().values.maxOrNull() ?: return false
    return mostCommon.toDouble() / compact.length >= 0.98
}
