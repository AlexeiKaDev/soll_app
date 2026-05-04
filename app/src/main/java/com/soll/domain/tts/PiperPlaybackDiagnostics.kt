package com.soll.domain.tts

data class PiperPlaybackDiagnostics(
    val packId: String? = null,
    val voiceId: String? = null,
    val voiceLabel: String? = null,
    val speechRate: Float = 1.0f,
    val sherpaThreads: Int = 2,
    val totalChunks: Int = 0,
    val completedChunks: Int = 0,
    val recoveredChunks: Int = 0,
    val failedChunks: Int = 0,
    val lastChunkPreview: String? = null,
    val lastChunkRange: IntRange? = null,
    val lastChunkSplitDepth: Int = 0,
    val lastRecoveryAction: String? = null,
    val lastFailureMessage: String? = null,
    val lastFailurePreview: String? = null,
    val lastFailureRange: IntRange? = null,
)

data class PiperPlaybackFailure(
    val message: String,
    val chunkPreview: String,
    val chunkRange: IntRange?,
    val packId: String?,
    val voiceId: String?,
    val voiceLabel: String?,
) {
    fun toUserMessage(): String {
        val voice = voiceLabel ?: voiceId ?: "Piper"
        val preview = chunkPreview.ifBlank { "фрагмент без текста" }
        return "Piper ($voice) остановлен: $message. Фрагмент: \"$preview\""
    }
}
