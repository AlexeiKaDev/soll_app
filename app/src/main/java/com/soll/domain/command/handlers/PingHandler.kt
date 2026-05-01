package com.soll.domain.command.handlers

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandHandler

class PingHandler(
    context: Context,
    telegramRepository: TelegramRepository
) : CommandHandler(context, telegramRepository) {

    override val command = "ping"
    override val description = "Check if bot is alive"

    override suspend fun execute(message: Message, args: String?) {
        val responseTime = System.currentTimeMillis() - (message.date * 1000)
        reply(message, "Pong! Response time: ${responseTime}ms")
    }
}
