package com.soll.domain.tts.voice

import com.soll.domain.tts.TtsEngineType
import com.soll.domain.tts.book.TtsEngineTunable
import com.soll.domain.tts.book.TtsPrepareResult
import com.soll.domain.tts.book.TtsVoiceOption

data class VoiceSynthesisRequest(
    val text: String,
    val packId: String? = null,
    val voiceId: String? = null,
    val speechRate: Float = 1.0f,
)

data class VoiceSynthesisOutput(
    val engineType: TtsEngineType,
    val sampleRate: Int,
    val audio: FloatArray,
    val durationMs: Long,
    val packId: String? = null,
    val voiceId: String? = null,
    val referencePath: String? = null,
    val generatedTokens: Int? = null,
)

interface ReusableVoiceEngine {
    val engineType: TtsEngineType
    val displayName: String

    fun voiceOptions(): List<TtsVoiceOption>
    fun tunableSettings(): List<TtsEngineTunable> = emptyList()

    fun setPackId(packId: String?) {}
    fun setVoiceId(voiceId: String?) {}
    fun setSpeechRate(rate: Float) {}
    fun applyTunable(key: String, value: Float) {}

    suspend fun prepare(): TtsPrepareResult
    suspend fun synthesize(request: VoiceSynthesisRequest): VoiceSynthesisOutput

    fun shutdown() {}
}
