package com.soll.domain.command.handlers

import android.content.Context
import android.os.Environment
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import timber.log.Timber
import java.io.File
import java.text.DecimalFormat

class DownloadHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "download"
    override val description = "Отправить файл: /download <путь>"

    // Telegram file size limit is 50MB for bots
    private val maxFileSize = 50L * 1024 * 1024

    override suspend fun execute(message: Message, args: String?) {
        if (args.isNullOrBlank()) {
            reply(message, "Использование: /download <путь_к_файлу>\n\nПример: /download /sdcard/Download/file.pdf")
            return
        }

        val filePath = args.trim()
        val file = File(filePath)

        if (!file.exists()) {
            reply(message, "❌ Файл не найден: $filePath")
            return
        }

        if (file.isDirectory) {
            reply(message, "❌ Нельзя отправить папку. Используйте /files для просмотра.")
            return
        }

        if (!file.canRead()) {
            reply(message, "❌ Не удается прочитать файл: нет доступа.")
            return
        }

        val fileSize = file.length()
        if (fileSize > maxFileSize) {
            reply(message, "❌ Файл слишком большой (${formatSize(fileSize)}). Максимум 50 MB.")
            return
        }

        if (fileSize == 0L) {
            reply(message, "❌ Файл пустой.")
            return
        }

        reply(message, "📤 Отправляю: ${file.name} (${formatSize(fileSize)})...")

        try {
            val result = telegramRepository.sendDocument(
                chatId = message.chat.id,
                file = file,
                caption = "📄 ${file.name}"
            )

            if (result.isFailure) {
                reply(message, "❌ Не удалось отправить файл: ${result.exceptionOrNull()?.message}")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error uploading file")
            reply(message, "❌ Ошибка отправки файла: ${e.message}")
        }
    }

    private fun formatSize(bytes: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1024L * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024 * 1024))} GB"
            bytes >= 1024L * 1024 -> "${df.format(bytes / (1024.0 * 1024))} MB"
            bytes >= 1024L -> "${df.format(bytes / 1024.0)} KB"
            else -> "$bytes B"
        }
    }
}
