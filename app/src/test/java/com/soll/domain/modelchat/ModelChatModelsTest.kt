package com.soll.domain.modelchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelChatModelsTest {
    @Test
    fun `safe request excludes private messages before server bridge`() {
        val request = ModelChatRequest(
            messages = listOf(
                ModelChatMessage("system", "answer briefly", private = false),
                ModelChatMessage("user", "MODEL_API_KEY=secret", private = true),
                ModelChatMessage("user", "привет", private = false),
            ),
            providerHint = ModelChatProviderHint.LLAMA,
            modelHint = "llama-test",
        )

        val safe = request.safeForServer()

        assertEquals(2, safe.messages.size)
        assertFalse(safe.messages.any { it.content.contains("secret") })
        assertEquals("llama-test", safe.modelHint)
    }

    @Test
    fun `server bridge keeps provider key server side`() {
        val question = ModelChatServerBridge.toAssistantQuestion(
            ModelChatRequest(
                messages = listOf(
                    ModelChatMessage("user", "Сделай краткий ответ", private = false),
                    ModelChatMessage("user", "MODEL_API_KEY=secret", private = true),
                ),
                providerHint = ModelChatProviderHint.LLAMA,
            )
        )

        assertTrue(question.contains("Provider hint: llama"))
        assertTrue(question.contains("provider keys stay server-side"))
        assertTrue(question.contains("Сделай краткий ответ"))
        assertFalse(question.contains("MODEL_API_KEY=secret"))
    }

    @Test
    fun `fallback does not ask android for provider secret`() {
        val response = ModelChatFallback.unavailable(
            request = ModelChatRequest(
                messages = listOf(ModelChatMessage("user", "hello")),
                providerHint = ModelChatProviderHint.LLAMA,
            ),
            reason = "timeout",
        )

        assertFalse(response.serverAvailable)
        assertEquals(ModelChatProviderHint.LLAMA, response.providerHint)
        assertEquals("timeout", response.fallbackReason)
        assertFalse(response.answer.contains("MODEL_API_KEY"))
    }
}
