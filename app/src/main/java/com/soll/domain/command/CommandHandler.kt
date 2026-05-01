package com.soll.domain.command

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository

/**
 * Base interface for command handlers
 */
abstract class CommandHandler(
    protected val context: Context,
    protected val telegramRepository: TelegramRepository
) {
    /**
     * Command name without slash
     */
    abstract val command: String

    /**
     * Command description for help
     */
    abstract val description: String

    /**
     * Execute the command
     */
    abstract suspend fun execute(message: Message, args: String?)

    /**
     * Send a reply to the message
     */
    protected suspend fun reply(message: Message, text: String) {
        telegramRepository.sendMessage(
            chatId = message.chat.id,
            text = text,
            replyToMessageId = message.messageId
        )
    }

    /**
     * Send a message to the chat
     */
    protected suspend fun send(chatId: Long, text: String) {
        telegramRepository.sendMessage(chatId = chatId, text = text)
    }
}
