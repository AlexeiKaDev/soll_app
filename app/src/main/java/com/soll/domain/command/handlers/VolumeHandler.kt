package com.soll.domain.command.handlers

import android.content.Context
import android.media.AudioManager
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class VolumeHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "volume"
    override val description = "Set or get media volume"

    override suspend fun execute(message: Message, args: String?) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val currentPercent = (currentVolume * 100 / maxVolume)

        if (args.isNullOrBlank()) {
            // Show current volume
            val text = """
                |<b>Volume Status</b>
                |
                |Media: $currentPercent%
                |Ring: ${getVolumePercent(audioManager, AudioManager.STREAM_RING)}%
                |Notification: ${getVolumePercent(audioManager, AudioManager.STREAM_NOTIFICATION)}%
                |Alarm: ${getVolumePercent(audioManager, AudioManager.STREAM_ALARM)}%
                |
                |Usage: /volume [0-100]
            """.trimMargin()
            reply(message, text)
            return
        }

        val targetPercent = args.toIntOrNull()
        if (targetPercent == null || targetPercent !in 0..100) {
            reply(message, "❌ Invalid volume. Use: /volume [0-100]")
            return
        }

        val targetVolume = (targetPercent * maxVolume / 100)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

        reply(message, "✅ Media volume set to $targetPercent%")
    }

    private fun getVolumePercent(audioManager: AudioManager, stream: Int): Int {
        val max = audioManager.getStreamMaxVolume(stream)
        val current = audioManager.getStreamVolume(stream)
        return if (max > 0) (current * 100 / max) else 0
    }
}
