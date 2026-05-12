package com.soll.domain.tts.catalog

enum class TtsPackEngineFamily {
    PIPER,
    NATASHA,
    UTROBIN,
    CHATTERBOX,
    ONNX_EXTERNAL,
}

enum class TtsPackSourceType {
    IMPORTED,
    LEGACY_INTERNAL,
    LEGACY_EXTERNAL,
    EXTERNAL_LINKED,
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
    val issues: List<String> = emptyList(),
)

enum class TtsTreeAccessState {
    UNSET,
    READY,
    NO_PERMISSION,
    INVALID_ROOT,
    PICKER_CANCELLED,
}

data class TtsTreeBrowserEntry(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val subtitle: String? = null,
)

data class TtsImportCandidatePreview(
    val sourceUri: String,
    val displayName: String,
    val engineFamily: TtsPackEngineFamily,
    val runtimeFamily: String? = null,
    val status: TtsPackStatus,
    val reason: String? = null,
    val voiceSummary: String? = null,
)

data class TtsImportBrowserState(
    val accessState: TtsTreeAccessState = TtsTreeAccessState.UNSET,
    val rootUri: String? = null,
    val rootLabel: String? = null,
    val currentUri: String? = null,
    val currentLabel: String? = null,
    val canGoUp: Boolean = false,
    val entries: List<TtsTreeBrowserEntry> = emptyList(),
    val candidates: List<TtsImportCandidatePreview> = emptyList(),
    val message: String? = null,
)
