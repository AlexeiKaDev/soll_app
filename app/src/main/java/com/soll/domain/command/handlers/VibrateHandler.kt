package com.soll.domain.command.handlers

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class VibrateHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "vibrate"
    override val description = "Вибрация устройства: /vibrate [мс]"

    override suspend fun execute(message: Message, args: String?) {
        val duration = args?.toLongOrNull() ?: 500L
        val actualDuration = duration.coerceIn(100L, 5000L) // Min 100ms, Max 5s

        vibrate(actualDuration)
        reply(message, "✅ Вибрация выполнена: ${actualDuration} мс.")
    }

    @Suppress("DEPRECATION")
    private fun vibrate(duration: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
