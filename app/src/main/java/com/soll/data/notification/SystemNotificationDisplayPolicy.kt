package com.soll.data.notification

import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest

object SystemNotificationDisplayPolicy {
    fun shouldShowSystemNotification(
        request: SollNotificationRequest,
        appInForeground: Boolean,
        preferences: SystemNotificationPreferences = SystemNotificationPreferences(),
    ): Boolean {
        if (!request.showSystem || !preferences.allows(request)) return false
        if (!appInForeground) return true
        return request.source == "fcm" &&
            request.priority != SollNotificationPriority.LOW &&
            request.channel in setOf(SollNotificationChannel.CHAT, SollNotificationChannel.ALERTS)
    }
}
