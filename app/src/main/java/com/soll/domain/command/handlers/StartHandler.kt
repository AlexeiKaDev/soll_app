package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class StartHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "start"
    override val description = "Show welcome message and available commands"

    override suspend fun execute(message: Message, args: String?) {
        val userName = message.from?.firstName ?: "User"

        val text = """
            |<b>Welcome to Soll, $userName!</b>
            |
            |This bot runs on an Android device and allows you to remotely control and monitor it.
            |
            |<b>System:</b>
            |/ping - Check if bot is alive
            |/status - Device status (battery, memory, network)
            |/info - Device information
            |/storage - Storage information
            |
            |<b>Files:</b>
            |/files [path] - Browse files
            |/download &lt;path&gt; - Download file
            |
            |<b>SMS &amp; Calls:</b>
            |/sms - Read SMS messages
            |/sms_send - Send SMS
            |/calls - View call log
            |/call - Make a call
            |/contacts - List contacts
            |
            |<b>Media:</b>
            |/location - Get GPS location
            |/photo - Take photo
            |/record - Record audio
            |
            |<b>Device Control:</b>
            |/notify, /vibrate, /flashlight, /volume
            |/brightness, /alarm, /bluetooth, /wifi
            |
            |/help - Show all commands
        """.trimMargin()

        reply(message, text)
    }
}
