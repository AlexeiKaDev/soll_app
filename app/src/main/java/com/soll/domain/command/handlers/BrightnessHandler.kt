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
    override val description = "Set brightness: /brightness [0-100|auto]"

    override suspend fun execute(message: Message, args: String?) {
        if (!canWriteSettings()) {
            reply(
                message,
                "❌ WRITE_SETTINGS permission not granted.\n\n" +
                "Please grant this permission in the app settings screen."
            )
            return
        }

        val arg = args?.trim()?.lowercase()

        when {
            arg.isNullOrEmpty() -> showCurrentBrightness(message)
            arg == "auto" -> setAutoBrightness(message, true)
            arg == "manual" -> setAutoBrightness(message, false)
            else -> {
                val level = arg.toIntOrNull()
                if (level != null && level in 0..100) {
                    setBrightness(message, level)
                } else {
                    reply(message, "Usage: /brightness [0-100|auto|manual]\n\nExamples:\n/brightness 50\n/brightness auto")
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
                append("<b>🔆 Brightness</b>\n\n")
                append("Mode: ${if (isAuto) "Auto" else "Manual"}\n")
                append("Level: $brightnessPercent%\n")
                append("\nUsage:\n")
                append("/brightness 50 - Set to 50%\n")
                append("/brightness auto - Enable auto\n")
                append("/brightness manual - Disable auto")
            }

            reply(message, text)
        } catch (e: Exception) {
            reply(message, "❌ Error reading brightness: ${e.message}")
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

            reply(message, "✅ Brightness set to $percent%")
        } catch (e: Exception) {
            reply(message, "❌ Error setting brightness: ${e.message}")
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

            reply(message, "✅ Auto-brightness ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            reply(message, "❌ Error setting auto-brightness: ${e.message}")
        }
    }
}
