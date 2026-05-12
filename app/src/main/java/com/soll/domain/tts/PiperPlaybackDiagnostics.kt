package com.soll.domain.tts

data class PiperPlaybackDiagnostics(
    val packId: String? = null,
    val voiceId: String? = null,
    val voiceLabel: String? = null,
    val prosodyPresetKey: String = PiperProsodyPreset.DEFAULT.storageKey,
    val prosodyPresetLabel: String = PiperProsodyPreset.DEFAULT.displayName,
    val noiseScale: Float = PiperProsodyPreset.DEFAULT.noiseScale,
    val noiseScaleW: Float = PiperProsodyPreset.DEFAULT.noiseScaleW,
    val speechRate: Float = 1.0f,
    val sherpaThreads: Int = 2,
    val totalChunks: Int = 0,
    val completedChunks: Int = 0,
    val recoveredChunks: Int = 0,
    val failedChunks: Int = 0,
    val prefetchHits: Int = 0,
    val prefetchQueuedIndex: Int? = null,
    val lastChunkPreview: String? = null,
    val lastChunkRange: IntRange? = null,
    val lastChunkSplitDepth: Int = 0,
    val lastChunkDurationMs: Long? = null,
    val lastChunkAudioMs: Long? = null,
    val lastChunkPrefetched: Boolean = false,
    val lastPrefetchWaitMs: Long? = null,
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
