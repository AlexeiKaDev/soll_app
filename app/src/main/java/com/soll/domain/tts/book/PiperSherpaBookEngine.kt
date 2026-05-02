package com.soll.domain.tts.book

import com.soll.domain.tts.SileroJitEngine
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PiperSherpaBookEngine @Inject constructor(
    private val impl: SileroJitEngine,
) : TtsBookEngine {

    override val type = TtsEngineType.SILERO
    override val displayName = "Piper (Sherpa-ONNX)"

    override val isReady = impl.isReady
    override val isSpeaking = impl.isSpeaking
    override val downloadProgress = impl.downloadProgress
    override val currentWordRange = impl.currentWordRange

    override fun voiceOptions(): List<TtsVoiceOption> =
        SileroJitEngine.VOICES.map { TtsVoiceOption(it.id, it.label) }

    override suspend fun prepare(): Boolean = impl.initialize()

    override suspend fun speakChapter(text: String, onChapterFinished: () -> Unit) {
        impl.speakChapter(text, onChapterFinished)
    }

    override fun pause() = impl.pause()
    override suspend fun resume() = impl.resume()
    override fun stop() = impl.stop()
    override fun setSpeechRate(rate: Float) = impl.setSpeechRate(rate)

    override fun setVoiceId(voiceId: String) = impl.setVoice(voiceId)

    override fun tunableSettings(): List<TtsEngineTunable> = listOf(
        TtsEngineTunable.Slider(
            key = "sherpa_num_threads",
            label = "Потоки Piper/Sherpa (ниже — экономичнее)",
            range = 1f..4f,
            defaultValue = impl.getSherpaNumThreads().toFloat(),
            materialSliderSteps = 3,
        ),
    )

    override fun applyTunable(key: String, value: Float) {
        if (key == "sherpa_num_threads") impl.applySherpaNumThreads(value)
    }

    override fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        impl.applyPerformanceProfile(profile)
    }

    override fun shutdown() = impl.shutdown()

    override fun isModelDownloaded(): Boolean = impl.isModelDownloaded()
}
