package com.soll.domain.tts

import androidx.test.platform.app.InstrumentationRegistry
import com.soll.domain.soll.isSollVoiceWav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AssistantSpeechSynthesizerTest {
    @Test fun systemFallbackProducesSpeechForTheManagedPlayer() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val audio = AssistantSpeechSynthesizer(context).synthesize("Голос работает.")
        assertEquals(1, audio.size)
        assertTrue(audio.single().isSollVoiceWav())
        assertTrue(audio.single().size > 44)
        withContext(Dispatchers.Main) {
            val player = AssistantVoicePlayer(context)
            try {
                assertTrue(player.play(201L, audio))
                withTimeout(5_000) {
                    while (player.state.value.phase != AssistantVoicePlaybackPhase.PLAYING) {
                        assertNotEquals(player.state.value.error, AssistantVoicePlaybackPhase.ERROR, player.state.value.phase)
                        delay(10)
                    }
                }
                assertEquals(201L, player.state.value.messageId)
            } finally { player.stop() }
        }
    }

    @Test fun cancelledSynthesisDoesNotPreventNextSpeech() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val synthesizer = AssistantSpeechSynthesizer(context)
        val cancelled = async { synthesizer.synthesize("Проверка отмены. ".repeat(500)) }
        delay(50)
        cancelled.cancelAndJoin()
        assertTrue(cancelled.isCancelled)
        assertTrue(synthesizer.synthesize("Проверка завершена.").single().isSollVoiceWav())
    }
}
