package com.soll.domain.tts.book

import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.chatterbox.ChatterboxOnnxTtsEngine
import com.soll.domain.tts.chatterbox.ChatterboxPlaybackDiagnostics
import com.soll.domain.tts.chatterbox.ChatterboxPlaybackFailure
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatterboxBookEngine @Inject constructor(
    private val impl: ChatterboxOnnxTtsEngine,
) : TtsBookEngine {

    override val type: TtsEngineType = TtsEngineType.CHATTERBOX
    override val displayName: String = "Chatterbox Multilingual"

    override val isReady = impl.isReady
    override val isSpeaking = impl.isSpeaking
    override val downloadProgress = impl.downloadProgress
    override val currentWordRange = impl.currentWordRange
    val diagnostics: StateFlow<ChatterboxPlaybackDiagnostics> = impl.diagnostics
    val playbackFailures: SharedFlow<ChatterboxPlaybackFailure> = impl.playbackFailures

    override fun voiceOptions(): List<TtsVoiceOption> = impl.voiceOptions()

    override suspend fun prepare(): TtsPrepareResult = impl.initialize()

    override suspend fun speakChapter(text: String, onChapterFinished: () -> Unit) {
        impl.speakChapter(text, onChapterFinished)
    }

    override fun pause() = impl.pause()
    override suspend fun resume() = impl.resume()
    override fun stop() = impl.stop()
    override fun setSpeechRate(rate: Float) = impl.setSpeechRate(rate)
    override fun setPackId(packId: String?) = impl.setSelectedPackId(packId)

    override fun setVoiceId(voiceId: String) {
        impl.setSelectedVoiceId(voiceId)
    }

    override fun tunableSettings(): List<TtsEngineTunable> = listOf(
        TtsEngineTunable.Slider(
            key = "chatterbox_ort_intra_threads",
            label = "Потоки Chatterbox (ниже — экономичнее)",
            range = 1f..4f,
            defaultValue = impl.getOrtIntraThreads().toFloat(),
            materialSliderSteps = 3,
        ),
        TtsEngineTunable.Slider(
            key = "chatterbox_exaggeration",
            label = "Эмоциональность Chatterbox",
            range = 0.3f..0.9f,
            defaultValue = impl.getExaggeration(),
            materialSliderSteps = 5,
        ),
    )

    override fun applyTunable(key: String, value: Float) {
        when (key) {
            "chatterbox_ort_intra_threads" -> impl.applyOrtIntraThreadsTunable(value)
            "chatterbox_exaggeration" -> impl.applyExaggeration(value)
        }
    }

    override fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        impl.applyPerformanceProfile(profile)
    }

    override fun shutdown() = impl.shutdown()

    override fun isModelDownloaded(): Boolean = impl.isModelDownloaded()
}
