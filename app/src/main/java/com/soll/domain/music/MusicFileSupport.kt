package com.soll.domain.music

object MusicFileSupport {
    private val supportedExtensions = setOf(
        "mp3",
        "m4a",
        "aac",
        "flac",
        "ogg",
        "opus",
        "wav",
        "weba",
    )

    private val supportedMimeTypes = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/mp4",
        "audio/aac",
        "audio/aacp",
        "audio/flac",
        "audio/ogg",
        "audio/opus",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/webm",
        "application/ogg",
        "application/x-ogg",
    )

    fun isSupported(
        name: String?,
        mimeType: String?,
        strict: Boolean = true,
    ): Boolean {
        val mime = mimeType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
        val extension = name?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            .orEmpty()
        if (extension in supportedExtensions) return true
        if (mime in supportedMimeTypes) return true
        return !strict && mime.startsWith("audio/")
    }

    fun cleanTitle(displayName: String): String =
        displayName.substringBeforeLast('.', displayName).ifBlank { displayName }
}

object MusicQueueEngine {
    fun nextIndex(
        currentIndex: Int,
        size: Int,
        repeatMode: MusicRepeatMode,
    ): Int? {
        if (size <= 0) return null
        if (repeatMode == MusicRepeatMode.ONE) return currentIndex.coerceIn(0, size - 1)
        val next = currentIndex + 1
        return when {
            next < size -> next
            repeatMode == MusicRepeatMode.ALL -> 0
            else -> null
        }
    }

    fun previousIndex(currentIndex: Int, size: Int): Int? {
        if (size <= 0) return null
        return (currentIndex - 1).takeIf { it >= 0 }
    }
}
