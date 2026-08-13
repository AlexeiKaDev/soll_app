package com.soll.presentation.screens.voice

import com.soll.domain.soll.SollChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFinalReplyTest {
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
