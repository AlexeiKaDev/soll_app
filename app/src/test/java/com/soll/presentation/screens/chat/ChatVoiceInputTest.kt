package com.soll.presentation.screens.chat

import com.soll.domain.soll.isSollVoiceWav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatVoiceInputTest {
    @Test
    fun `uses dictated text as input when message is empty`() {
        assertEquals(
            "проверить сервер",
            appendDictatedChatText("", "  проверить сервер  "),
        )
    }

    @Test
    fun `appends dictated text to existing input`() {
        assertEquals(
            "Soll проверь миграции",
            appendDictatedChatText("Soll  ", " проверь   миграции "),
        )
    }

    @Test
    fun `ignores blank recognition result`() {
        assertEquals(
            "оставить как есть",
            appendDictatedChatText("оставить как есть", "   "),
        )
    }

    @Test
    fun `prepares assistant markdown for bounded speech`() {
        val spoken = assistantSpeechText(
            "# Ответ\n[Документ](https://example.com) и `код`.\n```kotlin\nprintln(1)\n```",
        )

        assertEquals("Ответ Документ и код.", spoken)
    }

    @Test
    fun `accepts only complete wav envelope for server voice`() {
        val wav = b("RIFF") + ByteArray(4) + b("WAVE") + ByteArray(40)

        assertTrue(wav.isSollVoiceWav())
        assertFalse(b("RIFF-not-wave").isSollVoiceWav())
    }

    private fun b(value: String): ByteArray = value.toByteArray(Charsets.US_ASCII)
}
