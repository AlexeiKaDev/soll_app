package com.soll.domain.modelchat

enum class ModelChatProviderHint {
    AUTO,
    LLAMA,
}

data class ModelChatMessage(
    val role: String,
    val content: String,
    val private: Boolean = false,
)

data class ModelChatRequest(
    val messages: List<ModelChatMessage>,
    val providerHint: ModelChatProviderHint = ModelChatProviderHint.LLAMA,
    val modelHint: String = "",
) {
    fun safeForServer(): ModelChatRequest =
        copy(
            messages = messages
                .filterNot { it.private }
                .mapNotNull { message ->
                    val role = message.role.trim().lowercase().ifBlank { "user" }
                    val content = message.content.trim()
                    if (content.isBlank()) {
                        null
                    } else {
                        message.copy(
                            role = role.take(MAX_ROLE_LENGTH),
                            content = content.take(MAX_MESSAGE_LENGTH),
                            private = false,
                        )
                    }
                }
                .take(MAX_MESSAGES),
            modelHint = modelHint.trim().take(MAX_MODEL_HINT_LENGTH),
        )

    companion object {
        const val MAX_MESSAGES = 12
        const val MAX_MESSAGE_LENGTH = 1600
        const val MAX_ROLE_LENGTH = 24
        const val MAX_MODEL_HINT_LENGTH = 80
    }
}

data class ModelChatResponse(
    val answer: String,
    val providerHint: ModelChatProviderHint,
    val modelHint: String = "",
    val serverAvailable: Boolean = true,
    val fallbackReason: String? = null,
)

object ModelChatServerBridge {
    fun toAssistantQuestion(request: ModelChatRequest): String {
        val safeRequest = request.safeForServer()
        return buildString {
            appendLine("Backend-mediated model chat request from Soll Android.")
            appendLine("Provider hint: ${safeRequest.providerHint.name.lowercase()}")
            safeRequest.modelHint.takeIf { it.isNotBlank() }?.let { model ->
                appendLine("Model hint: ${model.take(ModelChatRequest.MAX_MODEL_HINT_LENGTH)}")
            }
            appendLine("Android must not store or send MODEL_API_KEY; provider keys stay server-side.")
            appendLine()
            appendLine("Messages:")
            if (safeRequest.messages.isEmpty()) {
                appendLine("- user: empty request")
            } else {
                safeRequest.messages.forEach { message ->
                    appendLine("- ${message.role}: ${message.content}")
                }
            }
            appendLine()
            appendLine("Answer in Russian unless the user asked otherwise. Do not ask Android for API keys.")
        }.trim()
    }

    fun fromAssistantAnswer(
        request: ModelChatRequest,
        answer: String,
    ): ModelChatResponse {
        val safeRequest = request.safeForServer()
        return ModelChatResponse(
            answer = answer.ifBlank { "Soll вернул пустой ответ." },
            providerHint = safeRequest.providerHint,
            modelHint = safeRequest.modelHint,
            serverAvailable = true,
        )
    }
}

object ModelChatFallback {
    fun unavailable(request: ModelChatRequest, reason: String): ModelChatResponse {
        val safeRequest = request.safeForServer()
        return ModelChatResponse(
            answer = "Модельный чат Soll сейчас недоступен. Ключи провайдера остаются только на сервере; можно повторить позже.",
            providerHint = safeRequest.providerHint,
            modelHint = safeRequest.modelHint,
            serverAvailable = false,
            fallbackReason = reason,
        )
    }
}
