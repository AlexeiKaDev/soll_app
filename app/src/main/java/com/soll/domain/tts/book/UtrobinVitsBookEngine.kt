package com.soll.domain.tts.book

import com.soll.domain.tts.TtsBookPerformanceProfile
import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.UtrobinTtsEngine
import com.soll.domain.tts.UtrobinPlaybackDiagnostics
import com.soll.domain.tts.UtrobinPlaybackFailure
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UtrobinVitsBookEngine @Inject constructor(
    private val impl: UtrobinTtsEngine,
) : TtsBookEngine {

    override val type = TtsEngineType.UTROBIN
    override val displayName = "Utrobin VITS"

    override val isReady = impl.isReady
    override val isSpeaking = impl.isSpeaking
    override val downloadProgress = impl.downloadProgress
    override val currentWordRange = impl.currentWordRange
    val diagnostics: StateFlow<UtrobinPlaybackDiagnostics> = impl.diagnostics
    val playbackFailures: SharedFlow<UtrobinPlaybackFailure> = impl.playbackFailures

    override fun voiceOptions(): List<TtsVoiceOption> =
        UtrobinTtsEngine.SPEAKERS.map { (label, id) -> TtsVoiceOption(id.toString(), label) }

    override suspend fun prepare(): TtsPrepareResult = impl.initialize()

    override suspend fun speakChapter(text: String, onChapterFinished: () -> Unit) {
        impl.speakChapter(text, onChapterFinished)
    }

    override fun pause() = impl.pause()
    override suspend fun resume() = impl.resume()
    override fun stop() = impl.stop()
    override fun setSpeechRate(rate: Float) = impl.setSpeechRate(rate)

    override fun setVoiceId(voiceId: String) {
        impl.setSpeaker(voiceId.toIntOrNull() ?: 0)
    }

    override fun setPackId(packId: String?) = impl.setSelectedPackId(packId)

    override fun tunableSettings(): List<TtsEngineTunable> = listOf(
        TtsEngineTunable.Slider(
            key = "ort_intra_threads",
            label = "Потоки CPU (ниже — экономичнее)",
            range = 1f..4f,
            defaultValue = impl.getOrtIntraThreads().toFloat(),
            materialSliderSteps = 3,
        ),
    )

    override fun applyTunable(key: String, value: Float) {
        if (key == "ort_intra_threads") impl.applyOrtIntraThreadsTunable(value)
    }

    override fun applyPerformanceProfile(profile: TtsBookPerformanceProfile) {
        impl.applyPerformanceProfile(profile)
    }

    override fun shutdown() = impl.shutdown()
}
