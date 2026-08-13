package com.soll.presentation.screens.logs

import com.soll.domain.notification.SollNotification
import com.soll.domain.notification.SollNotificationChannel
import org.junit.Assert.assertEquals
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
    }
}
