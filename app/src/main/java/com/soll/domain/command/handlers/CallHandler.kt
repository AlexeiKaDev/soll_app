package com.soll.domain.command.handlers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class CallHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "call"
    override val description = "Make a call: /call <phone_number>"

    override suspend fun execute(message: Message, args: String?) {
        if (args.isNullOrBlank()) {
            reply(message, "Usage: /call <phone_number>\n\nExample: /call +1234567890")
            return
        }

        val phoneNumber = args.trim()

        if (!isValidPhoneNumber(phoneNumber)) {
            reply(message, "❌ Invalid phone number format: $phoneNumber")
            return
        }

        try {
            if (hasCallPermission()) {
                // Direct call
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
                reply(message, "📞 Calling $phoneNumber...")
            } else {
                // Open dialer
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
                reply(message, "📱 Opening dialer for $phoneNumber\n\n<i>CALL_PHONE permission not granted, opened dialer instead.</i>")
            }
        } catch (e: Exception) {
            reply(message, "❌ Error initiating call: ${e.message}")
        }
    }

    private fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleaned = phone.replace(Regex("[\\s\\-()]"), "")
        return cleaned.matches(Regex("^\\+?\\d{7,15}$"))
    }
}
