package com.soll.domain.tts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SileroJitEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

    private val _currentWordRange = MutableStateFlow<IntRange?>(null)
    val currentWordRange: StateFlow<IntRange?> = _currentWordRange.asStateFlow()

    private var sherpaNumThreads: Int = 2

    fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        sherpaNumThreads = TtsBookPerformanceProfile.sherpaNumThreads(
            profile,
            Runtime.getRuntime().availableProcessors(),
        )
    }

    fun getSherpaNumThreads(): Int = sherpaNumThreads

    fun applySherpaNumThreads(value: Float) {
        sherpaNumThreads = value.toInt().coerceIn(1, 4)
    }

    companion object {
        private const val MODEL_BASE_DIR = "piper_ru"

        // Preserved for compatibility with existing settings/UI.
        val VOICES = listOf(
            Voice("irina", "Ирина (ж)", "vits-piper-ru_RU-irina-medium"),
            Voice("denis", "Денис (м)", "vits-piper-ru_RU-denis-medium"),
            Voice("dmitri", "Дмитрий (м)", "vits-piper-ru_RU-dmitri-medium"),
            Voice("ruslan", "Руслан (м)", "vits-piper-ru_RU-ruslan-medium"),
        )

    }

    data class Voice(val id: String, val label: String, val archiveName: String) {
        val onnxFilename get() = "ru_RU-$id-medium.onnx"
    }

    private var currentVoice = VOICES[0]

    fun isModelDownloaded(): Boolean = isVoiceDownloaded(currentVoice)

    private fun isVoiceDownloaded(voice: Voice): Boolean {
        val dir = File(context.filesDir, MODEL_BASE_DIR)
        val voiceDir = File(dir, voice.archiveName)
        return voiceDir.exists() &&
                File(voiceDir, voice.onnxFilename).let { it.exists() && it.length() > 1_000_000 } &&
                File(voiceDir, "tokens.txt").exists() &&
                File(voiceDir, "espeak-ng-data").exists()
    }

    fun setVoice(voiceId: String) {
        val voice = VOICES.find { it.id == voiceId } ?: VOICES[0]
        if (voice.id != currentVoice.id) currentVoice = voice
    }

    fun setUseV5(enabled: Boolean) {}
    fun setV5SpeakerId(id: Int) {}

    suspend fun initialize(): Boolean {
        Timber.w("Piper/Sherpa engine is disabled: local sherpa AAR removed from project")
        _isReady.value = false
        return false
    }

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit = {}) {
        _isSpeaking.value = false
        _currentWordRange.value = null
        onChapterFinished()
    }

    suspend fun resume() {}

    fun pause() {
        _isSpeaking.value = false
        _currentWordRange.value = null
    }

    fun stop() {
        _isSpeaking.value = false
        _currentWordRange.value = null
    }

    fun setSpeechRate(rate: Float) {}

    fun shutdown() { stop(); _isReady.value = false }
}
