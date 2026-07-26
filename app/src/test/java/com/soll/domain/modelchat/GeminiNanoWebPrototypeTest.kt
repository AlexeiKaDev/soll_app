package com.soll.domain.modelchat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiNanoWebPrototypeTest {
    @Test
    fun `ready capability keeps private request on device`() {
        val decision = GeminiNanoWebPrototype.plan(
            request = privateRequest(),
            capability = WebAiCapability.PROMPT,
            snapshot = WebAiCapabilitySnapshot(prompt = WebAiAvailability.READY),
        )

        assertEquals(WebAiPrototypeRoute.ON_DEVICE, decision.route)
        assertEquals(WebAiPrototypeReason.CAPABILITY_READY, decision.reason)
        assertNull(decision.serverRequest)
    }

    @Test
    fun `router uses the availability of the requested capability`() {
        val decision = GeminiNanoWebPrototype.plan(
            request = publicRequest(),
            capability = WebAiCapability.SUMMARIZE,
            snapshot = WebAiCapabilitySnapshot(
                prompt = WebAiAvailability.READY,
                summarize = WebAiAvailability.UNAVAILABLE,
            ),
        )

        assertEquals(WebAiPrototypeRoute.SERVER_FALLBACK, decision.route)
    }

    @Test
    fun `downloadable model requires explicit consent`() {
        val decision = GeminiNanoWebPrototype.plan(
            request = publicRequest(),
            capability = WebAiCapability.REWRITE,
            snapshot = WebAiCapabilitySnapshot(rewrite = WebAiAvailability.DOWNLOADABLE),
        )

        assertEquals(WebAiPrototypeRoute.DOWNLOAD_CONSENT_REQUIRED, decision.route)
        assertEquals(WebAiPrototypeReason.MODEL_DOWNLOAD_REQUIRES_CONSENT, decision.reason)
        assertNull(decision.serverRequest)
    }

    @Test
    fun `explicit consent enables local route after model download`() {
        val decision = GeminiNanoWebPrototype.plan(
            request = publicRequest(),
            capability = WebAiCapability.PROMPT,
            snapshot = WebAiCapabilitySnapshot(prompt = WebAiAvailability.DOWNLOADABLE),
            allowModelDownload = true,
        )

        assertEquals(WebAiPrototypeRoute.ON_DEVICE_AFTER_DOWNLOAD, decision.route)
        assertEquals(WebAiPrototypeReason.EXPLICIT_DOWNLOAD_ALLOWED, decision.reason)
        assertNull(decision.serverRequest)
    }

    @Test
    fun `unavailable local capability uses sanitized server fallback for public request`() {
        val decision = GeminiNanoWebPrototype.plan(
            request = ModelChatRequest(
                messages = listOf(
                    ModelChatMessage(role = " USER ", content = "  summarize this  "),
                    ModelChatMessage(role = "user", content = "   "),
                ),
                modelHint = "  server-model  ",
            ),
            capability = WebAiCapability.SUMMARIZE,
            snapshot = WebAiCapabilitySnapshot(summarize = WebAiAvailability.UNAVAILABLE),
        )

        assertEquals(WebAiPrototypeRoute.SERVER_FALLBACK, decision.route)
        assertEquals(WebAiPrototypeReason.LOCAL_CAPABILITY_UNAVAILABLE, decision.reason)
        val serverRequest = requireNotNull(decision.serverRequest)
        assertEquals(1, serverRequest.messages.size)
        assertEquals("user", serverRequest.messages.single().role)
        assertEquals("summarize this", serverRequest.messages.single().content)
        assertEquals("server-model", serverRequest.modelHint)
        assertFalse(serverRequest.messages.any { it.private })
    }

    @Test
    fun `unknown local capability blocks private request instead of server fallback`() {
        val decision = GeminiNanoWebPrototype.plan(
            request = privateRequest(),
            capability = WebAiCapability.PROMPT,
            snapshot = WebAiCapabilitySnapshot(),
        )

        assertEquals(WebAiPrototypeRoute.BLOCKED_PRIVATE_REQUEST, decision.route)
        assertEquals(
            WebAiPrototypeReason.PRIVATE_CONTENT_REQUIRES_LOCAL_CAPABILITY,
            decision.reason,
        )
        assertNull(decision.serverRequest)
    }

    @Test
    fun `decision rejects server payload on a local route`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WebAiPrototypeDecision(
                route = WebAiPrototypeRoute.ON_DEVICE,
                reason = WebAiPrototypeReason.CAPABILITY_READY,
                serverRequest = publicRequest(),
            )
        }

        assertTrue(error.message.orEmpty().contains("server fallback"))
    }

    private fun publicRequest(): ModelChatRequest = ModelChatRequest(
        messages = listOf(ModelChatMessage(role = "user", content = "Коротко резюмируй заметку")),
    )

    private fun privateRequest(): ModelChatRequest = ModelChatRequest(
        messages = listOf(
            ModelChatMessage(role = "user", content = "Личная заметка", private = true),
        ),
    )
}
