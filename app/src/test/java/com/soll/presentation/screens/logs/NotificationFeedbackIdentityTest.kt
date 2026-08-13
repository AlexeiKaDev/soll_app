package com.soll.presentation.screens.logs

import com.soll.domain.notification.SollNotification
import com.soll.domain.notification.SollNotificationChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationFeedbackIdentityTest {
    @Test
    fun `notification feedback prefers canonical server event id`() {
        val notification = SollNotification(
            id = "local-1",
            channel = SollNotificationChannel.EVENTS,
            type = "assistant_event",
            source = "fcm",
            title = "Soll",
            message = "Update",
            payloadJson = """{"event_id":"event-42","dedupe_key":"fcm:event:event-42"}""",
            dedupeKey = "fcm:event:event-42",
        )

        assertEquals("event-42", notification.feedbackEntityId())
        assertEquals("event-42", notification.authoritativeEventId())
    }

    @Test
    fun `opened receipt identity never falls back to dedupe key or local id`() {
        val notification = SollNotification(
            id = "local-1",
            channel = SollNotificationChannel.EVENTS,
            type = "assistant_event",
            source = "fcm",
            title = "Soll",
            message = "Update",
            payloadJson = """{"event_id":""}""",
            dedupeKey = "fcm:event:event-42",
        )

        assertNull(notification.authoritativeEventId())
        assertEquals("fcm:event:event-42", notification.feedbackEntityId())
    }

    @Test
    fun `only expand from collapsed records notification opened`() {
        assertTrue(shouldRecordNotificationOpened(expandedId = null, notificationId = "local-1"))
        assertTrue(shouldRecordNotificationOpened(expandedId = "local-2", notificationId = "local-1"))
        assertFalse(shouldRecordNotificationOpened(expandedId = "local-1", notificationId = "local-1"))
    }
}
