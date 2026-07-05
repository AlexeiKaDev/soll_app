package com.soll.data.notification

import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest

enum class SystemNotificationImportanceMode(val storageKey: String) {
    HIGH_ONLY("high_only"),
    DEFAULT_AND_HIGH("default_and_high"),
    ALL("all");

    fun allows(priority: SollNotificationPriority): Boolean =
        when (this) {
            HIGH_ONLY -> priority == SollNotificationPriority.HIGH
            DEFAULT_AND_HIGH -> priority != SollNotificationPriority.LOW
            ALL -> true
        }

    companion object {
        fun fromStorage(value: String?): SystemNotificationImportanceMode =
            entries.firstOrNull { it.storageKey == value } ?: DEFAULT_AND_HIGH
    }
}

data class SystemNotificationPreferences(
    val importanceMode: SystemNotificationImportanceMode = SystemNotificationImportanceMode.DEFAULT_AND_HIGH,
    val allowedChannels: Set<SollNotificationChannel> = DEFAULT_ALLOWED_CHANNELS,
) {
    fun allows(request: SollNotificationRequest): Boolean =
        importanceMode.allows(request.priority) && request.channel in allowedChannels

    companion object {
        val FILTERABLE_CHANNELS = listOf(
            SollNotificationChannel.CHAT,
            SollNotificationChannel.ALERTS,
            SollNotificationChannel.TOOL_JOBS,
            SollNotificationChannel.EVENTS,
            SollNotificationChannel.SERVER_SYNC,
        )

        val DEFAULT_ALLOWED_CHANNELS = setOf(
            SollNotificationChannel.CHAT,
            SollNotificationChannel.ALERTS,
        )
    }
}
