package com.soll.domain.tts.catalog

enum class TtsPackEngineFamily {
    PIPER,
    NATASHA,
    UTROBIN,
    ONNX_EXTERNAL,
}

enum class TtsPackSourceType {
    IMPORTED,
    LEGACY_INTERNAL,
    LEGACY_EXTERNAL,
    DOWNLOADED,
}

enum class TtsPackStatus {
    READY,
    READY_NON_RUSSIAN,
    INCOMPLETE,
    UNSUPPORTED_RUNTIME,
    DISABLED_RUNTIME,
    BROKEN_POINTER,
    INVALID_FILESET,
}

data class DetectedTtsVoice(
    val id: String,
    val label: String,
    val language: String = "ru",
    val isRussian: Boolean = language.equals("ru", ignoreCase = true),
    val sourcePath: String? = null,
)

data class DetectedTtsPack(
    val packId: String,
    val engineFamily: TtsPackEngineFamily,
    val displayName: String,
    val rootDir: String,
    val sourceType: TtsPackSourceType,
    val status: TtsPackStatus,
    val reason: String? = null,
    val isRussianCapable: Boolean = false,
    val runtimeFamily: String? = null,
    val voices: List<DetectedTtsVoice> = emptyList(),
    val suggestedDeletion: Boolean = false,
    val canDelete: Boolean = true,
    val modelId: String? = null,
    val precision: String? = null,
    val estimatedSizeMb: Int? = null,
) {
    val isRunnable: Boolean
        get() = status == TtsPackStatus.READY || status == TtsPackStatus.READY_NON_RUSSIAN

    val selectionKey: String?
        get() = if (modelId.isNullOrBlank() || precision.isNullOrBlank()) null else "$modelId|$precision"
}

data class DownloadableTtsPack(
    val id: String,
    val engineFamily: TtsPackEngineFamily,
    val displayName: String,
    val description: String,
    val estimatedSizeMb: Int,
    val isRussianCapable: Boolean,
    val suggestedEnginePackId: String? = null,
)

data class TtsPackDownloadState(
    val packId: String,
    val label: String,
    val progress: Float?,
    val message: String? = null,
    val isError: Boolean = false,
)

data class TtsPackImportResult(
    val detectedCount: Int,
    val importedCount: Int,
    val failedCount: Int,
)
