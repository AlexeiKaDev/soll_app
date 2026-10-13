package com.soll.domain.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class AssistantVoicePlayerTest {
    @Test fun transientFocusLossPausesReplyAndFocusReturnResumesIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val interruption = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { }
            .build()
        withContext(Dispatchers.Main) {
            val player = AssistantVoicePlayer(context)
            try {
                assertTrue(player.play(105L, silentWav(seconds = 10)))
                awaitPlaying(player)
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(interruption))
                val engine = AssistantVoicePlayer::class.java.getDeclaredField("player").apply {
                    isAccessible = true
                }.get(player) as ExoPlayer
                withTimeout(5_000) { while (engine.isPlaying) delay(10) }
                assertNotEquals(AssistantVoicePlaybackPhase.PLAYING, player.state.value.phase)
                manager.abandonAudioFocusRequest(interruption)
                awaitPlaying(player)
                assertEquals(105L, player.state.value.messageId)
            } finally {
                manager.abandonAudioFocusRequest(interruption)
                player.stop()
            }
        }
    }

    @Test fun validReplyCanAcquireAudioFocusAndBeStoppedAndPlayedAgain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.Main) {
            val player = AssistantVoicePlayer(context)
            try {
                assertTrue(player.play(101L, silentWav()))
                awaitPlaying(player)
                player.stop()
                assertEquals(AssistantVoicePlaybackPhase.IDLE, player.state.value.phase)
                assertTrue(player.play(102L, silentWav()))
                awaitPlaying(player)
                assertEquals(102L, player.state.value.messageId)
            } finally {
                player.stop()
            }
        }
    }

    @Test fun invalidReplyReportsErrorAndDoesNotPreventNextPlayback() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.Main) {
            val player = AssistantVoicePlayer(context)
            try {
                assertFalse(player.play(103L, byteArrayOf(1, 2, 3)))
                assertEquals(AssistantVoicePlaybackPhase.ERROR, player.state.value.phase)
                assertTrue(player.play(104L, silentWav()))
                awaitPlaying(player)
            } finally {
                player.stop()
            }
        }
    }

    private suspend fun awaitPlaying(player: AssistantVoicePlayer) = withTimeout(5_000) {
        while (player.state.value.phase != AssistantVoicePlaybackPhase.PLAYING) {
            assertNotEquals(player.state.value.error, AssistantVoicePlaybackPhase.ERROR, player.state.value.phase)
            delay(10)
        }
    }

    private fun silentWav(seconds: Int = 1): ByteArray {
        val pcmSize = 32_000 * seconds
        return ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcmSize)
            put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1.toShort()); putShort(1.toShort()); putInt(16_000)
            putInt(32_000); putShort(2.toShort()); putShort(16.toShort())
            put("data".toByteArray()); putInt(pcmSize)
            put(ByteArray(pcmSize))
        }.array()
    }
}
