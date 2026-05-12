package com.soll.data.music

import android.content.Context
import com.soll.data.repository.MusicRepository
import com.soll.data.service.MusicPlaybackService
import com.soll.data.service.TtsService
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.music.MusicRepeatMode
import com.soll.domain.tts.TextToSpeechManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class MusicVoiceControlResult(
    val spokenText: String,
    val detailText: String = spokenText,
)

@Singleton
class MusicVoiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val ttsManager: TextToSpeechManager,
    private val capabilityRegistry: CapabilityRegistry,
    private val assistantEventLogger: AssistantEventLogger,
) {
    suspend fun play(): MusicVoiceControlResult = withContext(Dispatchers.IO) {
        capabilityBlock()?.let { return@withContext it }
        if (musicRepository.getAllTracks().isEmpty()) {
            return@withContext MusicVoiceControlResult("Медиатека пуста. Сначала добавь папку или треки в инструменте Музыка.")
        }
        withContext(Dispatchers.Main) {
            ttsManager.stop()
            TtsService.stop(context)
            MusicPlaybackService.play(context)
        }
        MusicVoiceControlResult("Включаю музыку.")
    }

    suspend fun pause(): MusicVoiceControlResult = withContext(Dispatchers.Main) {
        withContext(Dispatchers.IO) { capabilityBlock() }?.let { return@withContext it }
        val state = MusicPlaybackService.currentState()
        if (!state.isServiceActive || state.queueSize == 0) {
            return@withContext MusicVoiceControlResult("Музыка сейчас не запущена.")
        }
        MusicPlaybackService.pause(context)
        MusicVoiceControlResult("Музыка на паузе.")
    }

    suspend fun next(): MusicVoiceControlResult = withContext(Dispatchers.Main) {
        withContext(Dispatchers.IO) { capabilityBlock() }?.let { return@withContext it }
        val state = MusicPlaybackService.currentState()
        if (!state.isServiceActive || state.queueSize == 0) {
            return@withContext MusicVoiceControlResult("Музыка сейчас не запущена.")
        }
        if (state.queueSize == 1 && state.repeatMode != MusicRepeatMode.ALL) {
            return@withContext MusicVoiceControlResult("В очереди только один трек.")
        }
        MusicPlaybackService.next(context)
        MusicVoiceControlResult("Следующий трек.")
    }

    suspend fun previous(): MusicVoiceControlResult = withContext(Dispatchers.Main) {
        withContext(Dispatchers.IO) { capabilityBlock() }?.let { return@withContext it }
        val state = MusicPlaybackService.currentState()
        if (!state.isServiceActive || state.queueSize == 0) {
            return@withContext MusicVoiceControlResult("Музыка сейчас не запущена.")
        }
        MusicPlaybackService.previous(context)
        MusicVoiceControlResult("Предыдущий трек.")
    }

    private suspend fun capabilityBlock(): MusicVoiceControlResult? {
        val decision = capabilityRegistry.checkCommand(MUSIC_CAPABILITY_ID)
        if (decision.allowed) return null
        val message = decision.message.ifBlank { "Музыка отключена в настройках возможностей." }
        assistantEventLogger.logEvent(
            AssistantEvent(
                type = "music_voice_blocked",
                source = "voice",
                summary = message,
            )
        )
        return MusicVoiceControlResult(message)
    }

    private companion object {
        const val MUSIC_CAPABILITY_ID = "music"
    }
}
