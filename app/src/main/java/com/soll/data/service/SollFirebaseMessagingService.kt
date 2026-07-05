package com.soll.data.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollServerSyncScheduler
import com.soll.data.repository.chatNotificationDedupeKey
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import com.soll.presentation.navigation.AppLaunchTargets
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import timber.log.Timber

class SollFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        AndroidPushTokenRegistrar.registerToken(applicationContext, token, reason = "fcm_refresh")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val route = classifyFcmNotification(data)
        val title = message.notification?.title
            ?: data["title"]
            ?: "Soll"
        val body = resolveFcmNotificationBody(
            notificationBody = message.notification?.body,
            route = route,
            data = data,
        )
        if (body.isBlank()) return

        val messageKey = data["message_id"]
            ?: message.messageId
            ?: "${title}:${body}:${message.sentTime}"
        val sessionId = data["session_id"]?.takeIf { it.isNotBlank() } ?: "soll-main"
        val chatMessageId = fcmChatMessageIdForWatermark(route, data)
        val chatDedupeKey = chatMessageId?.let { chatNotificationDedupeKey(sessionId, it) }
        val payload = JSONObject().apply {
            put("provider", "fcm")
            put("message_id", data["message_id"] ?: "")
            put("session_id", sessionId)
            put("route", data["route"] ?: "chat")
            put("channel", route.channel.channelId)
            put("type", route.type)
            put("priority", route.priority.name.lowercase())
            put("source", data["source"] ?: "server")
            put("dedupe_key", chatDedupeKey ?: "fcm:$messageKey")
        }.toString()

        runBlocking(Dispatchers.IO) {
            runCatching {
                val dependencies = entryPoint()
                val settingsRepository = dependencies.settingsRepository()
                dependencies.notificationCenter().post(
                    SollNotificationRequest(
                        channel = route.channel,
                        type = route.type,
                        source = "fcm",
                        title = title,
                        message = body,
                        payloadJson = payload,
                        priority = route.priority,
                        showSystem = shouldShowFcmSystemNotification(route, data),
                        onlyAlertOnce = true,
                        systemNotificationId = stablePushNotificationId(chatDedupeKey ?: "fcm:$messageKey"),
                        launchSection = route.launchSection,
                        launchLogsTab = data[AppLaunchTargets.EXTRA_OPEN_LOGS_TAB],
                        systemGroupKey = fcmNotificationGroupKey(data),
                        systemGroupTitle = fcmNotificationGroupTitle(data),
                        dedupeKey = chatDedupeKey ?: "fcm:$messageKey",
                    )
                )
                chatMessageId?.let { messageId ->
                    settingsRepository.advanceSollChatLastSeenMessageId(messageId)
                }
                SollServerSyncScheduler.schedule(
                    applicationContext,
                    settingsRepository,
                    initialDelayMs = 0L,
                    replaceExisting = true,
                )
            }.onFailure { error ->
                Timber.w(error, "Could not record FCM notification")
            }
        }
    }

    private fun entryPoint(): SollFirebaseMessagingEntryPoint =
        EntryPointAccessors.fromApplication(
            applicationContext,
            SollFirebaseMessagingEntryPoint::class.java,
        )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SollFirebaseMessagingEntryPoint {
    fun notificationCenter(): SollNotificationCenter
    fun settingsRepository(): SettingsRepository
}

private fun stablePushNotificationId(messageKey: String): Int =
    messageKey.hashCode() and Int.MAX_VALUE

internal data class FcmNotificationRoute(
    val channel: SollNotificationChannel,
    val type: String,
    val priority: SollNotificationPriority,
    val launchSection: String,
)

internal fun classifyFcmNotification(data: Map<String, String>): FcmNotificationRoute {
    val channel = data.toSollNotificationChannel()
    val priority = data.explicitSollPriority() ?: channel.defaultFcmPriority()
    val type = data["type"]
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: channel.defaultFcmType()
    return FcmNotificationRoute(
        channel = channel,
        type = type,
        priority = priority,
        launchSection = data.explicitLaunchSection() ?: channel.defaultLaunchSection(),
    )
}

internal fun resolveFcmNotificationBody(
    notificationBody: String?,
    route: FcmNotificationRoute,
    data: Map<String, String>,
): String =
    notificationBody.nonBlank()
        ?: data["body"].nonBlank()
        ?: data["message"].nonBlank()
        ?: route.defaultNotificationBody()

internal fun fcmChatMessageIdForWatermark(route: FcmNotificationRoute, data: Map<String, String>): Long? =
    data["message_id"]
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { messageId -> messageId > 0L && route.channel == SollNotificationChannel.CHAT }

internal fun shouldShowFcmSystemNotification(route: FcmNotificationRoute, data: Map<String, String>): Boolean {
    if (data.explicitSilent()) return false
    if (route.priority == SollNotificationPriority.LOW) return false
    if (route.priority == SollNotificationPriority.HIGH) return true
    return route.channel in setOf(
        SollNotificationChannel.CHAT,
        SollNotificationChannel.ALERTS,
        SollNotificationChannel.TOOL_JOBS,
        SollNotificationChannel.EVENTS,
    )
}

internal fun fcmNotificationGroupKey(data: Map<String, String>): String? =
    data["notification_group"].nonBlank()

internal fun fcmNotificationGroupTitle(data: Map<String, String>): String? =
    data["notification_group_title"].nonBlank()

private fun Map<String, String>.toSollNotificationChannel(): SollNotificationChannel {
    val hint = notificationHint()
    return when {
        hint.anyToken("alert", "alarm", "critical", "urgent", "device_qa") -> SollNotificationChannel.ALERTS
        hint.anyToken("chat", "message", "session") -> SollNotificationChannel.CHAT
        hint.anyToken("task_board", "board", "sync", "poll", "heartbeat") -> SollNotificationChannel.SERVER_SYNC
        hint.anyToken("tool_job", "job", "task", "action") -> SollNotificationChannel.TOOL_JOBS
        hint.anyToken("event", "insight", "suggestion", "source") -> SollNotificationChannel.EVENTS
        else -> SollNotificationChannel.CHAT
    }
}

private fun Map<String, String>.notificationHint(): String =
    listOf("channel", "notification_channel", "route", "type", "source", "category")
        .mapNotNull { key -> this[key] }
        .joinToString(" ")
        .lowercase()
        .replace('-', '_')
        .replace('/', '_')

private fun String.anyToken(vararg tokens: String): Boolean =
    tokens.any { token -> contains(token) }

private fun Map<String, String>.explicitSollPriority(): SollNotificationPriority? =
    listOf("priority", "importance", "severity")
        .asSequence()
        .mapNotNull { key -> this[key].toSollPriorityOrNull() }
        .firstOrNull()

private fun Map<String, String>.explicitLaunchSection(): String? =
    AppLaunchTargets.fromExtras(
        section = this[AppLaunchTargets.EXTRA_OPEN_SECTION]?.trim(),
        logsTab = this[AppLaunchTargets.EXTRA_OPEN_LOGS_TAB]?.trim(),
    )?.section

private fun Map<String, String>.explicitSilent(): Boolean =
    listOf("silent", "android_silent", "suppress_notification")
        .any { key -> this[key].toBooleanFlag() }

private fun String?.nonBlank(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }

private fun String?.toSollPriorityOrNull(): SollNotificationPriority? =
    when (this?.trim()?.lowercase()) {
        "high", "alert" -> SollNotificationPriority.HIGH
        "critical", "urgent" -> SollNotificationPriority.HIGH
        "default", "normal" -> SollNotificationPriority.DEFAULT
        "low", "silent", "info" -> SollNotificationPriority.LOW
        else -> null
    }

private fun String?.toBooleanFlag(): Boolean =
    when (this?.trim()?.lowercase()) {
        "1", "true", "yes", "y", "on", "silent" -> true
        else -> false
    }

private fun SollNotificationChannel.defaultFcmPriority(): SollNotificationPriority =
    when (this) {
        SollNotificationChannel.ALERTS -> SollNotificationPriority.HIGH
        SollNotificationChannel.EVENTS,
        SollNotificationChannel.SERVER_SYNC -> SollNotificationPriority.LOW
        else -> SollNotificationPriority.DEFAULT
    }

private fun SollNotificationChannel.defaultFcmType(): String =
    when (this) {
        SollNotificationChannel.CHAT -> "server_chat_push"
        SollNotificationChannel.ALERTS -> "server_alert_push"
        SollNotificationChannel.TOOL_JOBS -> "server_task_push"
        SollNotificationChannel.EVENTS -> "server_event_push"
        SollNotificationChannel.SERVER_SYNC -> "server_sync_push"
        else -> "server_push"
    }

private fun FcmNotificationRoute.defaultNotificationBody(): String =
    when (channel) {
        SollNotificationChannel.CHAT -> "New Soll message"
        SollNotificationChannel.ALERTS -> "Soll alert"
        SollNotificationChannel.TOOL_JOBS -> "Soll task update"
        SollNotificationChannel.EVENTS -> "Soll event"
        SollNotificationChannel.SERVER_SYNC -> "Soll sync update"
        else -> "Soll update"
    }

private fun SollNotificationChannel.defaultLaunchSection(): String =
    when (this) {
        SollNotificationChannel.CHAT -> AppLaunchTargets.SECTION_CHAT
        SollNotificationChannel.TOOL_JOBS -> AppLaunchTargets.SECTION_TASKS
        else -> AppLaunchTargets.SECTION_LOGS
    }
