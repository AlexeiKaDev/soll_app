package com.soll.domain.tts.book

import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import kotlinx.coroutines.flow.StateFlow

/** One selectable book-reading TTS backend (system, Piper, Utrobin, …). */
interface TtsBookEngine {
    val type: TtsEngineType
    val displayName: String

    val isReady: StateFlow<Boolean>
    val isSpeaking: StateFlow<Boolean>
    val downloadProgress: StateFlow<Float?>
    val currentWordRange: StateFlow<IntRange?>

    /** Empty when the engine has no speaker/voice UI. */
    fun voiceOptions(): List<TtsVoiceOption>

    /**
     * Load models / warm up. For system engine may be a no-op; use [SystemAndroidBookEngine.setup]
     * for TextToSpeech construction.
     */
    suspend fun prepare(): TtsPrepareResult

    suspend fun speakChapter(text: String, onChapterFinished: () -> Unit)

    fun pause()
    suspend fun resume() {}
    fun stop()

    fun setSpeechRate(rate: Float)

    /** Applied only if [voiceOptions] is non-empty. */
    fun setVoiceId(voiceId: String) {}

    /** Applied only for offline engines that manage multiple local packs. */
    fun setPackId(packId: String?) {}

    fun shutdown()

    /** For engines that download voices (e.g. Piper). */
    fun isModelDownloaded(): Boolean = true

    /**
     * Extra user-visible knobs (sliders, toggles). Empty by default; extend per engine.
     */
    fun tunableSettings(): List<TtsEngineTunable> = emptyList()

    fun applyTunable(key: String, value: Float) {}

    /** CPU/thread budget and chunking for offline book engines. */
    fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {}
}

data class TtsPrepareResult(
    val success: Boolean,
    val engineType: TtsEngineType,
    val resolvedPackPath: String? = null,
    val resolvedVoiceId: String? = null,
    val message: String? = null,
)

data class TtsVoiceOption(
    val id: String,
    val label: String,
)

/** Declarative description for a future settings row (engine-specific). */
sealed class TtsEngineTunable {
    abstract val key: String
    abstract val label: String

    data class Slider(
        override val key: String,
        override val label: String,
        val range: ClosedFloatingPointRange<Float>,
        val defaultValue: Float,
        /** If set, passed to Material [Slider] `steps` (intervals between min and max). */
        val materialSliderSteps: Int? = null,
    ) : TtsEngineTunable()
}
