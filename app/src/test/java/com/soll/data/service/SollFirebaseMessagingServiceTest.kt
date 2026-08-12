package com.soll.data.service

import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.presentation.navigation.AppLaunchTargets
import com.soll.data.repository.stableChatNotificationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SollFirebaseMessagingServiceTest {
    @Test
    fun `chat push stays in chat channel by default`() {
        val route = classifyFcmNotification(emptyMap())

        assertEquals(SollNotificationChannel.CHAT, route.channel)
        assertEquals("server_chat_push", route.type)
        assertEquals(SollNotificationPriority.DEFAULT, route.priority)
        assertEquals(AppLaunchTargets.SECTION_CHAT, route.launchSection)
        assertEquals(stableChatNotificationId("soll-main"), stableChatNotificationId("soll-main"))
        assertNotEquals(stableChatNotificationId("soll-main"), stableChatNotificationId("soll-other"))
    }

    @Test
    fun `monolith chat payload routes to visible chat notifications`() {
        val route = classifyFcmNotification(
            mapOf(
                "route" to "chat",
                "channel" to "chat",
                "notification_channel" to "chat",
                "type" to "server_chat_push",
                "category" to "chat",
                "priority" to "default",
                "soll_open_section" to "chat",
            )
        )

        assertEquals(SollNotificationChannel.CHAT, route.channel)
        assertEquals("server_chat_push", route.type)
        assertEquals(SollNotificationPriority.DEFAULT, route.priority)
        assertEquals(AppLaunchTargets.SECTION_CHAT, route.launchSection)
    }

    @Test
    fun `data only chat payload keeps a visible fallback body`() {
        val route = classifyFcmNotification(mapOf("route" to "chat"))

        val body = resolveFcmNotificationBody(
            notificationBody = null,
            route = route,
            data = mapOf("route" to "chat", "message_id" to "42"),
        )

        assertEquals("New Soll message", body)
    }

    @Test
    fun `chat fcm message id advances poll watermark`() {
        val route = classifyFcmNotification(mapOf("route" to "chat"))

        assertEquals(
            42L,
            fcmChatMessageIdForWatermark(route, mapOf("route" to "chat", "message_id" to "42")),
        )
    }

    @Test
    fun `silent fcm payload does not request system notification`() {
        val route = classifyFcmNotification(
            mapOf(
                "route" to "chat",
                "silent" to "true",
            )
        )

        assertEquals(false, shouldShowFcmSystemNotification(route, mapOf("silent" to "true")))
    }

    @Test
    fun `task board push is low priority server sync noise`() {
        val route = classifyFcmNotification(
            mapOf(
                "route" to "tasks/board",
                "type" to "server_task_board_changed",
            )
        )

        assertEquals(SollNotificationChannel.SERVER_SYNC, route.channel)
        assertEquals("server_task_board_changed", route.type)
        assertEquals(SollNotificationPriority.LOW, route.priority)
        assertEquals(AppLaunchTargets.SECTION_LOGS, route.launchSection)
        assertEquals(false, shouldShowFcmSystemNotification(route, mapOf("route" to "tasks/board")))
        assertEquals(
            null,
            fcmChatMessageIdForWatermark(route, mapOf("route" to "tasks/board", "message_id" to "42")),
        )
    }

    @Test
    fun `explicit launch section overrides notification channel default`() {
        val route = classifyFcmNotification(
            mapOf(
                "route" to "tasks/board",
                "type" to "server_task_board_changed",
                AppLaunchTargets.EXTRA_OPEN_SECTION to AppLaunchTargets.SECTION_TASKS,
            )
        )

        assertEquals(SollNotificationChannel.SERVER_SYNC, route.channel)
        assertEquals(AppLaunchTargets.SECTION_TASKS, route.launchSection)
    }

    @Test
    fun `explicit sync payload stays low priority server sync noise`() {
        val route = classifyFcmNotification(
            mapOf(
                "notification_channel" to "soll_server_sync",
                "type" to "server_task_board_changed",
                "priority" to "low",
            )
        )

        assertEquals(SollNotificationChannel.SERVER_SYNC, route.channel)
        assertEquals("server_task_board_changed", route.type)
        assertEquals(SollNotificationPriority.LOW, route.priority)
        assertEquals(AppLaunchTargets.SECTION_LOGS, route.launchSection)
    }

    @Test
    fun `event push is low priority unless server marks it high`() {
        val lowRoute = classifyFcmNotification(mapOf("channel" to "events"))
        val highRoute = classifyFcmNotification(
            mapOf(
                "channel" to "events",
                "severity" to "urgent",
            )
        )

        assertEquals(SollNotificationChannel.EVENTS, lowRoute.channel)
        assertEquals(SollNotificationPriority.LOW, lowRoute.priority)
        assertEquals(SollNotificationChannel.EVENTS, highRoute.channel)
        assertEquals(SollNotificationPriority.HIGH, highRoute.priority)
    }

    @Test
    fun `morning briefing payload opens Today`() {
        val route = classifyFcmNotification(
            mapOf(
                "route" to "assistant/today",
                "type" to "morning_briefing",
            )
        )

        assertEquals(SollNotificationChannel.EVENTS, route.channel)
        assertEquals(SollNotificationPriority.DEFAULT, route.priority)
        assertEquals(AppLaunchTargets.SECTION_TODAY, route.launchSection)
        assertEquals(true, shouldShowFcmSystemNotification(route, emptyMap()))
    }

    @Test
    fun `alert push becomes high priority alert`() {
        val route = classifyFcmNotification(mapOf("route" to "critical-alert"))

        assertEquals(SollNotificationChannel.ALERTS, route.channel)
        assertEquals("server_alert_push", route.type)
        assertEquals(SollNotificationPriority.HIGH, route.priority)
        assertEquals(AppLaunchTargets.SECTION_LOGS, route.launchSection)
    }

    @Test
    fun `task action push routes to tasks but remains opt in channel`() {
        val route = classifyFcmNotification(mapOf("route" to "task/action"))

        assertEquals(SollNotificationChannel.TOOL_JOBS, route.channel)
        assertEquals("server_task_push", route.type)
        assertEquals(SollNotificationPriority.DEFAULT, route.priority)
        assertEquals(AppLaunchTargets.SECTION_TASKS, route.launchSection)
        assertEquals(true, shouldShowFcmSystemNotification(route, mapOf("route" to "task/action")))
    }

    @Test
    fun `monolith schedule summary routes to grouped opt in job notifications`() {
        val data = mapOf(
            "route" to "task/schedule",
            "channel" to "tool_job",
            "notification_channel" to "tool_job",
            "notification_group" to "monosales_schedule",
            "notification_group_title" to "Schedule MonoSales",
            "type" to "schedule.cycle.summary",
            "category" to "schedule",
            "priority" to "default",
            AppLaunchTargets.EXTRA_OPEN_SECTION to AppLaunchTargets.SECTION_LOGS,
            AppLaunchTargets.EXTRA_OPEN_LOGS_TAB to AppLaunchTargets.LOGS_TAB_NOTIFICATIONS,
        )
        val route = classifyFcmNotification(data)

        assertEquals(SollNotificationChannel.TOOL_JOBS, route.channel)
        assertEquals("schedule.cycle.summary", route.type)
        assertEquals(SollNotificationPriority.DEFAULT, route.priority)
        assertEquals(AppLaunchTargets.SECTION_LOGS, route.launchSection)
        assertEquals("monosales_schedule", fcmNotificationGroupKey(data))
        assertEquals("Schedule MonoSales", fcmNotificationGroupTitle(data))
        assertEquals(true, shouldShowFcmSystemNotification(route, data))
    }

    @Test
    fun `monolith schedule failure routes to visible alerts`() {
        val data = mapOf(
            "route" to "critical-alert/schedule",
            "channel" to "alerts",
            "notification_channel" to "alerts",
            "notification_group" to "monosales_schedule",
            "type" to "schedule.job.failed",
            "category" to "schedule",
            "severity" to "error",
            "priority" to "high",
            AppLaunchTargets.EXTRA_OPEN_SECTION to AppLaunchTargets.SECTION_LOGS,
            AppLaunchTargets.EXTRA_OPEN_LOGS_TAB to AppLaunchTargets.LOGS_TAB_NOTIFICATIONS,
        )
        val route = classifyFcmNotification(data)

        assertEquals(SollNotificationChannel.ALERTS, route.channel)
        assertEquals("schedule.job.failed", route.type)
        assertEquals(SollNotificationPriority.HIGH, route.priority)
        assertEquals(AppLaunchTargets.SECTION_LOGS, route.launchSection)
        assertEquals(true, shouldShowFcmSystemNotification(route, data))
    }
}
