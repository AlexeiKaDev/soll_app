package com.soll.presentation.screens.voice

import com.soll.domain.soll.SollChatMessage
import com.soll.domain.soll.SollChatTurnError
import com.soll.domain.soll.SollChatTurnResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFinalReplyTest {
    @Test
    fun `exact core wait covers slow local model while legacy fallback stays bounded`() {
        assertEquals(
            180_000L,
            VOICE_REPLY_POLL_INTERVAL_MS * VOICE_EXACT_REPLY_POLL_ATTEMPTS,
        )
        assertEquals(23, VOICE_LEGACY_REPLY_POLL_ATTEMPTS)
    }

    @Test
    fun `relay stub can never become a spoken final reply`() {
        val stub = message(
            content = "Принял. Сервер Soll сохранил сообщение.",
            metadata = mapOf("source" to "yii2_soll_api", "assistant" to "stub"),
        )

        assertFalse(stub.isFinalLocalAgentVoiceReply())

        val mislabeledStub = stub.copy(
            metadata = mapOf("source" to "local_agent_chat_bridge", "assistant" to "local_agent"),
        )
        assertFalse(mislabeledStub.isFinalLocalAgentVoiceReply())
    }

    @Test
    fun `local agent reply must be explicit and correlated to voice request`() {
        val reply = message(
            content = "Финальный локальный ответ",
            metadata = mapOf(
                "source" to "local_agent_chat_bridge",
                "assistant" to "local_agent",
                "reply_to_message_id" to 42.0,
            ),
        )

        assertTrue(reply.isFinalLocalAgentVoiceReply())
        assertTrue(reply.matchesVoiceRequest(userMessageId = 42L, requestId = "voice-1"))
        assertFalse(reply.matchesVoiceRequest(userMessageId = 43L, requestId = "voice-1"))
    }

    @Test
    fun `uncorrelated assistant content stays fail closed`() {
        val unrelated = message(
            content = "Другой ответ",
            metadata = mapOf("assistant" to "local_agent"),
        )

        assertTrue(unrelated.isFinalLocalAgentVoiceReply())
        assertFalse(unrelated.matchesVoiceRequest(userMessageId = 42L, requestId = "voice-1"))
    }

    @Test
    fun `queued and failed status can never become spoken reply`() {
        val user = message("Голосовой запрос", emptyMap()).copy(id = 42L, role = "user")
        val assistant = message(
            content = "Финальный локальный ответ",
            metadata = mapOf(
                "source" to "local_agent_chat_bridge",
                "assistant" to "local_agent",
                "request_id" to "voice-1",
            ),
        )
        val queued = SollChatTurnResult(
            sessionId = "soll-main",
            message = user,
            assistant = assistant,
            status = "queued",
            final = false,
        )
        val failed = queued.copy(
            status = "failed",
            final = true,
            error = SollChatTurnError(code = "core_failed", message = "Core отказал"),
        )

        assertNull(queued.finalVoiceAssistantOrNull(userMessageId = 42L, requestId = "voice-1"))
        assertNull(failed.finalVoiceAssistantOrNull(userMessageId = 42L, requestId = "voice-1"))
        assertEquals("Core отказал", failed.failureMessageForVoice())
    }

    @Test
    fun `exact answered turn uses top level client id as authoritative correlation`() {
        val user = message("Голосовой запрос", emptyMap()).copy(id = 42L, role = "user")
        val assistantWithoutLegacyCorrelation = message(
            content = "Финальный локальный ответ",
            metadata = mapOf(
                "source" to "local_agent_chat_bridge",
                "assistant" to "local_agent",
            ),
        )
        val answered = SollChatTurnResult(
            sessionId = "soll-main",
            message = user,
            assistant = assistantWithoutLegacyCorrelation,
            clientTurnId = "voice-1",
            status = "answered",
            final = true,
        )

        assertEquals(
            assistantWithoutLegacyCorrelation,
            answered.finalVoiceAssistantOrNull(userMessageId = 42L, requestId = "voice-1"),
        )
        assertNull(answered.finalVoiceAssistantOrNull(userMessageId = 42L, requestId = "voice-2"))
    }

    private fun message(content: String, metadata: Map<String, Any?>): SollChatMessage =
        SollChatMessage(
            id = 43L,
            sessionId = "soll-main",
            role = "assistant",
            content = content,
            createdAt = "2026-08-13T08:00:00Z",
            metadata = metadata,
        )
}
