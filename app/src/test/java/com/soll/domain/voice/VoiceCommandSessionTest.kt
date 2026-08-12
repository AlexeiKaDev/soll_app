package com.soll.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceCommandSessionTest {
    @Test
    fun `tracks voice command lifecycle`() {
        val completed = VoiceCommandSession(id = "session-1")
            .processing("статус сервера")
            .completed("Сервер работает")

        assertEquals("session-1", completed.id)
        assertEquals(VoiceCommandSessionStatus.COMPLETED, completed.status)
        assertEquals("статус сервера", completed.recognizedText)
        assertEquals("Сервер работает", completed.responseText)
        assertNotNull(completed.finishedAt)
    }

    @Test
    fun `stores failed session error`() {
        val failed = VoiceCommandSession(id = "session-2").failed("Нет гарнитуры")

        assertEquals(VoiceCommandSessionStatus.FAILED, failed.status)
        assertEquals("Нет гарнитуры", failed.errorMessage)
        assertNotNull(failed.finishedAt)
    }

    @Test
    fun `cancelled session is terminal without a response`() {
        val cancelled = VoiceCommandSession(id = "session-3").cancelled()

        assertEquals(VoiceCommandSessionStatus.CANCELLED, cancelled.status)
        assertEquals("", cancelled.responseText)
        assertNotNull(cancelled.finishedAt)
    }
}
