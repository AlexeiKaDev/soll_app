package com.soll.data.notification

import com.soll.domain.notification.SollNotificationRequest

object SystemNotificationDisplayPolicy {
    fun shouldShowSystemNotification(
        request: SollNotificationRequest,
        appInForeground: Boolean,
        preferences: SystemNotificationPreferences = SystemNotificationPreferences(),
    ): Boolean =
        request.showSystem && !appInForeground && preferences.allows(request)
}
