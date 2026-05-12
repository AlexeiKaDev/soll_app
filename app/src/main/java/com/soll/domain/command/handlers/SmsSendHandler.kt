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
    override val description = "Отправить SMS: /sms_send <номер> <текст>"

    override suspend fun execute(message: Message, args: String?) {
        if (!hasPermission()) {
            reply(message, "Нет разрешения на отправку SMS. Выдайте SEND_SMS в настройках приложения.")
            return
        }

        if (args.isNullOrBlank()) {
            reply(message, "Использование: /sms_send <номер> <текст>\n\nПример: /sms_send +1234567890 Привет")
            return
        }

        val parts = args.trim().split(" ", limit = 2)
        if (parts.size < 2) {
            reply(message, "Использование: /sms_send <номер> <текст>\n\nНужны и номер, и текст сообщения.")
            return
        }

        val phoneNumber = parts[0]
        val smsText = parts[1]

        if (!isValidPhoneNumber(phoneNumber)) {
            reply(message, "Неверный формат номера: $phoneNumber")
            return
        }

        if (smsText.isBlank()) {
            reply(message, "Текст SMS не может быть пустым.")
            return
        }

        try {
            sendSms(phoneNumber, smsText)
            reply(message, "✅ SMS отправлено.\n\n<b>Кому:</b> $phoneNumber\n<b>Текст:</b> ${escapeHtml(smsText)}")
        } catch (e: Exception) {
            reply(message, "❌ Не удалось отправить SMS: ${e.message}")
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
            ?: throw IllegalStateException("SmsManager недоступен")

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
