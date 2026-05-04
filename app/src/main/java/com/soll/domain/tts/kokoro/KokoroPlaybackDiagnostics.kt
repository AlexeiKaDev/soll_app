package com.soll.domain.tts.kokoro

data class KokoroPlaybackDiagnostics(
    val packRoot: String? = null,
    val voiceId: String? = null,
    val speechRate: Float = 1.0f,
    val ortThreads: Int = 2,
    val totalChunks: Int = 0,
    val completedChunks: Int = 0,
    val recoveredChunks: Int = 0,
    val failedChunks: Int = 0,
    val lastChunkPreview: String? = null,
    val lastChunkRange: IntRange? = null,
    val lastChunkSplitDepth: Int = 0,
    val lastChunkDurationMs: Long? = null,
    val lastRecoveryAction: String? = null,
    val lastFailureMessage: String? = null,
    val lastFailurePreview: String? = null,
    val lastFailureRange: IntRange? = null,
)

data class KokoroPlaybackFailure(
    val message: String,
    val chunkPreview: String,
    val chunkRange: IntRange?,
    val packRoot: String?,
    val voiceId: String?,
) {
    fun toUserMessage(): String {
        val voice = voiceId ?: "Kokoro"
        val preview = chunkPreview.ifBlank { "фрагмент без текста" }
        return "Kokoro ($voice) остановлен: $message. Фрагмент: \"$preview\""
    }
}
