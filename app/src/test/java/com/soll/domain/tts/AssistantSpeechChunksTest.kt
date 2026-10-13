package com.soll.domain.tts

import org.junit.Assert.*
import org.junit.Test

class AssistantSpeechChunksTest {
    @Test fun longRussianReplyIsPreservedWithoutSplittingWords() {
        val text = List(250) { "Проверка русского голосового ответа." }.joinToString(" ")
        val chunks = assistantSpeechChunks(text, 200)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.isNotEmpty() && it.length <= 200 })
        assertEquals(text, chunks.joinToString(" "))
    }
    @Test fun oversizedUnbrokenTextCannotExceedEngineLimit() {
        val text = "а".repeat(500)
        val chunks = assistantSpeechChunks(text, 120)
        assertTrue(chunks.all { it.length <= 120 })
        assertEquals(text, chunks.joinToString(""))
    }
    @Test fun shortReplyDoesNotAddExtraChunks() {
        assertEquals(listOf("Голос работает."), assistantSpeechChunks("  Голос работает.  ", 200))
    }
}
