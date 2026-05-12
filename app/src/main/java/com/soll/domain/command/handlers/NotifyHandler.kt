package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.notification.SollNotificationChannels
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler
import com.soll.domain.notification.SollNotificationCenter
import com.soll.domain.notification.SollNotificationChannel
import com.soll.domain.notification.SollNotificationPriority
import com.soll.domain.notification.SollNotificationRequest
import org.json.JSONObject

class NotifyHandler(
    context: Context,
    telegramRepository: TelegramRepository,
    private val notificationCenter: SollNotificationCenter,
) : CommandHandler(context, telegramRepository) {

    override val command = "notify"
    override val description = "Показать локальное уведомление на устройстве"

    override suspend fun execute(message: Message, args: String?) {
        if (args.isNullOrBlank()) {
            reply(message, "Использование: /notify [текст]\n\nПример: /notify Проверить музыку")
            return
        }

        val notification = notificationCenter.post(
            SollNotificationRequest(
                channel = SollNotificationChannel.ALERTS,
                type = "telegram_notify",
                source = "telegram",
                title = "Soll",
                message = args.trim(),
                payloadJson = JSONObject()
                    .put("chat_id", message.chat.id)
                    .put("message_id", message.messageId)
                    .put("username", message.from?.username)
                    .toString(),
                priority = SollNotificationPriority.HIGH,
                systemNotificationId = SollNotificationChannels.TELEGRAM_COMMAND_NOTIFICATION_ID,
            )
        )
        val systemResult = if (notification.shownAt != null) {
            "Системное уведомление показано."
        } else {
            "Сохранено в центре уведомлений, но системные уведомления не разрешены."
        }
        reply(message, "Уведомление создано. $systemResult")
    }
}
