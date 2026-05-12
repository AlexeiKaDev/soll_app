package com.soll.domain.command

import android.content.Context
import com.soll.data.api.model.Message
import com.soll.data.repository.TelegramRepository

interface CommandExecutable {
    val command: String
    val description: String
    suspend fun execute(message: Message, args: String?)
}

/**
 * Base interface for command handlers
 */
abstract class CommandHandler(
    protected val context: Context,
    protected val telegramRepository: TelegramRepository
) : CommandExecutable {
    /**
     * Command name without slash
     */
    abstract override val command: String

    /**
     * Command description for help
     */
    abstract override val description: String

    /**
     * Execute the command
     */
    abstract override suspend fun execute(message: Message, args: String?)

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
