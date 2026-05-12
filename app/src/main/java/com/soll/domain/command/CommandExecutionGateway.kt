package com.soll.domain.command

import com.soll.data.api.model.Message

interface CommandExecutionGateway {
    suspend fun sendMessage(
        chatId: Long,
        text: String,
        parseMode: String? = "HTML",
        replyToMessageId: Long? = null,
    ): Result<Message>

    suspend fun logCommand(
        command: String,
        args: String?,
        chatId: Long,
        userId: Long?,
        username: String?,
        status: String,
        errorMessage: String? = null,
        responseText: String? = null,
        executionTimeMs: Long? = null,
    )
}
