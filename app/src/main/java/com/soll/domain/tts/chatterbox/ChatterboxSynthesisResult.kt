package com.soll.domain.tts.chatterbox

data class ChatterboxSynthesisResult(
    val audio: FloatArray,
    val sampleRate: Int,
    val generatedTokens: Int,
    val durationMs: Long,
    val voiceId: String?,
    val referenceVoicePath: String?,
)
