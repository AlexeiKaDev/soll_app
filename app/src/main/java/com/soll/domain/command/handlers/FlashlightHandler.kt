package com.soll.domain.command.handlers

import android.content.Context
import android.hardware.camera2.CameraManager
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class FlashlightHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "flashlight"
    override val description = "Переключить фонарик: /flashlight [вкл|выкл]"

    private var isFlashlightOn = false

    override suspend fun execute(message: Message, args: String?) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                reply(message, "❌ Камера с фонариком не найдена")
                return
            }

            val turnOn = when (args?.trim()?.lowercase()) {
                "on", "1", "true", "вкл", "включить", "да" -> true
                "off", "0", "false", "выкл", "выключить", "нет" -> false
                else -> !isFlashlightOn // Toggle
            }

            cameraManager.setTorchMode(cameraId, turnOn)
            isFlashlightOn = turnOn

            val status = if (turnOn) "включен 🔦" else "выключен"
            reply(message, "✅ Фонарик $status")
        } catch (e: Exception) {
            reply(message, "❌ Не удалось управлять фонариком: ${e.message}")
        }
    }
}
