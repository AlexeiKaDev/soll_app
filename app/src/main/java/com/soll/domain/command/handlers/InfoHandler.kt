package com.soll.domain.command.handlers

import android.content.Context
import android.os.Build
import com.soll.BuildConfig
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import java.util.*

class InfoHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "info"
    override val description = "Показать информацию об устройстве"

    override suspend fun execute(message: Message, args: String?) {
        val text = """
            |<b>Информация об устройстве</b>
            |
            |<b>Устройство:</b>
            |Производитель: ${Build.MANUFACTURER}
            |Модель: ${Build.MODEL}
            |Бренд: ${Build.BRAND}
            |Код устройства: ${Build.DEVICE}
            |
            |<b>Android:</b>
            |Версия: Android ${Build.VERSION.RELEASE}
            |Уровень API: ${Build.VERSION.SDK_INT}
            |Патч безопасности: ${Build.VERSION.SECURITY_PATCH}
            |
            |<b>Система:</b>
            |Плата: ${Build.BOARD}
            |Железо: ${Build.HARDWARE}
            |Загрузчик: ${Build.BOOTLOADER}
            |
            |<b>Сборка:</b>
            |ID сборки: ${Build.ID}
            |Fingerprint: ${Build.FINGERPRINT.take(50)}...
            |
            |<b>Soll:</b>
            |Версия: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            |Пакет: ${BuildConfig.APPLICATION_ID}
            |Debug: ${BuildConfig.DEBUG}
            |
            |<b>Locale:</b>
            |${Locale.getDefault().displayName}
            |Часовой пояс: ${TimeZone.getDefault().id}
        """.trimMargin()

        reply(message, text)
    }
}
