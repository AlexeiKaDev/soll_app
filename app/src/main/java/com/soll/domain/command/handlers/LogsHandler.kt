package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class LogsHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "logs"
    override val description = "Show recent command logs"

    override suspend fun execute(message: Message, args: String?) {
        val logs = telegramRepository.getCommandLogs(20).first()
        val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        if (logs.isEmpty()) {
            reply(message, "No command logs yet.")
            return
        }

        val text = buildString {
            append("<b>Recent Commands</b>\n\n")

            logs.forEach { log ->
                val date = dateFormat.format(Date(log.executedAt))
                val status = when (log.status) {
                    "success" -> "✅"
                    "error" -> "❌"
                    else -> "⏳"
                }
                append("$status <code>/${log.command}</code>")
                log.args?.let { append(" $it") }
                append("\n   $date")
                log.executionTimeMs?.let { append(" (${it}ms)") }
                if (log.status == "error" && log.errorMessage != null) {
                    append("\n   Error: ${log.errorMessage.take(50)}")
                }
                append("\n\n")
            }
        }

        reply(message, text)
    }
}
