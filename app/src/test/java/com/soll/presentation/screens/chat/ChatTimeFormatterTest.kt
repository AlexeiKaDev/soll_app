package com.soll.presentation.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class ChatTimeFormatterTest {
    @Test
    fun `formats utc api timestamp in phone timezone`() {
        val label = formatChatTimeLabel(
            createdAt = "2026-07-02T07:41:52+00:00",
            zoneId = ZoneId.of("Europe/Chisinau"),
        )

        assertEquals("10:41", label)
    }

    @Test
    fun `falls back to embedded time for legacy values`() {
        assertEquals("10:41", formatChatTimeLabel("02.07.2026 10:41:52"))
    }
}
