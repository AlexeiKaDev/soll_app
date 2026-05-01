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
    override val description = "Toggle flashlight on/off"

    private var isFlashlightOn = false

    override suspend fun execute(message: Message, args: String?) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId == null) {
                reply(message, "❌ No camera with flashlight found")
                return
            }

            val turnOn = when (args?.lowercase()) {
                "on", "1", "true" -> true
                "off", "0", "false" -> false
                else -> !isFlashlightOn // Toggle
            }

            cameraManager.setTorchMode(cameraId, turnOn)
            isFlashlightOn = turnOn

            val status = if (turnOn) "ON 🔦" else "OFF"
            reply(message, "✅ Flashlight is now $status")
        } catch (e: Exception) {
            reply(message, "❌ Failed to control flashlight: ${e.message}")
        }
    }
}
