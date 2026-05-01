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
    override val description = "List files in directory"

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override suspend fun execute(message: Message, args: String?) {
        val path = args?.trim()?.takeIf { it.isNotEmpty() }
            ?: Environment.getExternalStorageDirectory().absolutePath

        val file = File(path)

        if (!file.exists()) {
            reply(message, "Path not found: $path")
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
            reply(message, "Cannot access directory: $path\nPermission denied or invalid path.")
            return
        }

        if (files.isEmpty()) {
            reply(message, "<b>📁 $path</b>\n\n<i>Directory is empty</i>")
            return
        }

        // Sort: directories first, then files, alphabetically
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

        val text = buildString {
            append("<b>📁 $path</b>\n\n")

            // Show parent directory link if not root
            if (file.parentFile != null && file.absolutePath != "/") {
                append("📂 <code>..</code> (parent)\n")
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
                append("\n<i>... and ${sorted.size - 50} more items</i>\n")
            }

            append("\n<b>Total:</b> $dirCount folders, $fileCount files")
        }

        reply(message, text)
    }

    private fun getFileInfo(file: File): String {
        return buildString {
            append("<b>📄 File Info</b>\n\n")
            append("<b>Name:</b> ${file.name}\n")
            append("<b>Path:</b> <code>${file.absolutePath}</code>\n")
            append("<b>Size:</b> ${formatSize(file.length())}\n")
            append("<b>Modified:</b> ${dateFormat.format(Date(file.lastModified()))}\n")
            append("<b>Readable:</b> ${if (file.canRead()) "Yes" else "No"}\n")
            append("<b>Writable:</b> ${if (file.canWrite()) "Yes" else "No"}\n")

            // Get extension
            val ext = file.extension.lowercase()
            append("<b>Type:</b> ${getFileType(ext)}")
        }
    }

    private fun getFileType(extension: String): String {
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "Image"
            "mp4", "mkv", "avi", "mov", "wmv", "flv" -> "Video"
            "mp3", "wav", "ogg", "flac", "aac", "m4a" -> "Audio"
            "pdf" -> "PDF Document"
            "doc", "docx" -> "Word Document"
            "xls", "xlsx" -> "Excel Spreadsheet"
            "ppt", "pptx" -> "PowerPoint"
            "txt" -> "Text File"
            "zip", "rar", "7z", "tar", "gz" -> "Archive"
            "apk" -> "Android App"
            "json", "xml", "html", "css", "js" -> "Code/Markup"
            else -> extension.ifEmpty { "Unknown" }
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
