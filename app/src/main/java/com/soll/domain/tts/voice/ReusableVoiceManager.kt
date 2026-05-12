package com.soll.domain.tts.voice

import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.book.TtsEngineTunable
import com.soll.domain.tts.book.TtsPrepareResult
import com.soll.domain.tts.book.TtsVoiceOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReusableVoiceManager @Inject constructor(
    chatterbox: ChatterboxVoiceEngine,
) {
    private val engines: Map<TtsEngineType, ReusableVoiceEngine> = mapOf(
        TtsEngineType.CHATTERBOX to chatterbox,
    )

    fun availableEngines(): List<TtsEngineType> = engines.keys.toList()

    fun hasEngine(type: TtsEngineType): Boolean = engines.containsKey(type)

    fun voiceOptions(type: TtsEngineType): List<TtsVoiceOption> =
        engines[type]?.voiceOptions().orEmpty()

    fun tunableSettings(type: TtsEngineType): List<TtsEngineTunable> =
        engines[type]?.tunableSettings().orEmpty()

    fun setPackId(type: TtsEngineType, packId: String?) {
        engines[type]?.setPackId(packId)
    }

    fun setVoiceId(type: TtsEngineType, voiceId: String?) {
        engines[type]?.setVoiceId(voiceId)
    }

    fun setSpeechRate(type: TtsEngineType, rate: Float) {
        engines[type]?.setSpeechRate(rate)
    }

    fun applyTunable(type: TtsEngineType, key: String, value: Float) {
        engines[type]?.applyTunable(key, value)
    }

    suspend fun prepare(type: TtsEngineType): TtsPrepareResult {
        val engine = engines[type]
            ?: return TtsPrepareResult(
                success = false,
                engineType = type,
                message = "Reusable voice engine '$type' не зарегистрирован",
            )
        return engine.prepare()
    }

    suspend fun synthesize(
        type: TtsEngineType,
        request: VoiceSynthesisRequest,
    ): VoiceSynthesisOutput {
        val engine = engines[type] ?: error("Reusable voice engine '$type' не зарегистрирован")
        return engine.synthesize(request)
    }

    fun shutdown(type: TtsEngineType) {
        engines[type]?.shutdown()
    }
}
