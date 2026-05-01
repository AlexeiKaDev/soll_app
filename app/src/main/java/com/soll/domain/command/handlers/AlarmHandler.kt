package com.soll.domain.command.handlers

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import kotlinx.coroutines.delay
import timber.log.Timber

class AlarmHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "alarm"
    override val description = "Play loud alarm sound to find device"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override suspend fun execute(message: Message, args: String?) {
        val durationSeconds = (args?.toIntOrNull() ?: 10).coerceIn(1, 30)

        reply(message, "🔊 Playing alarm for $durationSeconds seconds...")

        var mediaPlayer: MediaPlayer? = null
        var originalVolume: Int = 0

        try {
            // Save original volume
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

            // Set volume to maximum
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // Get alarm sound
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = true
                prepare()
                start()
            }

            // Also vibrate
            startVibration(durationSeconds * 1000L)

            // Wait for duration
            delay(durationSeconds * 1000L)

            reply(message, "🔕 Alarm stopped.")

        } catch (e: Exception) {
            Timber.e(e, "Error playing alarm")
            reply(message, "❌ Error playing alarm: ${e.message}")
        } finally {
            // Stop and release
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }

            // Restore original volume
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            } catch (e: Exception) {
                Timber.e(e, "Error restoring volume")
            }

            // Stop vibration
            stopVibration()
        }
    }

    @Suppress("DEPRECATION")
    private fun startVibration(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Vibrate pattern: vibrate 500ms, pause 200ms, repeat
                    val pattern = longArrayOf(0, 500, 200, 500, 200)
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    val pattern = longArrayOf(0, 500, 200, 500, 200)
                    vibrator.vibrate(pattern, 0)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting vibration")
        }
    }

    @Suppress("DEPRECATION")
    private fun stopVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping vibration")
        }
    }
}
