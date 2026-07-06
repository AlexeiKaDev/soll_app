package com.soll.data.notification

import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemNotificationDisplayPolicyTest {
    @Test
    fun `system notification is shown when app is backgrounded`() {
        assertTrue(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true),
                appInForeground = false,
            )
        )
    }

    @Test
    fun `system notification is suppressed while app is foreground`() {
        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true),
                appInForeground = true,
            )
        )
    }

    @Test
    fun `foreground fcm chat notification can still alert`() {
        assertTrue(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true, source = "fcm"),
                appInForeground = true,
            )
        )
    }

    @Test
    fun `foreground low priority fcm stays silent`() {
        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(
                    showSystem = true,
                    source = "fcm",
                    priority = SollNotificationPriority.LOW,
                ),
                appInForeground = true,
            )
        )
    }

    @Test
    fun `explicit non system notification stays silent in background`() {
        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = false),
                appInForeground = false,
            )
        )
    }

    @Test
    fun `default preferences suppress low priority notification noise`() {
        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true, priority = SollNotificationPriority.LOW),
                appInForeground = false,
            )
        )
    }

    @Test
    fun `high only mode suppresses default priority and allows high priority`() {
        val preferences = SystemNotificationPreferences(
            importanceMode = SystemNotificationImportanceMode.HIGH_ONLY,
            allowedChannels = setOf(SollNotificationChannel.CHAT),
        )

        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true, priority = SollNotificationPriority.DEFAULT),
                appInForeground = false,
                preferences = preferences,
            )
        )
        assertTrue(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true, priority = SollNotificationPriority.HIGH),
                appInForeground = false,
                preferences = preferences,
            )
        )
    }

    @Test
    fun `disabled channel stays silent even in background`() {
        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true, channel = SollNotificationChannel.EVENTS),
                appInForeground = false,
                preferences = SystemNotificationPreferences(
                    allowedChannels = setOf(SollNotificationChannel.CHAT),
                ),
            )
        )
    }

    @Test
    fun `default preferences keep noisy task events and sync in journal`() {
        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true, channel = SollNotificationChannel.TOOL_JOBS),
                appInForeground = false,
            )
        )
        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true, channel = SollNotificationChannel.EVENTS),
                appInForeground = false,
            )
        )
        assertFalse(
            SystemNotificationDisplayPolicy.shouldShowSystemNotification(
                request = request(showSystem = true, channel = SollNotificationChannel.SERVER_SYNC),
                appInForeground = false,
            )
        )
    }

    private fun request(
        showSystem: Boolean,
        channel: SollNotificationChannel = SollNotificationChannel.CHAT,
        priority: SollNotificationPriority = SollNotificationPriority.DEFAULT,
        source: String = "unit",
    ): SollNotificationRequest =
        SollNotificationRequest(
            channel = channel,
            type = "test",
            source = source,
            title = "Soll",
            message = "message",
            priority = priority,
            showSystem = showSystem,
        )
}
