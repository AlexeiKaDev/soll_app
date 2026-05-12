package com.soll.domain.assistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AssistantEventSummaryExporter {
    fun toServerSummaryMarkdown(events: List<AssistantEvent>): String {
        if (events.isEmpty()) {
            return "# Summary событий Soll App\n\nСобытий ассистента пока нет.\n"
        }

        val sorted = events.sortedByDescending { it.createdAt }
        return buildString {
            appendLine("# Summary событий Soll App")
            appendLine()
            appendLine("Это безопасная сводка событий ассистента. Payload JSON, Telegram-сообщения, медиа и сырые логи не включены.")
            appendLine()
            appendLine("## Счетчики")
            appendLine()
            sorted
                .groupingBy { it.type.ifBlank { "unknown" } }
                .eachCount()
                .toSortedMap()
                .forEach { (type, count) ->
                    appendLine("- $type: $count")
                }
            appendLine()
            appendLine("## Последние события")
            appendLine()
            sorted.forEach { event ->
                appendLine(
                    "- ${formatEventTime(event.createdAt)} | " +
                        "${event.type.safeInline()} | ${event.source.safeInline()} | ${event.summary.safeInline()}"
                )
            }
        }
    }

    private fun String.safeInline(): String =
        replace('\n', ' ')
            .replace('\r', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(240)
            .ifBlank { "-" }

    private fun formatEventTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}
