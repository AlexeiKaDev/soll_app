package com.soll.domain.command.handlers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "record"
    override val description = "Record audio: /record [seconds] (default 10, max 60)"

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    override suspend fun execute(message: Message, args: String?) {
        if (!hasPermission()) {
            reply(message, "Microphone permission not granted. Please grant RECORD_AUDIO permission in app settings.")
            return
        }

        val durationSeconds = (args?.toIntOrNull() ?: 10).coerceIn(1, 60)

        reply(message, "🎙 Recording audio for $durationSeconds seconds...")

        try {
            val audioFile = recordAudio(durationSeconds)

            if (audioFile != null && audioFile.exists()) {
                telegramRepository.sendVoice(
                    chatId = message.chat.id,
                    file = audioFile,
                    caption = "Audio recording (${durationSeconds}s)",
                    duration = durationSeconds
                )

                // Clean up
                audioFile.delete()
            } else {
                reply(message, "❌ Failed to record audio.")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error recording audio")
            reply(message, "❌ Error recording: ${e.message}")
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun recordAudio(durationSeconds: Int): File? {
        val outputFile = File(
            context.cacheDir,
            "recording_${dateFormat.format(Date())}.ogg"
        )

        var recorder: MediaRecorder? = null

        try {
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(48000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            // Record for specified duration
            delay(durationSeconds * 1000L)

            recorder.stop()
            recorder.release()

            return outputFile

        } catch (e: Exception) {
            Timber.e(e, "Recording failed")
            recorder?.release()
            outputFile.delete()
            throw e
        }
    }
}
