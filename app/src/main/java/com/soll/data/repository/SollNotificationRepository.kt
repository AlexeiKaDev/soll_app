package com.soll.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.soll.R
import com.soll.data.local.dao.AppNotificationDao
import com.soll.data.local.entity.AppNotificationEntity
import com.soll.data.notification.SollNotificationChannels
import com.soll.domain.notification.SollNotification
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
) : SollNotificationCenter {
    override fun observeRecent(limit: Int): Flow<List<SollNotification>> =
        notificationDao.getRecent(limit).map { items -> items.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = notificationDao.getUnreadCount()

    override suspend fun post(request: SollNotificationRequest): SollNotification {
        ensureChannels()
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
        )
        notificationDao.insert(AppNotificationEntity.fromDomain(base))

        val shown = if (request.showSystem && canPostSystemNotifications()) {
            showSystemNotification(request, systemNotificationId)
        } else {
            false
        }
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
    private fun showSystemNotification(
        request: SollNotificationRequest,
        systemNotificationId: Int,
    ): Boolean {
        if (!canPostSystemNotifications()) return false
        val notification = NotificationCompat.Builder(context, request.channel.channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(request.title)
            .setContentText(request.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(request.message))
            .setContentIntent(contentIntent(systemNotificationId))
            .setPriority(request.priority.toCompatPriority())
            .setCategory(request.priority.toCategory())
            .setAutoCancel(request.autoCancel)
            .setOnlyAlertOnce(request.onlyAlertOnce)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(systemNotificationId, notification)
            true
        }.onFailure { error ->
            Timber.w(error, "Failed to show Soll notification")
        }.getOrDefault(false)
    }

    private fun contentIntent(requestCode: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AppLaunchTargets.EXTRA_OPEN_SECTION, AppLaunchTargets.SECTION_LOGS)
                putExtra(AppLaunchTargets.EXTRA_OPEN_LOGS_TAB, AppLaunchTargets.LOGS_TAB_NOTIFICATIONS)
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

}
