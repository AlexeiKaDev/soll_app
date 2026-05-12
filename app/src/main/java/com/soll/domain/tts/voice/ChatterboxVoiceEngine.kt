package com.soll.domain.tts.voice

import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.book.TtsEngineTunable
import com.soll.domain.tts.book.TtsPrepareResult
import com.soll.domain.tts.book.TtsVoiceOption
import com.soll.domain.tts.chatterbox.ChatterboxOnnxTtsEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatterboxVoiceEngine @Inject constructor(
    private val impl: ChatterboxOnnxTtsEngine,
) : ReusableVoiceEngine {

    override val engineType: TtsEngineType = TtsEngineType.CHATTERBOX
    override val displayName: String = "Chatterbox Multilingual"

    override fun voiceOptions(): List<TtsVoiceOption> = impl.voiceOptions()

    override fun tunableSettings(): List<TtsEngineTunable> = listOf(
        TtsEngineTunable.Slider(
            key = "chatterbox_ort_intra_threads",
            label = "Потоки Chatterbox",
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

    override fun setPackId(packId: String?) {
        impl.setSelectedPackId(packId)
    }

    override fun setVoiceId(voiceId: String?) {
        impl.setSelectedVoiceId(voiceId)
    }

    override fun setSpeechRate(rate: Float) {
        impl.setSpeechRate(rate)
    }

    override fun applyTunable(key: String, value: Float) {
        when (key) {
            "chatterbox_ort_intra_threads" -> impl.applyOrtIntraThreadsTunable(value)
            "chatterbox_exaggeration" -> impl.applyExaggeration(value)
        }
    }

    override suspend fun prepare(): TtsPrepareResult = impl.initialize()

    override suspend fun synthesize(request: VoiceSynthesisRequest): VoiceSynthesisOutput {
        setPackId(request.packId)
        setVoiceId(request.voiceId)
        setSpeechRate(request.speechRate)
        val result = impl.synthesize(request.text)
        return VoiceSynthesisOutput(
            engineType = engineType,
            sampleRate = result.sampleRate,
            audio = result.audio,
            durationMs = result.durationMs,
            packId = impl.diagnostics.value.packId,
            voiceId = result.voiceId,
            referencePath = result.referenceVoicePath,
            generatedTokens = result.generatedTokens,
        )
    }

    override fun shutdown() {
        impl.shutdown()
    }
}
