package com.soll.data.notification

import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest

object SystemNotificationDisplayPolicy {
    const val CHAT_BURST_COOLDOWN_MS = 5 * 60_000L

    fun shouldShowSystemNotification(
        request: SollNotificationRequest,
        appInForeground: Boolean,
        preferences: SystemNotificationPreferences = SystemNotificationPreferences(),
    ): Boolean {
        if (!request.showSystem || !preferences.allows(request)) return false
        if (!appInForeground) return true
        return request.source == "fcm" &&
            request.priority != SollNotificationPriority.LOW &&
            request.channel == SollNotificationChannel.ALERTS
    }

    fun allowsChatBurst(
        lastShownAt: Long,
        nowMillis: Long,
        cooldownMillis: Long = CHAT_BURST_COOLDOWN_MS,
    ): Boolean =
        lastShownAt <= 0L ||
            nowMillis < lastShownAt ||
            nowMillis - lastShownAt >= cooldownMillis.coerceAtLeast(1L)
}
