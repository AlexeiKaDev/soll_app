package com.soll.domain.command.handlers

import android.content.Context
import android.os.Environment
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FilesHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "files"
    override val description = "Показать файлы в папке"

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override suspend fun execute(message: Message, args: String?) {
        val path = args?.trim()?.takeIf { it.isNotEmpty() }
            ?: Environment.getExternalStorageDirectory().absolutePath

        val file = File(path)

        if (!file.exists()) {
            reply(message, "Путь не найден: $path")
            return
        }

        if (!file.isDirectory) {
            // It's a file, show file info
            val info = getFileInfo(file)
            reply(message, info)
            return
        }

        // List directory contents
        val files = file.listFiles()
        if (files == null) {
            reply(message, "Нет доступа к папке: $path\nПроверьте разрешения или путь.")
            return
        }

        if (files.isEmpty()) {
            reply(message, "<b>📁 $path</b>\n\n<i>Папка пустая</i>")
            return
        }

        // Sort: directories first, then files, alphabetically
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        val text = buildString {
            append("<b>📁 $path</b>\n\n")

            // Show parent directory link if not root
            if (file.parentFile != null && file.absolutePath != "/") {
                append("📂 <code>..</code> (выше)\n")
            }

            var dirCount = 0
            var fileCount = 0

            sorted.take(50).forEach { f ->
                if (f.isDirectory) {
                    dirCount++
                    append("📂 <code>${f.name}</code>\n")
                } else {
                    fileCount++
                    val size = formatSize(f.length())
                    append("📄 <code>${f.name}</code> ($size)\n")
                }
            }

            if (sorted.size > 50) {
                append("\n<i>... еще элементов: ${sorted.size - 50}</i>\n")
            }

            append("\n<b>Итого:</b> папок: $dirCount, файлов: $fileCount")
        }

        reply(message, text)
    }

    private fun getFileInfo(file: File): String {
        return buildString {
            append("<b>📄 Файл</b>\n\n")
            append("<b>Имя:</b> ${file.name}\n")
            append("<b>Путь:</b> <code>${file.absolutePath}</code>\n")
            append("<b>Размер:</b> ${formatSize(file.length())}\n")
            append("<b>Изменен:</b> ${dateFormat.format(Date(file.lastModified()))}\n")
            append("<b>Чтение:</b> ${if (file.canRead()) "да" else "нет"}\n")
            append("<b>Запись:</b> ${if (file.canWrite()) "да" else "нет"}\n")

            // Get extension
            val ext = file.extension.lowercase()
            append("<b>Тип:</b> ${getFileType(ext)}")
        }
    }

    private fun getFileType(extension: String): String {
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "изображение"
            "mp4", "mkv", "avi", "mov", "wmv", "flv" -> "видео"
            "mp3", "wav", "ogg", "flac", "aac", "m4a" -> "аудио"
            "pdf" -> "PDF-документ"
            "doc", "docx" -> "Word-документ"
            "xls", "xlsx" -> "Excel-таблица"
            "ppt", "pptx" -> "PowerPoint"
            "txt" -> "текстовый файл"
            "zip", "rar", "7z", "tar", "gz" -> "архив"
            "apk" -> "Android-приложение"
            "json", "xml", "html", "css", "js" -> "код/разметка"
            else -> extension.ifEmpty { "неизвестно" }
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
