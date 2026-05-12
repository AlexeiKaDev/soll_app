package com.soll.domain.tts.chatterbox

data class ChatterboxPlaybackDiagnostics(
    val packId: String? = null,
    val runtimeFamily: String? = null,
    val languageId: String = "ru",
    val voiceId: String? = null,
    val referenceVoicePath: String? = null,
    val speechRate: Float = 1.0f,
    val exaggeration: Float = 0.5f,
    val ortThreads: Int = 2,
    val totalChunks: Int = 0,
    val completedChunks: Int = 0,
    val recoveredChunks: Int = 0,
    val failedChunks: Int = 0,
    val lastChunkPreview: String? = null,
    val lastChunkRange: IntRange? = null,
    val lastChunkSplitDepth: Int = 0,
    val lastChunkDurationMs: Long? = null,
    val lastGeneratedTokens: Int? = null,
    val lastRecoveryAction: String? = null,
    val lastFailureMessage: String? = null,
    val lastFailurePreview: String? = null,
    val lastFailureRange: IntRange? = null,
)

data class ChatterboxPlaybackFailure(
    val message: String,
    val chunkPreview: String,
    val chunkRange: IntRange?,
    val packId: String?,
    val languageId: String,
) {
    fun toUserMessage(): String {
        val preview = chunkPreview.ifBlank { "фрагмент без текста" }
        return "Chatterbox ($languageId) остановлен: $message. Фрагмент: \"$preview\""
    }
}
