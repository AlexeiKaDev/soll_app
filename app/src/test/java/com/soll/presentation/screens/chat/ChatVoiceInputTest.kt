package com.soll.presentation.screens.chat

import org.junit.Assert.assertEquals
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
}
