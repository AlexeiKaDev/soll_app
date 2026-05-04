package com.soll.domain.tts

data class UtrobinPlaybackDiagnostics(
    val packId: String? = null,
    val speakerId: Int = 0,
    val speakerLabel: String? = null,
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

data class UtrobinPlaybackFailure(
    val message: String,
    val chunkPreview: String,
    val chunkRange: IntRange?,
    val packId: String?,
    val speakerId: Int,
    val speakerLabel: String?,
) {
    fun toUserMessage(): String {
        val speaker = speakerLabel ?: "speaker $speakerId"
        val preview = chunkPreview.ifBlank { "фрагмент без текста" }
        return "Utrobin ($speaker) остановлен: $message. Фрагмент: \"$preview\""
    }
}
