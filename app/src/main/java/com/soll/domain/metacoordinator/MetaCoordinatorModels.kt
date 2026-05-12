package com.soll.domain.metacoordinator

import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.CapabilityDecision
import com.soll.domain.assistant.RiskTier
import com.soll.domain.assistant.isRisky

enum class MetaPrivacyMode {
    LOCAL_ONLY,
    SAFE_SUMMARY,
}

enum class MetaSuggestedActionStatus {
    ALLOWED,
    CONFIRMATION_REQUIRED,
    BLOCKED,
}

enum class MetaDecisionStepType {
    USER_INTENT,
    CONTEXT_SUMMARY,
    CAPABILITY_FILTER,
    SERVER_RESPONSE,
    ACTION_GATE,
    FALLBACK,
}

data class MetaCoordinatorContextItem(
    val key: String,
    val value: String,
    val private: Boolean = true,
)

data class MetaAllowedCapability(
    val id: String,
    val name: String,
    val riskTier: RiskTier,
    val requiresConfirmation: Boolean,
)

data class MetaCoordinatorRequest(
    val userRequestSummary: String,
    val locale: String = "ru-RU",
    val currentScreen: String? = null,
    val privacyMode: MetaPrivacyMode = MetaPrivacyMode.SAFE_SUMMARY,
    val allowedCapabilities: List<MetaAllowedCapability>,
    val context: List<MetaCoordinatorContextItem> = emptyList(),
) {
    fun safeForServer(): MetaCoordinatorRequest =
        copy(context = context.filterNot { it.private })
}

data class MetaSuggestedAction(
    val id: String,
    val capabilityId: String,
    val title: String,
    val summary: String,
    val riskTier: RiskTier,
    val requiresConfirmation: Boolean,
    val payloadPreview: String? = null,
)

data class MetaDecisionStep(
    val type: MetaDecisionStepType,
    val summary: String,
    val createdAt: Long = System.currentTimeMillis(),
)

data class MetaCoordinatorResponse(
    val answer: String,
    val suggestedActions: List<MetaSuggestedAction> = emptyList(),
    val decisionChain: List<MetaDecisionStep> = emptyList(),
    val serverAvailable: Boolean = true,
    val fallbackReason: String? = null,
)

data class MetaSuggestedActionDecision(
    val status: MetaSuggestedActionStatus,
    val reason: String,
    val action: MetaSuggestedAction,
) {
    val allowed: Boolean = status == MetaSuggestedActionStatus.ALLOWED
    val requiresConfirmation: Boolean = status == MetaSuggestedActionStatus.CONFIRMATION_REQUIRED
}

object MetaCoordinatorActionGate {
    fun review(
        action: MetaSuggestedAction,
        capabilityDecision: CapabilityDecision,
        userConfirmed: Boolean,
    ): MetaSuggestedActionDecision {
        if (!capabilityDecision.allowed) {
            return MetaSuggestedActionDecision(
                status = MetaSuggestedActionStatus.BLOCKED,
                reason = capabilityDecision.message.ifBlank { "Capability запрещен настройками или политикой." },
                action = action,
            )
        }

        val capability = capabilityDecision.capability
        if (capability == null || capability.id != action.capabilityId) {
            return MetaSuggestedActionDecision(
                status = MetaSuggestedActionStatus.BLOCKED,
                reason = "Ответ сервера ссылается на неизвестный или несовпадающий capability.",
                action = action,
            )
        }

        val confirmationRequired =
            action.requiresConfirmation ||
                capability.requiresConfirmation ||
                action.riskTier.isRisky() ||
                capability.riskTier.isRisky()

        if (confirmationRequired && !userConfirmed) {
            return MetaSuggestedActionDecision(
                status = MetaSuggestedActionStatus.CONFIRMATION_REQUIRED,
                reason = "Перед выполнением предложенного действия нужно явное подтверждение пользователя.",
                action = action,
            )
        }

        return MetaSuggestedActionDecision(
            status = MetaSuggestedActionStatus.ALLOWED,
            reason = "Действие разрешено после проверки capability и подтверждений.",
            action = action,
        )
    }
}

object MetaCoordinatorFallback {
    fun unavailable(request: MetaCoordinatorRequest, reason: String): MetaCoordinatorResponse =
        MetaCoordinatorResponse(
            answer = "Soll сейчас недоступен. Я могу показать локальный статус, открыть существующий раздел или сохранить безопасную заметку после подтверждения.",
            suggestedActions = emptyList(),
            serverAvailable = false,
            fallbackReason = reason,
            decisionChain = listOf(
                MetaDecisionStep(
                    type = MetaDecisionStepType.USER_INTENT,
                    summary = request.userRequestSummary.take(MAX_STEP_SUMMARY_LENGTH),
                ),
                MetaDecisionStep(
                    type = MetaDecisionStepType.FALLBACK,
                    summary = "Сервер недоступен: ${reason.take(MAX_STEP_SUMMARY_LENGTH)}",
                ),
            ),
        )

    private const val MAX_STEP_SUMMARY_LENGTH = 180
}

object MetaCoordinatorServerBridge {
    fun toAssistantQuestion(request: MetaCoordinatorRequest): String {
        val safeRequest = request.safeForServer()
        return buildString {
            appendLine("Мобильный запрос Soll App:")
            appendLine(safeRequest.userRequestSummary.trim().take(MAX_TEXT_BLOCK_LENGTH))
            appendLine()
            appendLine("Локаль: ${safeRequest.locale}")
            safeRequest.currentScreen?.takeIf { it.isNotBlank() }?.let { screen ->
                appendLine("Текущий экран: ${screen.take(MAX_INLINE_LENGTH)}")
            }
            appendLine()
            appendLine("Разрешенные возможности телефона:")
            if (safeRequest.allowedCapabilities.isEmpty()) {
                appendLine("- Нет разрешенных действий; ответь без предложений к выполнению.")
            } else {
                safeRequest.allowedCapabilities.take(MAX_CAPABILITIES).forEach { capability ->
                    appendLine(
                        "- ${capability.id}: ${capability.name}; риск=${capability.riskTier}; " +
                            "подтверждение=${capability.requiresConfirmation}"
                    )
                }
            }
            if (safeRequest.context.isNotEmpty()) {
                appendLine()
                appendLine("Безопасный контекст:")
                safeRequest.context.take(MAX_CONTEXT_ITEMS).forEach { item ->
                    appendLine("- ${item.key.take(MAX_INLINE_LENGTH)}: ${item.value.take(MAX_TEXT_BLOCK_LENGTH)}")
                }
            }
            appendLine()
            appendLine(
                "Ответь кратко по-русски. Если нужны действия на телефоне, опиши их как план; " +
                    "Android выполнит что-либо только после локальной проверки capability и явного подтверждения."
            )
        }.trim()
    }

    fun fromAssistantAnswer(
        request: MetaCoordinatorRequest,
        answer: String,
        usedTopics: List<String> = emptyList(),
        confidence: String? = null,
        gaps: List<String> = emptyList(),
        contradictions: List<String> = emptyList(),
    ): MetaCoordinatorResponse {
        val safeRequest = request.safeForServer()
        val responseSummary = buildString {
            append(answer.take(MAX_TEXT_BLOCK_LENGTH))
            if (!confidence.isNullOrBlank()) append(" Уверенность: $confidence.")
            if (usedTopics.isNotEmpty()) append(" Темы: ${usedTopics.take(5).joinToString()}.")
            if (gaps.isNotEmpty()) append(" Пробелы: ${gaps.take(3).joinToString()}.")
            if (contradictions.isNotEmpty()) append(" Противоречия: ${contradictions.take(3).joinToString()}.")
        }.trim()

        return MetaCoordinatorResponse(
            answer = answer.ifBlank { "Soll вернул пустой ответ." },
            suggestedActions = emptyList(),
            serverAvailable = true,
            decisionChain = listOf(
                MetaDecisionStep(
                    type = MetaDecisionStepType.USER_INTENT,
                    summary = safeRequest.userRequestSummary.take(MAX_STEP_SUMMARY_LENGTH),
                ),
                MetaDecisionStep(
                    type = MetaDecisionStepType.CONTEXT_SUMMARY,
                    summary = "На сервер отправлено безопасных контекстных полей: ${safeRequest.context.size}.",
                ),
                MetaDecisionStep(
                    type = MetaDecisionStepType.CAPABILITY_FILTER,
                    summary = "Передан список разрешенных capability: ${safeRequest.allowedCapabilities.size}.",
                ),
                MetaDecisionStep(
                    type = MetaDecisionStepType.SERVER_RESPONSE,
                    summary = responseSummary.take(MAX_STEP_SUMMARY_LENGTH),
                ),
            ),
        )
    }

    private const val MAX_CAPABILITIES = 40
    private const val MAX_CONTEXT_ITEMS = 12
    private const val MAX_INLINE_LENGTH = 80
    private const val MAX_TEXT_BLOCK_LENGTH = 800
    private const val MAX_STEP_SUMMARY_LENGTH = 180
}

object MetaDecisionChainLog {
    fun toLocalMarkdown(response: MetaCoordinatorResponse): String {
        val builder = StringBuilder()
        builder.appendLine("# Decision chain")
        builder.appendLine()
        builder.appendLine("server_available: ${response.serverAvailable}")
        response.fallbackReason?.let { builder.appendLine("fallback_reason: $it") }
        builder.appendLine()
        response.decisionChain.forEachIndexed { index, step ->
            builder.appendLine("${index + 1}. ${step.type.name.lowercase()}: ${step.summary}")
        }
        if (response.suggestedActions.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("## Suggested actions")
            response.suggestedActions.forEach { action ->
                builder.appendLine("- ${action.capabilityId}: ${action.title} (${action.riskTier})")
            }
        }
        return builder.toString().trim()
    }
}

object MetaCoordinatorAudit {
    fun eventFor(response: MetaCoordinatorResponse): AssistantEvent =
        AssistantEvent(
            type = if (response.serverAvailable) {
                "meta_coordinator_response"
            } else {
                "meta_coordinator_fallback"
            },
            source = "meta_coordinator",
            summary = buildString {
                append("Ответ: ")
                append(response.answer.take(120))
                if (response.suggestedActions.isNotEmpty()) {
                    append("; действий: ")
                    append(response.suggestedActions.size)
                }
            },
            payloadJson = null,
        )
}
