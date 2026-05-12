package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import com.soll.domain.soll.SollGateway
import com.soll.domain.soll.SollHealth
import com.soll.domain.soll.SollTaskBoard

class SyncHandler(
    context: Context,
    telegramRepository: TelegramRepository,
    private val sollGateway: SollGateway,
) : CommandHandler(context, telegramRepository) {

    override val command = "sync"
    override val description = "Проверить сервер Soll и получить задачи: /sync now"

    override suspend fun execute(message: Message, args: String?) {
        reply(message, "Проверяю сервер Soll и доску задач...")

        val healthResult = sollGateway.getHealth()
        if (healthResult.isFailure) {
            reply(message, "❌ Сервер Soll недоступен: ${healthResult.exceptionOrNull()?.message}")
            return
        }

        val taskBoardResult = sollGateway.getTaskBoard()
        val text = buildString {
            append("<b>Синхронизация Soll</b>\n\n")
            append(healthResult.getOrThrow().format())

            taskBoardResult.fold(
                onSuccess = { board ->
                    append("\n\n")
                    append(board.format())
                },
                onFailure = { error ->
                    append("\n\n❌ Не удалось получить задачи: ${error.message?.escapeHtml() ?: "ошибка"}")
                }
            )
        }

        reply(message, text)
    }

    private fun SollHealth.format(): String = buildString {
        append("<b>Сервер:</b> ${statusLabel(status)}\n")
        append("<b>Хранилище:</b> ${if (vaultAccessible) "доступно" else "недоступно"}\n")
        append("<b>Планировщик:</b> ${if (schedulerRunning) "запущен" else "остановлен"}\n")
        append("<b>Задач планировщика:</b> $jobsCount")
    }

    private fun SollTaskBoard.format(): String = buildString {
        append("<b>Доска задач:</b>\n")
        append("Сегодня: ${today.size}, входящих: ${inbox.size}, зависших: ${stale.size}, открытых всего: $openCount")
        if (today.isNotEmpty()) {
            append("\n\n<b>Сегодня:</b>")
            today.take(5).forEach { task ->
                append("\n• ${task.title.escapeHtml()}")
                task.projectName?.takeIf { it.isNotBlank() }?.let {
                    append(" <i>${it.escapeHtml()}</i>")
                }
            }
        }
    }

    private fun statusLabel(status: String): String = when (status.lowercase()) {
        "healthy" -> "работает"
        "degraded" -> "работает с проблемами"
        else -> status.escapeHtml()
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
