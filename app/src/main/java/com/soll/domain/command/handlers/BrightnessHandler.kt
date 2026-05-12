package com.soll.domain.command.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class BrightnessHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "brightness"
    override val description = "Настроить яркость: /brightness [0-100|авто|ручной]"

    override suspend fun execute(message: Message, args: String?) {
        if (!canWriteSettings()) {
            reply(
                message,
                "❌ Нет разрешения WRITE_SETTINGS.\n\n" +
                "Выдайте его в настройках приложения."
            )
            return
        }

        val arg = args?.trim()?.lowercase()

        when {
            arg.isNullOrEmpty() -> showCurrentBrightness(message)
            arg == "auto" || arg == "авто" -> setAutoBrightness(message, true)
            arg == "manual" || arg == "ручной" -> setAutoBrightness(message, false)
            else -> {
                val level = arg.toIntOrNull()
                if (level != null && level in 0..100) {
                    setBrightness(message, level)
                } else {
                    reply(message, "Использование: /brightness [0-100|авто|ручной]\n\nПримеры:\n/brightness 50\n/brightness авто")
                }
            }
        }
    }

    private fun canWriteSettings(): Boolean {
        return Settings.System.canWrite(context)
    }

    private suspend fun showCurrentBrightness(message: Message) {
        try {
            val isAuto = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

            val brightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )

            val brightnessPercent = (brightness * 100 / 255)

            val text = buildString {
                append("<b>🔆 Яркость</b>\n\n")
                append("Режим: ${if (isAuto) "авто" else "ручной"}\n")
                append("Уровень: $brightnessPercent%\n")
                append("\nИспользование:\n")
                append("/brightness 50 - поставить 50%\n")
                append("/brightness авто - включить автояркость\n")
                append("/brightness ручной - выключить автояркость")
            }

            reply(message, text)
        } catch (e: Exception) {
            reply(message, "❌ Не удалось прочитать яркость: ${e.message}")
        }
    }

    private suspend fun setBrightness(message: Message, percent: Int) {
        try {
            // Disable auto brightness first
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            // Convert percent to 0-255 range
            val brightnessValue = (percent * 255 / 100).coerceIn(1, 255)

            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightnessValue
            )

            reply(message, "✅ Яркость установлена: $percent%")
        } catch (e: Exception) {
            reply(message, "❌ Не удалось изменить яркость: ${e.message}")
        }
    }

    private suspend fun setAutoBrightness(message: Message, enabled: Boolean) {
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                } else {
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                }
            )

            reply(message, "✅ Автояркость ${if (enabled) "включена" else "выключена"}")
        } catch (e: Exception) {
            reply(message, "❌ Не удалось изменить автояркость: ${e.message}")
        }
    }
}
