package com.soll.presentation.screens.chat

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val chatTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun formatChatTimeLabel(
    createdAt: String,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val value = createdAt.trim()
    if (value.isBlank()) return ""

    parseOffsetInstant(value)?.let { instant ->
        return chatTimeFormatter.format(instant.atZone(zoneId))
    }

    runCatching { LocalDateTime.parse(value) }
        .getOrNull()
        ?.let { return chatTimeFormatter.format(it) }

    return Regex("""\b\d{2}:\d{2}\b""")
        .find(value)
        ?.value
        .orEmpty()
}

private fun parseOffsetInstant(value: String): Instant? =
    runCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrNull()
        ?: runCatching { Instant.parse(value) }.getOrNull()
