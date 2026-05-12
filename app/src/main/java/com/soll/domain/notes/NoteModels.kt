package com.soll.domain.notes

import java.util.Locale

enum class NoteSyncStatus(
    val storageKey: String,
    val label: String,
) {
    DRAFT("draft", "Черновик"),
    QUEUED("queued", "В очереди"),
    SYNCING("syncing", "Отправляется"),
    SYNCED("synced", "Отправлено"),
    ERROR("error", "Ошибка");

    companion object {
        fun fromStorage(value: String?): NoteSyncStatus =
            entries.firstOrNull { it.storageKey == value } ?: DRAFT
    }
}

enum class NoteFilter(val label: String) {
    ALL("Все"),
    PINNED("Закрепленные"),
    UNSENT("Не отправлены"),
    ERRORS("Ошибки"),
    ARCHIVED("Архив"),
}

enum class NoteSort(val storageKey: String, val label: String) {
    UPDATED("updated", "Изменены"),
    CREATED("created", "Созданы"),
}

data class NoteSettings(
    val autoSync: Boolean = true,
    val wifiOnly: Boolean = false,
    val keepLocalAfterSync: Boolean = true,
    val defaultTags: String = "mobile, заметки",
)

object NoteTextNormalizer {
    private val hashTagRegex = Regex("""(?<!\S)#([\p{L}\p{N}_-]{2,50})""")

    fun deriveTitle(title: String, content: String): String {
        val explicit = title.trim()
        if (explicit.isNotBlank()) return explicit.take(TITLE_LIMIT)

        val firstLine = content.lineSequence()
            .map { it.trim().removePrefix("#").trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        return firstLine.take(TITLE_LIMIT).ifBlank { "Новая заметка" }
    }

    fun normalizeTags(vararg sources: String): List<String> {
        val directTags = sources.asSequence()
            .flatMap { source ->
                val explicitTagList = source.contains(",") || source.contains(";") || source.contains("\n")
                source.split(",", ";", "\n").asSequence()
                    .map { it.trim() }
                    .filter { chunk ->
                        explicitTagList || chunk.startsWith("#") || !chunk.contains(Regex("""\s"""))
                    }
            }
            .map { it.removePrefix("#") }

        val inlineTags = sources.asSequence()
            .flatMap { source -> hashTagRegex.findAll(source).map { it.groupValues[1] }.asSequence() }

        return (directTags + inlineTags)
            .map { tag ->
                tag.trim()
                    .lowercase(Locale.ROOT)
                    .replace(Regex("""\s+"""), "-")
                    .replace(Regex("""[^\p{L}\p{N}_-]+"""), "")
                    .trim('-', '_')
                    .take(TAG_LIMIT)
            }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_TAGS)
            .toList()
    }

    fun buildSnippet(content: String, maxLength: Int = 140): String =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .take(maxLength)
            .ifBlank { "Без текста" }

    private const val TITLE_LIMIT = 80
    private const val TAG_LIMIT = 40
    private const val MAX_TAGS = 24
}
