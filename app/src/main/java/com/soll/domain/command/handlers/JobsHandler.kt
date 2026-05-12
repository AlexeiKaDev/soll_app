package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobStatus
import com.soll.domain.tool.ToolJobStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first

class JobsHandler(
    context: Context,
    telegramRepository: TelegramRepository,
    private val toolJobStore: ToolJobStore,
) : CommandHandler(context, telegramRepository) {

    override val command = "jobs"
    override val description = "Показать задачи инструментов: /jobs [id]"

    private val dateFormat = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())

    override suspend fun execute(message: Message, args: String?) {
        val query = args?.trim().orEmpty()
        if (query.isNotBlank()) {
            val job = toolJobStore.getJob(query)
            if (job == null) {
                reply(message, "Задача <code>${query.escapeHtml()}</code> не найдена.")
                return
            }
            reply(message, job.formatDetails())
            return
        }

        val jobs = toolJobStore.getRecentJobs(limit = 10).first()
        if (jobs.isEmpty()) {
            reply(message, "Задач инструментов пока нет.")
            return
        }

        val text = buildString {
            append("<b>Последние задачи инструментов</b>\n\n")
            jobs.forEach { job ->
                append(job.status.icon())
                append(" <code>${job.id.take(8)}</code> ")
                append("<b>${job.toolId.escapeHtml()}</b> - ${job.status.label()}")
                job.progressPercent?.let { append(" ($it%)") }
                append("\n")
                append("   ${dateFormat.format(Date(job.updatedAt))}")
                if (job.logText.isNotBlank()) {
                    append("\n   ${job.logText.lineSequence().last().take(80).escapeHtml()}")
                }
                append("\n\n")
            }
            append("Детали: <code>/jobs &lt;id&gt;</code>")
        }

        reply(message, text)
    }

    private fun ToolJob.formatDetails(): String = buildString {
        append("<b>Задача инструмента</b>\n\n")
        append("<b>ID:</b> <code>${id.escapeHtml()}</code>\n")
        append("<b>Инструмент:</b> ${toolId.escapeHtml()}\n")
        append("<b>Статус:</b> ${status.icon()} ${status.label()}\n")
        progressPercent?.let { append("<b>Прогресс:</b> $it%\n") }
        append("<b>Создана:</b> ${dateFormat.format(Date(createdAt))}\n")
        append("<b>Обновлена:</b> ${dateFormat.format(Date(updatedAt))}\n")
        finishedAt?.let { append("<b>Завершена:</b> ${dateFormat.format(Date(it))}\n") }
        if (inputJson.isNotBlank()) {
            append("\n<b>Вход:</b>\n<code>${inputJson.take(500).escapeHtml()}</code>\n")
        }
        outputJson?.takeIf { it.isNotBlank() }?.let {
            append("\n<b>Выход:</b>\n<code>${it.take(500).escapeHtml()}</code>\n")
        }
        if (logText.isNotBlank()) {
            append("\n<b>Лог:</b>\n<code>${logText.takeLast(1200).escapeHtml()}</code>")
        }
    }

    private fun ToolJobStatus.icon(): String = when (this) {
        ToolJobStatus.QUEUED -> "⏳"
        ToolJobStatus.RUNNING -> "🔄"
        ToolJobStatus.WAITING_FOR_CONFIRMATION -> "⏸"
        ToolJobStatus.SUCCESS -> "✅"
        ToolJobStatus.FAILED -> "❌"
        ToolJobStatus.CANCELLED -> "🚫"
        ToolJobStatus.BLOCKED -> "⛔"
    }

    private fun ToolJobStatus.label(): String = when (this) {
        ToolJobStatus.QUEUED -> "в очереди"
        ToolJobStatus.RUNNING -> "выполняется"
        ToolJobStatus.WAITING_FOR_CONFIRMATION -> "ждет подтверждения"
        ToolJobStatus.SUCCESS -> "успешно"
        ToolJobStatus.FAILED -> "ошибка"
        ToolJobStatus.CANCELLED -> "отменено"
        ToolJobStatus.BLOCKED -> "заблокировано"
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
