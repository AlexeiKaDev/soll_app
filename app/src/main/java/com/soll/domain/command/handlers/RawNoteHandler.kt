package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.local.entity.NoteEntity
import com.soll.data.repository.NoteRepository
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import com.soll.domain.notes.NoteSyncStatus

class RawNoteHandler(
    context: Context,
    telegramRepository: TelegramRepository,
    private val noteRepository: NoteRepository,
) : CommandHandler(context, telegramRepository) {

    override val command = "raw"
    override val description = "Создать raw-заметку в Soll: /raw текст"

    override suspend fun execute(message: Message, args: String?) {
        val text = args?.trim().orEmpty()
        if (text.isBlank()) {
            reply(message, "Напишите текст заметки после команды: <code>/raw текст заметки</code>")
            return
        }

        val title = text.lineSequence()
            .firstOrNull()
            ?.trim()
            ?.take(80)
            ?.ifBlank { null }
            ?: "Заметка из Telegram"

        runCatching {
            noteRepository.captureAndSend(
                title = title,
                content = text,
                tags = listOf("telegram"),
                source = NoteEntity.SOURCE_TELEGRAM,
            )
        }.fold(
            onSuccess = { result ->
                val response = when (result.syncStatus) {
                    NoteSyncStatus.SYNCED -> {
                        "✅ Заметка сохранена и отправлена в Soll\nФайл: <code>${result.filename?.escapeHtml().orEmpty()}</code>"
                    }
                    else -> {
                        "⚠️ Заметка сохранена на телефоне и поставлена в очередь отправки"
                    }
                }
                reply(message, response)
            },
            onFailure = { error ->
                reply(message, "❌ Не удалось сохранить заметку: ${error.message?.escapeHtml() ?: "ошибка"}")
            },
        )
    }

    private fun String.escapeHtml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
