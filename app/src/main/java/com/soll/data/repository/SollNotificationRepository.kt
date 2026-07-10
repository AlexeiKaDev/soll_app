package com.soll.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.soll.BuildConfig
import androidx.core.content.ContextCompat
import com.soll.R
import com.soll.data.local.dao.AppNotificationDao
import com.soll.data.local.entity.AppNotificationEntity
import com.soll.data.notification.AppForegroundState
import com.soll.data.notification.SollNotificationChannels
import com.soll.data.notification.SystemNotificationDisplayPolicy
import com.soll.data.notification.systemNotificationGroupKey
import com.soll.data.notification.systemNotificationSummaryId
import com.soll.data.notification.systemNotificationSummaryText
import com.soll.data.notification.systemNotificationSummaryTitle
import com.soll.domain.notification.SollNotification
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import com.soll.presentation.MainActivity
import com.soll.presentation.navigation.AppLaunchTargets
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SollNotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationDao: AppNotificationDao,
    private val settingsRepository: SettingsRepository,
) : SollNotificationCenter {
    override fun observeRecent(limit: Int): Flow<List<SollNotification>> =
        notificationDao.getRecent(limit).map { items -> items.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = notificationDao.getUnreadCount()

    override suspend fun post(request: SollNotificationRequest): SollNotification {
        ensureChannels()
        val dedupeKey = request.dedupeKey?.trim()?.takeIf { it.isNotBlank() }
        if (dedupeKey != null) {
            notificationDao.findByDedupeKey(dedupeKey)?.let { existing ->
                logNotificationDiagnostic(
                    "dedupe type=%s channel=%s key=%s systemId=%s",
                    request.type,
                    request.channel.channelId,
                    dedupeKey,
                    existing.systemNotificationId,
                )
                return existing.toDomain()
            }
        }
        val now = System.currentTimeMillis()
        val systemNotificationId = request.systemNotificationId ?: request.stableSystemNotificationId(now)
        val base = SollNotification(
            channel = request.channel,
            type = request.type,
            source = request.source,
            title = request.title,
            message = request.message,
            payloadJson = request.payloadJson,
            priority = request.priority,
            createdAt = now,
            systemNotificationId = systemNotificationId,
            dedupeKey = dedupeKey,
        )
        val entity = AppNotificationEntity.fromDomain(base)
        if (dedupeKey != null) {
            val inserted = notificationDao.insertIfAbsent(entity)
            if (inserted == -1L) {
                notificationDao.findByDedupeKey(dedupeKey)?.let { existing ->
                    return existing.toDomain()
                }
            }
        } else {
            notificationDao.insert(entity)
        }

        val appInForeground = AppForegroundState.isUserFacing()
        val canPostSystem = canPostSystemNotifications()
        val shouldShowSystem = SystemNotificationDisplayPolicy.shouldShowSystemNotification(
            request = request,
            appInForeground = appInForeground,
            preferences = settingsRepository.systemNotificationPreferences(),
        )
        val unreadInChannel = if (shouldShowSystem) {
            notificationDao.getUnreadCountForChannel(request.channel.name).coerceAtLeast(1)
        } else {
            1
        }
        val summaryUnreadInChannel = if (request.channel == SollNotificationChannel.CHAT) {
            1
        } else {
            unreadInChannel
        }
        val shown = if (shouldShowSystem && canPostSystem) {
            showSystemNotification(request, systemNotificationId, summaryUnreadInChannel)
        } else {
            false
        }
        logNotificationDiagnostic(
            "post type=%s channel=%s foreground=%s shouldSystem=%s canPost=%s shown=%s systemId=%d",
            request.type,
            request.channel.channelId,
            appInForeground,
            shouldShowSystem,
            canPostSystem,
            shown,
            systemNotificationId,
        )
        return if (shown) {
            val shownAt = System.currentTimeMillis()
            notificationDao.markShown(base.id, shownAt, systemNotificationId)
            base.copy(shownAt = shownAt)
        } else {
            base
        }
    }

    override suspend fun markRead(id: String) {
        notificationDao.markRead(id, System.currentTimeMillis())
    }

    override suspend fun markAllRead() {
        notificationDao.markAllRead(System.currentTimeMillis())
    }

    override suspend fun dismiss(id: String) {
        notificationDao.dismiss(id, System.currentTimeMillis())
    }

    override suspend fun deleteAll() {
        notificationDao.deleteAll()
    }

    override fun ensureChannels() {
        SollNotificationChannels.ensureAll(context)
    }

    override fun canPostSystemNotifications(): Boolean {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!notificationsEnabled) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun showSystemNotification(
        request: SollNotificationRequest,
        systemNotificationId: Int,
        unreadInChannel: Int,
    ): Boolean {
        if (!canPostSystemNotifications()) return false
        val groupKey = systemNotificationGroupKey(request.channel, request.systemGroupKey)
        val summaryId = systemNotificationSummaryId(request.channel, groupKey)
        cleanupLegacySystemNotifications(request.channel, systemNotificationId, summaryId)
        val notification = NotificationCompat.Builder(context, request.channel.channelId)
            .setSmallIcon(R.drawable.ic_ai_robot_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_tint))
            .setContentTitle(request.title)
            .setContentText(request.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(request.message))
            .setContentIntent(contentIntent(request, systemNotificationId))
            .setPriority(request.priority.toCompatPriority())
            .setCategory(request.priority.toCategory())
            .setAutoCancel(request.autoCancel)
            .setOnlyAlertOnce(request.onlyAlertOnce)
            .setGroup(groupKey)
            .setGroupAlertBehavior(groupAlertBehavior(request.channel))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        val summaryNotification = buildGroupSummaryNotification(
            request = request,
            groupKey = groupKey,
            unreadInChannel = unreadInChannel,
        )

        return runCatching {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(systemNotificationId, notification)
            manager.notify(summaryId, summaryNotification)
            true
        }.onFailure { error ->
            Timber.w(error, "Failed to show Soll notification")
        }.getOrDefault(false)
    }

    private suspend fun cleanupLegacySystemNotifications(
        channel: SollNotificationChannel,
        keepNotificationId: Int,
        keepSummaryId: Int,
    ) {
        if (channel != SollNotificationChannel.CHAT) return
        val keepIds = setOf(keepNotificationId, keepSummaryId)
        val legacyIds = notificationDao.getSystemNotificationIdsForChannel(channel.name)
            .asSequence()
            .filter { it !in keepIds }
            .distinct()
            .toList()
        if (legacyIds.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        legacyIds.forEach { manager.cancel(it) }
    }

    private fun buildGroupSummaryNotification(
        request: SollNotificationRequest,
        groupKey: String,
        unreadInChannel: Int,
    ): android.app.Notification {
        val summaryId = systemNotificationSummaryId(request.channel, groupKey)
        val summaryTitle = request.systemGroupTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: systemNotificationSummaryTitle(request.channel)
        val summaryText = systemNotificationSummaryText(request.channel, unreadInChannel)
        return NotificationCompat.Builder(context, request.channel.channelId)
            .setSmallIcon(R.drawable.ic_ai_robot_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_icon_tint))
            .setContentTitle(summaryTitle)
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.InboxStyle()
                    .addLine("${request.title}: ${request.message}".compactNotificationLine())
                    .setSummaryText(summaryText),
            )
            .setContentIntent(
                contentIntent(
                    request.copy(
                        title = summaryTitle,
                        message = summaryText,
                        onlyAlertOnce = true,
                        systemNotificationId = summaryId,
                    ),
                    summaryId,
                )
            )
            .setPriority(request.priority.toCompatPriority())
            .setCategory(request.priority.toCategory())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(groupAlertBehavior(request.channel))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
    }

    private fun groupAlertBehavior(channel: SollNotificationChannel): Int =
        when (channel) {
            SollNotificationChannel.CHAT,
            SollNotificationChannel.ALERTS -> NotificationCompat.GROUP_ALERT_CHILDREN
            else -> NotificationCompat.GROUP_ALERT_SUMMARY
        }

    private fun contentIntent(request: SollNotificationRequest, requestCode: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val launchSection = request.launchSection ?: if (request.channel == SollNotificationChannel.CHAT) {
            AppLaunchTargets.SECTION_CHAT
        } else {
            AppLaunchTargets.SECTION_LOGS
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AppLaunchTargets.EXTRA_OPEN_SECTION, launchSection)
                if (launchSection == AppLaunchTargets.SECTION_LOGS) {
                    putExtra(
                        AppLaunchTargets.EXTRA_OPEN_LOGS_TAB,
                        request.launchLogsTab ?: AppLaunchTargets.LOGS_TAB_NOTIFICATIONS,
                    )
                }
            },
            flags,
        )
    }

    private fun SollNotificationRequest.stableSystemNotificationId(now: Long): Int {
        val raw = "$source:$type:$title:$now".hashCode()
        return raw and Int.MAX_VALUE
    }

    private fun SollNotificationPriority.toCompatPriority(): Int = when (this) {
        SollNotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
        SollNotificationPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        SollNotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
    }

    private fun SollNotificationPriority.toCategory(): String = when (this) {
        SollNotificationPriority.HIGH -> NotificationCompat.CATEGORY_ALARM
        SollNotificationPriority.DEFAULT -> NotificationCompat.CATEGORY_EVENT
        SollNotificationPriority.LOW -> NotificationCompat.CATEGORY_STATUS
    }

    private fun logNotificationDiagnostic(message: String, vararg args: Any?) {
        if (BuildConfig.DEBUG) {
            Log.i("SollNotificationRepository", message.format(*args))
        }
    }

}

private fun String.compactNotificationLine(maxLength: Int = 120): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= maxLength) normalized else normalized.take(maxLength - 3).trimEnd() + "..."
}
