package com.soll.presentation.screens.chat

import com.soll.domain.soll.SollChatMessage
import com.soll.domain.soll.SollChatTurnError
import com.soll.domain.soll.SollChatTurnResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageFiltersTest {
    @Test
    fun `automatic chat speech is deduped by the last spoken message id`() {
        assertTrue(shouldAutomaticallySpeakChatMessage(enabled = true, messageId = 42L, lastSpokenMessageId = 41L))
        assertFalse(shouldAutomaticallySpeakChatMessage(enabled = true, messageId = 42L, lastSpokenMessageId = 42L))
        assertFalse(shouldAutomaticallySpeakChatMessage(enabled = false, messageId = 43L, lastSpokenMessageId = 42L))
    }

    @Test
    fun `queued turn wait is bounded and timeout is truthful`() {
        assertEquals(180_000L, CHAT_TURN_STATUS_POLL_INTERVAL_MS * CHAT_TURN_STATUS_POLL_ATTEMPTS)
        assertEquals("Ответ всё ещё обрабатывается и появится в чате позже.", chatTurnTimeoutMessage())
    }

    @Test
    fun `clarification fallback targets the task code and keeps free text`() {
        assertEquals(
            "#a2da45 Close the test task. Free-text answer is enough.",
            clarificationFallbackMessage(
                taskId = "a2da451839a04d9089507ec351496c99",
                note = "  Close the test task. Free-text answer is enough.  ",
            ),
        )
    }

    @Test
    fun `queued assistant payload cannot be displayed spoken or actioned`() {
        val user = chatMessage(content = "Запрос", id = 41).copy(role = "user")
        val unsafeAssistant = chatMessage(
            content = "Промежуточный relay ответ",
            id = 42,
            metadata = mapOf(
                "source" to "local_agent_chat_bridge",
                "send_voice" to true,
                "action" to mapOf("id" to "notice:1", "type" to "notice.ack"),
            ),
        )
        val queued = SollChatTurnResult(
            sessionId = "soll-main",
            message = user,
            assistant = unsafeAssistant,
            turnId = "turn-1",
            clientTurnId = "android-chat:1",
            status = "queued",
            final = false,
        )

        assertEquals(listOf(user), queued.immediateMessagesForChat())
        assertEquals(null, queued.immediateAssistantForChat())
        assertTrue(queued.immediateMessagesForChat().flatMap(SollChatMessage::actionUis).isEmpty())
    }

    @Test
    fun `failed turn cannot expose assistant actions and keeps truthful error`() {
        val user = chatMessage(content = "Запрос", id = 51).copy(role = "user")
        val unsafeAssistant = chatMessage(
            content = "Не должен отображаться",
            id = 52,
            metadata = mapOf("action" to mapOf("id" to "notice:2", "type" to "notice.ack")),
        )
        val failed = SollChatTurnResult(
            sessionId = "soll-main",
            message = user,
            assistant = unsafeAssistant,
            status = "failed",
            final = true,
            error = SollChatTurnError(code = "core_failed", message = "Core отказал"),
        )

        assertEquals(listOf(user), failed.immediateMessagesForChat())
        assertEquals(null, failed.immediateAssistantForChat())
        assertEquals("Core отказал", failed.failureMessageForChat())
        assertTrue(failed.immediateMessagesForChat().flatMap(SollChatMessage::actionUis).isEmpty())
    }

    @Test
    fun `queued approval is accepted but not completed before core receipt`() {
        val queued = com.soll.domain.soll.SollChatActionResult(
            actionId = "approval:approval-1:approve",
            action = "approval.approve",
            taskId = null,
            status = "pending",
            task = null,
        )
        val terminal = queued.copy(status = "approved")
        val clarificationAnswered = queued.copy(
            actionId = "task:task-1:clarify",
            action = "task.clarify",
            taskId = "task-1",
            status = "answered",
        )

        assertTrue(queued.isAcceptedPendingAction())
        assertTrue(
            queued.completedActionIds(
                requestedActionId = "approval:approval-1:approve",
                requestedTaskId = null,
            ).isEmpty(),
        )
        assertFalse(terminal.isAcceptedPendingAction())
        assertEquals(
            setOf("task:task-1:clarify", "task:task-1:*"),
            clarificationAnswered.completedActionIds(
                requestedActionId = "task:task-1:clarify",
                requestedTaskId = "task-1",
            ),
        )
        assertEquals(
            setOf("approval:approval-1:approve", "approval:approval-1:*"),
            terminal.completedActionIds(
                requestedActionId = "approval:approval-1:approve",
                requestedTaskId = null,
            ),
        )
    }

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
    fun `hides non destructively superseded legacy cards`() {
        val message = chatMessage(
            content = "Старая ошибочная карточка",
            metadata = mapOf("superseded" to true),
        )

        assertFalse(message.isDisplayableChatMessage())
    }

    @Test
    fun `reads synchronized tombstone ids and hides its control message`() {
        val tombstone = chatMessage(
            content = "Обновление истории чата",
            id = 99,
            metadata = mapOf(
                "visibility" to "superseded_control",
                "superseded_message_ids" to listOf(10, "11", 0),
                "superseded_task_ids" to listOf("task-1", "task-2"),
            ),
        )

        assertEquals(setOf(10L, 11L), supersededChatMessageIds(listOf(tombstone)))
        assertEquals(setOf("task-1", "task-2"), supersededChatTaskIds(listOf(tombstone)))
        assertFalse(tombstone.isDisplayableChatMessage())
    }

    @Test
    fun `resolves task id from action and nested extra metadata`() {
        val actionCard = chatMessage(
            content = "Карточка",
            metadata = mapOf("action" to mapOf("task_id" to "task-action")),
        )
        val relayCard = chatMessage(
            content = "Relay карточка",
            metadata = mapOf("extra" to mapOf("task_id" to "task-extra")),
        )

        assertEquals("task-action", actionCard.taskIdForChatMessage())
        assertEquals("task-extra", relayCard.taskIdForChatMessage())
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
    fun `voice playback is opt in through direct or nested delivery metadata`() {
        val direct = chatMessage("Critical alert", metadata = mapOf("send_voice" to true))
        val nested = chatMessage(
            "Digest",
            metadata = mapOf("extra" to mapOf("send_voice" to "true")),
        )
        val regular = chatMessage("Routine update")

        assertTrue(direct.requestsVoicePlayback())
        assertTrue(nested.requestsVoicePlayback())
        assertFalse(regular.requestsVoicePlayback())
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
    fun `chat refresh can fall back to sync status recent messages`() {
        val syncMessages = listOf(
            chatMessage("Other", id = 8, sessionId = "other"),
            chatMessage("Old", id = 10),
            chatMessage("New", id = 12),
        )

        val initial = chatMessagesWithSyncFallback(
            sessionMessages = emptyList(),
            syncRecentMessages = syncMessages,
            sessionId = "soll-main",
            afterId = null,
        )
        val incremental = chatMessagesWithSyncFallback(
            sessionMessages = emptyList(),
            syncRecentMessages = syncMessages,
            sessionId = "soll-main",
            afterId = 10,
        )

        assertEquals(listOf(10L, 12L), initial.map { it.id })
        assertEquals(listOf(12L), incremental.map { it.id })
    }

    @Test
    fun `direct chat session messages win over sync status fallback`() {
        val direct = listOf(chatMessage("Direct", id = 20))
        val fallback = listOf(chatMessage("Fallback", id = 10))

        assertSame(
            direct,
            chatMessagesWithSyncFallback(
                sessionMessages = direct,
                syncRecentMessages = fallback,
                sessionId = "soll-main",
                afterId = null,
            ),
        )
    }

    @Test
    fun `merge chat messages dedupes by id and prefers newest copy`() {
        val cached = listOf(
            chatMessage("Old copy", id = 10, metadata = mapOf("source" to "sync")),
            chatMessage("Still current", id = 11),
        )
        val fetched = listOf(
            chatMessage("Updated copy", id = 10, metadata = mapOf("source" to "session")),
            chatMessage("Newest", id = 12),
        )

        val merged = mergeChatMessages(cached, fetched)

        assertEquals(listOf(10L, 11L, 12L), merged.map { it.id })
        assertEquals("Updated copy", merged.first { it.id == 10L }.content)
        assertEquals("session", merged.first { it.id == 10L }.metadata["source"])
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
    fun `slash path opening is a required chat link contract`() {
        val slashPathUrl = "https://example.com/api/v1/soll/roadmap"

        assertEquals(
            listOf(slashPathUrl),
            extractChatLinks("Открыть $slashPathUrl"),
        )
        assertTrue(isOpenableChatUrl(slashPathUrl))
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

    @Test
    fun `clarification action keeps task bound russian prompt for answer dialog`() {
        val message = chatMessage(
            content = "Нужно решение владельца",
            metadata = mapOf(
                "action" to mapOf(
                    "id" to "task:task-1:clarify",
                    "type" to "task.clarify",
                    "task_id" to "task-1",
                    "label" to "Ответить по задаче",
                    "prompt" to "1. Нужно ли выполнять изменение в Soll?",
                ),
            ),
        )

        val action = message.actionUiOrNull()

        assertEquals("task-1", action?.taskId)
        assertEquals("1. Нужно ли выполнять изменение в Soll?", action?.prompt)
        assertEquals(message.id, action?.sourceMessageId)
        assertTrue(action?.requiresText == true)
    }

    @Test
    fun `clarification reply is attached directly below its source card`() {
        val source = chatMessage(content = "Уточните вариант", id = 10)
        val later = chatMessage(content = "Другое сообщение", id = 11)
        val reply = chatMessage(
            content = "Показывать одной строкой",
            id = 12,
            metadata = mapOf(
                "reply_to_message_id" to 10L,
                "action_result" to mapOf(
                    "action_id" to "task:task-1:clarify",
                    "task_id" to "task-1",
                    "status" to "answered",
                ),
            ),
        ).copy(role = "user")

        assertEquals(10L, reply.replyToMessageId())
        assertEquals(listOf(10L, 12L, 11L), attachChatReplies(listOf(source, later, reply)).map { it.id })
        assertEquals(
            setOf("task:task-1:clarify", "task:task-1:*"),
            completedChatActionIds(listOf(source, reply)),
        )
    }

    @Test
    fun `chat action parser rejects unknown action types`() {
        val message = chatMessage(
            content = "Untrusted action",
            metadata = mapOf(
                "actions" to listOf(
                    mapOf(
                        "id" to "shell:1",
                        "type" to "shell.execute",
                        "label" to "Run",
                    ),
                    mapOf(
                        "id" to "notice:1",
                        "type" to " NOTICE.ACK ",
                    ),
                ),
            ),
        )

        val actions = message.actionUis()

        assertEquals(listOf("notice:1"), actions.map { it.id })
        assertEquals(listOf("notice.ack"), actions.map { it.type })
    }

    @Test
    fun `completed chat action metadata hides stale action buttons`() {
        val taskMessage = chatMessage(
            content = "Registered task",
            metadata = mapOf(
                "actions" to listOf(
                    mapOf(
                        "id" to "task:task-1:today",
                        "type" to "task.today",
                        "task_id" to "task-1",
                    ),
                    mapOf(
                        "id" to "task:task-1:reject",
                        "type" to "task.reject",
                        "task_id" to "task-1",
                        "status" to "done",
                    ),
                ),
            ),
        )
        val resultMessage = chatMessage(
            content = "Action complete",
            metadata = mapOf(
                "action_result" to mapOf(
                    "action_id" to "task:task-1:today",
                    "status" to "done",
                ),
            ),
        )

        assertEquals(listOf("task:task-1:today"), taskMessage.actionUis().map { it.id })
        assertEquals(
            setOf("task:task-1:today", "task:task-1:*"),
            completedChatActionIds(listOf(taskMessage, resultMessage)),
        )
    }

    @Test
    fun `completed task action hides sibling task action buttons`() {
        val taskMessage = chatMessage(
            content = "Registered task",
            metadata = mapOf(
                "actions" to listOf(
                    mapOf(
                        "id" to "task:task-1:today",
                        "type" to "task.today",
                        "task_id" to "task-1",
                    ),
                    mapOf(
                        "id" to "task:task-1:reject",
                        "type" to "task.reject",
                        "task_id" to "task-1",
                    ),
                ),
            ),
        )
        val resultMessage = chatMessage(
            content = "Action complete",
            metadata = mapOf(
                "action_result" to mapOf(
                    "action_id" to "task:task-1:today",
                    "status" to "done",
                ),
            ),
        )

        val completed = completedChatActionIds(listOf(taskMessage, resultMessage))
        val visibleActions = taskMessage.actionUis().filterNot { it.isCompletedBy(completed) }

        assertTrue(visibleActions.isEmpty())
    }

    @Test
    fun `completed approval event hides approve and reject buttons`() {
        val approvalMessage = chatMessage(
            content = "Approval required",
            metadata = mapOf(
                "action" to mapOf(
                    "id" to "approval:approval-1:approve",
                    "type" to "approval.approve",
                    "approval_id" to "approval-1",
                    "label" to "Подтвердить",
                ),
                "actions" to listOf(
                    mapOf(
                        "id" to "approval:approval-1:reject",
                        "type" to "approval.reject",
                        "approval_id" to "approval-1",
                        "label" to "Отклонить",
                    ),
                ),
            ),
        )
        val completedMessage = chatMessage(
            content = "Approval accepted",
            metadata = mapOf(
                "extra" to mapOf(
                    "approval_id" to "approval-1",
                    "status" to "approved",
                ),
            ),
        )

        val completed = completedChatActionIds(listOf(approvalMessage, completedMessage))
        val visibleActions = approvalMessage.actionUis().filterNot { it.isCompletedBy(completed) }

        assertEquals(setOf("approval:approval-1:*"), completed)
        assertTrue(visibleActions.isEmpty())
    }

    @Test
    fun `assistant badges include server badge payload and hide duplicated server source`() {
        val message = chatMessage(
            content = "Нужен handoff в локальный агент",
            metadata = mapOf(
                "source" to "server",
                "title" to "Soll Core",
                "status" to "needs_agent",
                "badges" to listOf(
                    mapOf("label" to "agent needed", "tone" to "warning"),
                    mapOf("label" to "wiki 1", "tone" to "info"),
                    mapOf("label" to "tasks 1", "tone" to "success"),
                ),
            ),
        )

        val badges = message.badgeUis()

        assertEquals("Soll Core", messageTitle(message))
        assertEquals(null, messageSourceLabel(message))
        assertEquals(
            listOf("needs_agent", "agent needed", "wiki 1", "tasks 1"),
            badges.map { it.text },
        )
        assertEquals(
            listOf(
                ChatBadgeKind.STATUS,
                ChatBadgeKind.WARNING,
                ChatBadgeKind.INFO,
                ChatBadgeKind.SUCCESS,
            ),
            badges.map { it.kind },
        )
    }

    @Test
    fun `source label overrides transport source in chat metadata`() {
        val message = chatMessage(
            content = "New source item",
            metadata = mapOf(
                "source" to "telegram_archived",
                "source_label" to "Habr",
                "title" to "Source Monitor",
            ),
        )

        assertEquals("Habr", messageSourceLabel(message))
        assertTrue(messageTitle(message).orEmpty().contains("Habr"))
        assertTrue(message.badgeUis().map { it.text }.contains("Habr"))
    }

    @Test
    fun `source digest exposes article rows for chat card`() {
        val message = chatMessage(
            content = "Digest",
            metadata = mapOf(
                "entity_type" to "source_monitor",
                "event_type" to "source_digest",
                "items" to listOf(
                    mapOf(
                        "title" to "First article",
                        "summary" to "Useful detail for Android sync.",
                        "source_url" to "https://example.com/one",
                        "usefulness" to "high",
                        "needs_deep_dive" to true,
                    ),
                ),
            ),
        )

        val items = message.sourceDigestItemUis()

        assertEquals(1, items.size)
        assertEquals("First article", items[0].title)
        assertEquals("Useful detail for Android sync.", items[0].summary)
        assertEquals("https://example.com/one", items[0].url)
        assertEquals("high", items[0].usefulness)
        assertTrue(items[0].needsDeepDive)
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
