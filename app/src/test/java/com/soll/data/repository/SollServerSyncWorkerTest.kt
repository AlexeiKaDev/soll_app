package com.soll.data.repository

import com.soll.data.service.classifyFcmNotification
import com.soll.data.service.fcmChatMessageIdForWatermark
import com.soll.domain.soll.SollAndroidPushHealth
import com.soll.domain.soll.SollAndroidSyncStatus
import com.soll.domain.soll.SollChatMessage
import com.soll.domain.soll.SollHealth
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SollServerSyncWorkerTest {

    @Test
    fun `chat notifications include only new non-user displayable messages`() {
        val messages = listOf(
            chatMessage(id = 1, role = "assistant", content = "old"),
            chatMessage(id = 2, role = "user", content = "my message"),
            chatMessage(id = 3, role = "assistant", content = "x".repeat(120)),
            chatMessage(id = 5, role = "assistant", content = "second"),
            chatMessage(id = 4, role = "assistant", content = "first"),
        )

        val result = newChatMessagesForNotification(messages, lastSeenMessageId = 1)

        assertEquals(listOf(4L, 5L), result.map { it.id })
    }

    @Test
    fun `foreground sync preserves chat watermark for later background notification`() {
        val plan = planChatNotificationsForSync(
            messages = listOf(chatMessage(id = 10, role = "assistant", content = "new")),
            lastSeenMessageId = 9,
            latestMessageId = 10,
            appInForeground = true,
        )

        assertTrue(plan.messagesToNotify.isEmpty())
        assertEquals(9L, plan.nextLastSeenMessageId)
        assertEquals(10L, plan.latestMessageId)
    }

    @Test
    fun `first background sync seeds chat watermark without backlog notification`() {
        val plan = planChatNotificationsForSync(
            messages = listOf(
                chatMessage(id = 1, role = "assistant", content = "old"),
                chatMessage(id = 2, role = "assistant", content = "also old"),
            ),
            lastSeenMessageId = 0,
            latestMessageId = null,
            appInForeground = false,
        )

        assertTrue(plan.messagesToNotify.isEmpty())
        assertEquals(2L, plan.nextLastSeenMessageId)
    }

    @Test
    fun `background sync plans new assistant notifications and advances watermark`() {
        val plan = planChatNotificationsForSync(
            messages = listOf(
                chatMessage(id = 2, role = "user", content = "mine"),
                chatMessage(id = 3, role = "assistant", content = "server reply"),
                chatMessage(id = 4, role = "assistant", content = "next server reply"),
            ),
            lastSeenMessageId = 1,
            latestMessageId = 4,
            appInForeground = false,
        )

        assertEquals(listOf(3L, 4L), plan.messagesToNotify.map { it.id })
        assertEquals(4L, plan.nextLastSeenMessageId)
    }

    @Test
    fun `background sync skips server messages marked silent or routine source monitor`() {
        val plan = planChatNotificationsForSync(
            messages = listOf(
                chatMessage(id = 2, role = "assistant", content = "silent", metadata = mapOf("silent" to true)),
                chatMessage(
                    id = 3,
                    role = "assistant",
                    content = "source monitor",
                    metadata = mapOf("extra" to mapOf("entity_type" to "source_monitor")),
                ),
                chatMessage(id = 4, role = "assistant", content = "real chat"),
            ),
            lastSeenMessageId = 1,
            latestMessageId = 4,
            appInForeground = false,
        )

        assertEquals(listOf(4L), plan.messagesToNotify.map { it.id })
        assertEquals(4L, plan.nextLastSeenMessageId)
    }

    @Test
    fun `background sync notifies source digest but keeps source item silent`() {
        val plan = planChatNotificationsForSync(
            messages = listOf(
                chatMessage(
                    id = 2,
                    role = "assistant",
                    content = "source item",
                    metadata = mapOf(
                        "entity_type" to "source_monitor",
                        "event_type" to "source_item",
                    ),
                ),
                chatMessage(
                    id = 3,
                    role = "assistant",
                    content = "source digest",
                    metadata = mapOf(
                        "entity_type" to "source_monitor",
                        "event_type" to "source_digest",
                    ),
                ),
            ),
            lastSeenMessageId = 1,
            latestMessageId = 3,
            appInForeground = false,
        )

        assertEquals(listOf(3L), plan.messagesToNotify.map { it.id })
        assertEquals(3L, plan.nextLastSeenMessageId)
    }

    @Test
    fun `background sync does not advance past messages it did not receive`() {
        val plan = planChatNotificationsForSync(
            messages = emptyList(),
            lastSeenMessageId = 4,
            latestMessageId = 9,
            appInForeground = false,
        )

        assertTrue(plan.messagesToNotify.isEmpty())
        assertEquals(4L, plan.nextLastSeenMessageId)
        assertEquals(9L, plan.latestMessageId)
    }

    @Test
    fun `fcm chat watermark prevents duplicate poll notification`() {
        val route = classifyFcmNotification(mapOf("route" to "chat"))
        val lastSeenFromFcm = fcmChatMessageIdForWatermark(
            route = route,
            data = mapOf("route" to "chat", "message_id" to "42"),
        ) ?: 0L

        val plan = planChatNotificationsForSync(
            messages = listOf(chatMessage(id = 42, role = "assistant", content = "already pushed")),
            lastSeenMessageId = lastSeenFromFcm,
            latestMessageId = 42,
            appInForeground = false,
        )

        assertTrue(plan.messagesToNotify.isEmpty())
        assertEquals(42L, plan.nextLastSeenMessageId)
    }

    @Test
    fun `chat notification dedupe key is shared by fcm and polling`() {
        assertEquals("chat:soll-main:42", chatNotificationDedupeKey("soll-main", 42))
    }

    @Test
    fun `chat notification id stays stable per session`() {
        assertEquals(stableChatNotificationId("soll-main"), stableChatNotificationId("soll-main"))
        assertNotEquals(stableChatNotificationId("soll-main"), stableChatNotificationId("soll-other"))
    }

    @Test
    fun `task board signature changes when task state changes`() {
        val first = taskBoard(task(status = "inbox", executionState = "queued"))
        val changed = taskBoard(task(status = "today", executionState = "running"))

        assertNotEquals(taskBoardSignature(first), taskBoardSignature(changed))
    }

    @Test
    fun `sync status recovers fcm registration when server has no tokens`() {
        val status = syncStatus(
            androidPush = SollAndroidPushHealth(
                enabled = true,
                configured = true,
                tokenCount = 0,
            ),
        )

        assertEquals(true, shouldRecoverAndroidPushRegistration(status))
        assertEquals(false, shouldRecoverAndroidPushRegistration(status.copy(fromCache = true)))
        assertEquals(
            false,
            shouldRecoverAndroidPushRegistration(
                status.copy(
                    health = status.health.copy(
                        androidPush = status.health.androidPush.copy(tokenCount = 1),
                    ),
                ),
            ),
        )
        assertEquals(
            false,
            shouldRecoverAndroidPushRegistration(
                status.copy(
                    health = status.health.copy(
                        androidPush = status.health.androidPush.copy(configured = false),
                    ),
                ),
            ),
        )
    }

    private fun chatMessage(
        id: Long,
        role: String,
        content: String,
        metadata: Map<String, Any?> = emptyMap(),
    ): SollChatMessage =
        SollChatMessage(
            id = id,
            sessionId = "soll-main",
            role = role,
            content = content,
            createdAt = "2026-07-02T12:00:00Z",
            metadata = metadata,
        )

    private fun task(
        status: String,
        executionState: String,
    ): SollTask =
        SollTask(
            id = "task-1",
            title = "Task",
            description = "",
            sourceRef = "test",
            projectName = "Soll",
            status = status,
            priority = "A",
            dueDate = null,
            tags = emptyList(),
            executionState = executionState,
        )

    private fun taskBoard(task: SollTask): SollTaskBoard =
        SollTaskBoard(
            today = emptyList(),
            inbox = listOf(task),
            stale = emptyList(),
            doneRecent = emptyList(),
        )

    private fun syncStatus(androidPush: SollAndroidPushHealth): SollAndroidSyncStatus =
        SollAndroidSyncStatus(
            serverTime = "2026-07-05T18:30:00Z",
            health = SollHealth(
                status = "healthy",
                schedulerRunning = true,
                vaultAccessible = true,
                jobsCount = 14,
                androidPush = androidPush,
            ),
            tasks = taskBoard(task(status = "inbox", executionState = "queued")),
            device = null,
            briefing = null,
            protocol = null,
            warnings = emptyList(),
        )
}
