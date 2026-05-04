package com.soll.domain.tts.book

import com.soll.domain.tts.NatashaTtsEngine
import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NatashaVitsBookEngine @Inject constructor(
    private val impl: NatashaTtsEngine,
) : TtsBookEngine {

    override val type = TtsEngineType.NATASHA
    override val displayName = "Natasha VITS2"

    override val isReady = impl.isReady
    override val isSpeaking = impl.isSpeaking
    override val downloadProgress = impl.downloadProgress
    override val currentWordRange = impl.currentWordRange

    override fun voiceOptions(): List<TtsVoiceOption> = emptyList()

    override suspend fun prepare(): TtsPrepareResult = impl.initialize()

    override suspend fun speakChapter(text: String, onChapterFinished: () -> Unit) {
        impl.speakChapter(text, onChapterFinished)
    }

    override fun pause() = impl.pause()
    override suspend fun resume() = impl.resume()
    override fun stop() = impl.stop()
    override fun setSpeechRate(rate: Float) = impl.setSpeechRate(rate)
    override fun setPackId(packId: String?) = impl.setSelectedPackId(packId)

    override fun tunableSettings(): List<TtsEngineTunable> = listOf(
        TtsEngineTunable.Slider(
            key = "natasha_ort_intra_threads",
            label = "Потоки CPU Natasha (ниже — экономичнее)",
            range = 1f..4f,
            defaultValue = impl.getOrtIntraThreads().toFloat(),
            materialSliderSteps = 3,
        ),
    )

    override fun applyTunable(key: String, value: Float) {
        if (key == "natasha_ort_intra_threads") impl.applyOrtIntraThreadsTunable(value)
    }

    override fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        impl.applyPerformanceProfile(profile)
    }

    override fun shutdown() = impl.shutdown()

    override fun isModelDownloaded(): Boolean = impl.isModelDownloaded()
}
