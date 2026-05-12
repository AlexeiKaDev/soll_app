package com.soll.domain.command.handlers

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import java.io.File
import java.text.DecimalFormat

class StorageHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "storage"
    override val description = "Показать информацию о хранилище"

    override suspend fun execute(message: Message, args: String?) {
        val internalStorage = getStorageInfo(Environment.getDataDirectory())
        val externalStorage = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            getStorageInfo(Environment.getExternalStorageDirectory())
        } else {
            null
        }

        val text = buildString {
            append("<b>Хранилище</b>\n\n")

            append("<b>Внутреннее хранилище:</b>\n")
            append(internalStorage)

            if (externalStorage != null) {
                append("\n\n<b>Внешнее хранилище:</b>\n")
                append(externalStorage)
            }

            // App specific storage
            val appDir = context.filesDir
            val appCacheDir = context.cacheDir
            append("\n\n<b>Хранилище приложения:</b>\n")
            append("Файлы: ${formatSize(getDirSize(appDir))}\n")
            append("Кэш: ${formatSize(getDirSize(appCacheDir))}")
        }

        reply(message, text)
    }

    private fun getStorageInfo(path: File): String {
        val stat = StatFs(path.absolutePath)
        val totalBytes = stat.totalBytes
        val availableBytes = stat.availableBytes
        val usedBytes = totalBytes - availableBytes
        val usedPercent = (usedBytes * 100.0 / totalBytes)

        val df = DecimalFormat("#.##")
        return buildString {
            append("Всего: ${formatSize(totalBytes)}\n")
            append("Занято: ${formatSize(usedBytes)} (${df.format(usedPercent)}%)\n")
            append("Доступно: ${formatSize(availableBytes)}")
        }
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) getDirSize(file) else file.length()
            }
        }
        return size
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
