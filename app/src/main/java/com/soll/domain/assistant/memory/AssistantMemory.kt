package com.soll.domain.assistant.memory

import java.util.UUID

enum class AssistantMemoryCategory {
    SUGGESTION,
    PREFERENCE,
    TOOL_USAGE,
    COMMAND_PATTERN,
    DEVICE_PROFILE,
    SYSTEM,
}

data class AssistantMemory(
    val id: String = UUID.randomUUID().toString(),
    val category: AssistantMemoryCategory,
    val key: String,
    val title: String,
    val summary: String,
    val source: String,
    val confidence: Float,
    val payloadJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val lastUsedAt: Long? = null,
    val pinned: Boolean = false,
)

object AssistantMemoryExporter {
    fun toMarkdown(memories: List<AssistantMemory>): String {
        if (memories.isEmpty()) return "# Память Soll\n\nПамять пока пуста.\n"

        return buildString {
            appendLine("# Память Soll")
            appendLine()
            memories
                .sortedWith(
                    compareByDescending<AssistantMemory> { it.pinned }
                        .thenByDescending { it.updatedAt }
                        .thenBy { it.title.lowercase() }
                )
                .groupBy { it.category }
                .forEach { (category, items) ->
                    appendLine("## ${category.exportTitle()}")
                    appendLine()
                    items.forEach { memory ->
                        appendLine("- ${memory.title}")
                        appendLine("  - ${memory.summary}")
                        appendLine("  - Источник: ${memory.source}; уверенность: ${memory.confidence.coerceIn(0f, 1f)}")
                    }
                    appendLine()
                }
        }
    }

    fun toServerSummaryMarkdown(memories: List<AssistantMemory>): String {
        if (memories.isEmpty()) return "# Summary памяти Soll App\n\nНет сохраненных записей памяти.\n"

        return buildString {
            appendLine("# Summary памяти Soll App")
            appendLine()
            appendLine("Это безопасная сводка локальной памяти. Сырые логи, payload JSON и медиа не включены.")
            appendLine()
            memories
                .sortedWith(
                    compareByDescending<AssistantMemory> { it.pinned }
                        .thenByDescending { it.updatedAt }
                        .thenBy { it.title.lowercase() }
                )
                .groupBy { it.category }
                .forEach { (category, items) ->
                    appendLine("## ${category.exportTitle()}")
                    appendLine()
                    items.forEach { memory ->
                        appendLine("- ${memory.title}: ${memory.summary}")
                        appendLine("  - Источник: ${memory.source}; уверенность: ${memory.confidence.coerceIn(0f, 1f)}")
                    }
                    appendLine()
                }
        }
    }

    private fun AssistantMemoryCategory.exportTitle(): String =
        when (this) {
            AssistantMemoryCategory.SUGGESTION -> "Принятые предложения"
            AssistantMemoryCategory.PREFERENCE -> "Предпочтения"
            AssistantMemoryCategory.TOOL_USAGE -> "Инструменты"
            AssistantMemoryCategory.COMMAND_PATTERN -> "Команды"
            AssistantMemoryCategory.DEVICE_PROFILE -> "Устройства"
            AssistantMemoryCategory.SYSTEM -> "Система"
        }
}
