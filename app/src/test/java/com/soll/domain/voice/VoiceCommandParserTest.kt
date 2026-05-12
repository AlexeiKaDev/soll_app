package com.soll.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
    private val parser = VoiceCommandParser()

    @Test
    fun `parses task summary command in russian`() {
        assertEquals(VoiceCommand.TaskSummary, parser.parse("Какие задачи на сегодня"))
    }

    @Test
    fun `parses sync command in russian`() {
        assertEquals(VoiceCommand.Sync, parser.parse("обнови синхронизацию"))
    }

    @Test
    fun `parses raw note prefix`() {
        val command = parser.parse("создай заметку купить молоко")

        assertTrue(command is VoiceCommand.RawNote)
        assertEquals("купить молоко", (command as VoiceCommand.RawNote).text)
    }

    @Test
    fun `parses music controls in russian`() {
        assertEquals(VoiceCommand.MusicPlay, parser.parse("включи музыку"))
        assertEquals(VoiceCommand.MusicPause, parser.parse("музыка пауза"))
        assertEquals(VoiceCommand.MusicNext, parser.parse("музыка следующий трек"))
        assertEquals(VoiceCommand.MusicPrevious, parser.parse("музыка предыдущий трек"))
    }
}
