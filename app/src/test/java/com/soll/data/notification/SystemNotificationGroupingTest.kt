package com.soll.data.notification

import com.soll.data.repository.shouldPostSystemGroupSummary
import com.soll.domain.notification.SollNotificationChannel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SystemNotificationGroupingTest {
    @Test
    fun `chat uses one system notification while noisy channels keep summaries`() {
        assertFalse(shouldPostSystemGroupSummary(SollNotificationChannel.CHAT))
        assertTrue(shouldPostSystemGroupSummary(SollNotificationChannel.ALERTS))
        assertTrue(shouldPostSystemGroupSummary(SollNotificationChannel.TOOL_JOBS))
    }

    @Test
    fun `notification groups are stable per channel`() {
        assertEquals("soll.group.soll_chat", systemNotificationGroupKey(SollNotificationChannel.CHAT))
        assertEquals("soll.group.soll_alerts", systemNotificationGroupKey(SollNotificationChannel.ALERTS))
        assertEquals("soll.group.monosales_schedule", systemNotificationGroupKey(SollNotificationChannel.TOOL_JOBS, "MonoSales Schedule"))
        assertNotEquals(
            systemNotificationSummaryId(SollNotificationChannel.CHAT),
            systemNotificationSummaryId(SollNotificationChannel.ALERTS),
        )
        assertNotEquals(
            systemNotificationSummaryId(SollNotificationChannel.TOOL_JOBS),
            systemNotificationSummaryId(
                SollNotificationChannel.TOOL_JOBS,
                systemNotificationGroupKey(SollNotificationChannel.TOOL_JOBS, "monosales_schedule"),
            ),
        )
    }

    @Test
    fun `summary text keeps noisy streams consolidated`() {
        assertEquals("1 уведомление в чате", systemNotificationSummaryText(SollNotificationChannel.CHAT, 1))
        assertEquals("2 уведомления в чате", systemNotificationSummaryText(SollNotificationChannel.CHAT, 2))
        assertEquals("5 уведомлений в чате", systemNotificationSummaryText(SollNotificationChannel.CHAT, 5))
        assertEquals("12 уведомлений в чате", systemNotificationSummaryText(SollNotificationChannel.CHAT, 12))
        assertEquals("3 уведомления требуют внимания", systemNotificationSummaryText(SollNotificationChannel.ALERTS, 3))
        assertEquals("4 уведомления по задачам", systemNotificationSummaryText(SollNotificationChannel.TOOL_JOBS, 4))
        assertEquals("7 технических уведомлений", systemNotificationSummaryText(SollNotificationChannel.SERVER_SYNC, 7))
    }
}
