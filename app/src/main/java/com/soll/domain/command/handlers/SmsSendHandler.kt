package com.soll.domain.command.handlers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class SmsSendHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "sms_send"
    override val description = "Send SMS: /sms_send <number> <message>"

    override suspend fun execute(message: Message, args: String?) {
        if (!hasPermission()) {
            reply(message, "SMS permission not granted. Please grant SEND_SMS permission in app settings.")
            return
        }

        if (args.isNullOrBlank()) {
            reply(message, "Usage: /sms_send <phone_number> <message>\n\nExample: /sms_send +1234567890 Hello!")
            return
        }

        val parts = args.trim().split(" ", limit = 2)
        if (parts.size < 2) {
            reply(message, "Usage: /sms_send <phone_number> <message>\n\nPlease provide both phone number and message.")
            return
        }

        val phoneNumber = parts[0]
        val smsText = parts[1]

        if (!isValidPhoneNumber(phoneNumber)) {
            reply(message, "Invalid phone number format: $phoneNumber")
            return
        }

        if (smsText.isBlank()) {
            reply(message, "Message cannot be empty.")
            return
        }

        try {
            sendSms(phoneNumber, smsText)
            reply(message, "✅ SMS sent successfully!\n\n<b>To:</b> $phoneNumber\n<b>Message:</b> ${escapeHtml(smsText)}")
        } catch (e: Exception) {
            reply(message, "❌ Failed to send SMS: ${e.message}")
        }
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        // Basic validation: should contain digits, may start with + and have reasonable length
        val cleaned = phone.replace(Regex("[\\s\\-()]"), "")
        return cleaned.matches(Regex("^\\+?\\d{7,15}$"))
    }

    private fun sendSms(phoneNumber: String, message: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
            ?: throw IllegalStateException("SmsManager not available")

        // Split message if it's too long
        if (message.length > 160) {
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                parts,
                null,
                null
            )
        } else {
            smsManager.sendTextMessage(
                phoneNumber,
                null,
                message,
                null,
                null
            )
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
