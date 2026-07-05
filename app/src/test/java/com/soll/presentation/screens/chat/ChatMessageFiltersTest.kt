package com.soll.presentation.screens.chat

import com.soll.domain.soll.SollChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageFiltersTest {
    @Test
    fun `hides long repeated placeholder messages`() {
        val message = chatMessage("x".repeat(8200), source = "telegram_mirror")

        assertFalse(message.isDisplayableChatMessage())
    }

    @Test
    fun `keeps normal telegram mirrored messages`() {
        val message = chatMessage("Мониторинг источников завершен. Новых задач нет.", source = "telegram_mirror")

        assertTrue(message.isDisplayableChatMessage())
    }

    @Test
    fun `hides server assistant stub messages`() {
        val message = chatMessage(
            content = "Принял. Сервер Soll сохранил сообщение.",
            source = "yii2_soll_api",
            metadata = mapOf("source" to "yii2_soll_api", "assistant" to "stub"),
        )

        assertFalse(message.isDisplayableChatMessage())
    }

    @Test
    fun `matches content and metadata source`() {
        val message = chatMessage("Сделал задачу: Sync cached task.", source = "android_action")

        assertTrue(message.matchesChatQuery("cached"))
        assertTrue(message.matchesChatQuery("android_action"))
        assertFalse(message.matchesChatQuery("telegram"))
    }

    @Test
    fun `matches nested metadata without building combined search text`() {
        val message = chatMessage(
            content = "Задача обновлена",
            metadata = mapOf(
                "task_intake" to mapOf(
                    "actions" to listOf(
                        mapOf(
                            "type" to "task.defer",
                            "label" to "Позже",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(message.matchesChatQuery("task.defer"))
        assertTrue(message.matchesChatQuery("позже"))
        assertTrue(message.matchesChatQuery("actions"))
        assertFalse(message.matchesChatQuery("telegram"))
    }

    @Test
    fun `visible chat messages reuse source list when search is blank`() {
        val messages = listOf(
            chatMessage("Первое", id = 10),
            chatMessage("Второе", id = 11),
        )

        assertSame(messages, visibleChatMessages(messages, " "))
        assertEquals(listOf(11L), visibleChatMessages(messages, "втор").map { it.id })
    }

    @Test
    fun `does not advance chat scroll when refresh returns same messages`() {
        val previous = listOf(
            chatMessage("Первое", id = 10),
            chatMessage("Второе", id = 11),
        )

        assertFalse(
            shouldAdvanceChatScroll(
                previousMessages = previous,
                nextMessages = previous,
                previousSessionId = "soll-main",
                nextSessionId = "soll-main",
                fetchedAfterId = null,
                fetchedMessages = previous,
            )
        )
    }

    @Test
    fun `advances chat scroll when refresh finds newer message`() {
        val previous = listOf(chatMessage("Первое", id = 10))
        val next = previous + chatMessage("Новое", id = 11)

        assertTrue(
            shouldAdvanceChatScroll(
                previousMessages = previous,
                nextMessages = next,
                previousSessionId = "soll-main",
                nextSessionId = "soll-main",
                fetchedAfterId = null,
                fetchedMessages = next,
            )
        )
    }

    @Test
    fun `after-id refresh only scrolls when new messages were fetched`() {
        val previous = listOf(chatMessage("Первое", id = 10))

        assertFalse(
            shouldAdvanceChatScroll(
                previousMessages = previous,
                nextMessages = previous,
                previousSessionId = "soll-main",
                nextSessionId = "soll-main",
                fetchedAfterId = 10,
                fetchedMessages = emptyList(),
            )
        )
        assertTrue(
            shouldAdvanceChatScroll(
                previousMessages = previous,
                nextMessages = previous + chatMessage("Новое", id = 11),
                previousSessionId = "soll-main",
                nextSessionId = "soll-main",
                fetchedAfterId = 10,
                fetchedMessages = listOf(chatMessage("Новое", id = 11)),
            )
        )
    }

    @Test
    fun `refresh scroll reason distinguishes initial load and remote append`() {
        val previous = listOf(chatMessage("Первое", id = 10))

        assertEquals(
            ChatScrollReason.INITIAL_LOAD,
            chatScrollReasonForRefresh(
                previousMessages = emptyList(),
                nextMessages = previous,
                previousSessionId = "soll-main",
                nextSessionId = "soll-main",
                fetchedAfterId = null,
                fetchedMessages = previous,
            ),
        )
        assertEquals(
            ChatScrollReason.REMOTE_APPEND,
            chatScrollReasonForRefresh(
                previousMessages = previous,
                nextMessages = previous + chatMessage("Новое", id = 11),
                previousSessionId = "soll-main",
                nextSessionId = "soll-main",
                fetchedAfterId = 10,
                fetchedMessages = listOf(chatMessage("Новое", id = 11)),
            ),
        )
    }

    @Test
    fun `remote chat append does not force scroll while user reads history`() {
        assertFalse(
            shouldAutoScrollChatList(
                reason = ChatScrollReason.REMOTE_APPEND,
                totalItemsCount = 100,
                lastVisibleIndex = 35,
            )
        )
        assertTrue(
            shouldAutoScrollChatList(
                reason = ChatScrollReason.REMOTE_APPEND,
                totalItemsCount = 100,
                lastVisibleIndex = 98,
            )
        )
        assertTrue(
            shouldAutoScrollChatList(
                reason = ChatScrollReason.USER_SEND,
                totalItemsCount = 100,
                lastVisibleIndex = 35,
            )
        )
        assertFalse(
            shouldAutoScrollChatList(
                reason = ChatScrollReason.NONE,
                totalItemsCount = 100,
                lastVisibleIndex = 98,
            )
        )
    }

    @Test
    fun `link preview image urls only allow remote http images`() {
        assertTrue(isSafePreviewImageUrl("https://example.com/image.png"))
        assertTrue(isSafePreviewImageUrl("http://example.com/image.png"))
        assertFalse(isSafePreviewImageUrl("file:///sdcard/private.png"))
        assertFalse(isSafePreviewImageUrl("data:image/png;base64,abc"))
        assertFalse(isSafePreviewImageUrl("https://user:pass@example.com/image.png"))
        assertFalse(isSafePreviewImageUrl("https://localhost/image.png"))
        assertFalse(isSafePreviewImageUrl("https://127.0.0.1/image.png"))
        assertFalse(isSafePreviewImageUrl("https://10.1.2.3/image.png"))
        assertFalse(isSafePreviewImageUrl("https://172.16.1.2/image.png"))
        assertFalse(isSafePreviewImageUrl("https://192.168.1.20/image.png"))
        assertFalse(isSafePreviewImageUrl("https://169.254.1.20/image.png"))
        assertFalse(isSafePreviewImageUrl("https://[::1]/image.png"))
        assertFalse(isSafePreviewImageUrl("https://[fe80::1]/image.png"))
        assertFalse(isSafePreviewImageUrl("https://[::ffff:127.0.0.1]/image.png"))
        assertFalse(isSafePreviewImageUrl("https://[::ffff:10.1.2.3]/image.png"))
        assertFalse(isSafePreviewImageUrl("https://[::ffff:192.168.1.20]/image.png"))
        assertFalse(isSafePreviewImageUrl("https://[0:0:0:0:0:ffff:169.254.1.20]/image.png"))
        assertFalse(isSafePreviewImageUrl("not a url"))
    }

    @Test
    fun `chat text links only expose openable http browser urls`() {
        assertEquals(
            listOf(
                "https://example.com/article?ref=soll",
                "http://sales.monolith-ost.com/api/v1/soll",
            ),
            extractChatLinks(
                "Смотри https://example.com/article?ref=soll, потом http://sales.monolith-ost.com/api/v1/soll.",
            ),
        )
        assertTrue(isOpenableChatUrl("https://example.com/article"))
        assertFalse(isOpenableChatUrl("file:///sdcard/private.txt"))
        assertFalse(isOpenableChatUrl("data:text/plain,hello"))
        assertFalse(isOpenableChatUrl("https://user:pass@example.com/private"))
        assertFalse(isOpenableChatUrl("not a url"))
    }

    @Test
    fun `link preview loader rejects redirect responses`() {
        assertFalse(isPreviewRedirectStatus(200))
        assertFalse(isPreviewRedirectStatus(204))
        assertTrue(isPreviewRedirectStatus(301))
        assertTrue(isPreviewRedirectStatus(302))
        assertTrue(isPreviewRedirectStatus(307))
        assertFalse(isPreviewSuccessStatus(199))
        assertTrue(isPreviewSuccessStatus(200))
        assertTrue(isPreviewSuccessStatus(204))
        assertFalse(isPreviewSuccessStatus(300))
    }

    @Test
    fun `link preview image responses require image content type`() {
        assertTrue(isPreviewImageContentType("image/jpeg"))
        assertTrue(isPreviewImageContentType("image/png; charset=binary"))
        assertFalse(isPreviewImageContentType("text/html"))
        assertFalse(isPreviewImageContentType("application/json"))
        assertFalse(isPreviewImageContentType(null))
    }

    @Test
    fun `chat action parser keeps legacy single action`() {
        val message = chatMessage(
            content = "Принять событие",
            metadata = mapOf(
                "action" to mapOf(
                    "id" to "notice:1",
                    "type" to "notice.ack",
                ),
            ),
        )

        val action = message.actionUiOrNull()

        assertEquals("notice:1", action?.id)
        assertEquals("notice.ack", action?.type)
        assertEquals("Принято", action?.label)
    }

    @Test
    fun `chat action parser supports multiple direct and task intake actions`() {
        val message = chatMessage(
            content = "Задача обновлена",
            metadata = mapOf(
                "actions" to listOf(
                    mapOf(
                        "type" to "task.done",
                        "task_id" to "task-1",
                    ),
                ),
                "task_intake" to mapOf(
                    "actions" to listOf(
                        mapOf(
                            "type" to "task.defer",
                            "task_id" to "task-1",
                            "label" to "Позже",
                        ),
                    ),
                ),
            ),
        )

        val actions = message.actionUis()

        assertEquals(listOf("task:task-1:done", "task:task-1:defer"), actions.map { it.id })
        assertEquals(listOf("Готово", "Позже"), actions.map { it.label })
    }

    private fun chatMessage(
        content: String,
        source: String = "telegram_mirror",
        id: Long = 1,
        sessionId: String = "soll-main",
        metadata: Map<String, Any?> = mapOf("source" to source, "title" to "Soll"),
    ): SollChatMessage =
        SollChatMessage(
            id = id,
            sessionId = sessionId,
            role = "assistant",
            content = content,
            createdAt = "2026-07-02T07:41:52+00:00",
            metadata = metadata,
        )
}
