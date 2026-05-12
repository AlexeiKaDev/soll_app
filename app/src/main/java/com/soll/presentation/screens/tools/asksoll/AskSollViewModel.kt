package com.soll.presentation.screens.tools.asksoll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.metacoordinator.MetaAllowedCapability
import com.soll.domain.metacoordinator.MetaCoordinatorAudit
import com.soll.domain.metacoordinator.MetaCoordinatorContextItem
import com.soll.domain.metacoordinator.MetaCoordinatorRequest
import com.soll.domain.metacoordinator.MetaCoordinatorResponse
import com.soll.domain.metacoordinator.MetaDecisionStep
import com.soll.domain.soll.SollGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class AskSollUiState(
    val question: String = "",
    val isLoading: Boolean = false,
    val answer: String = "",
    val serverAvailable: Boolean? = null,
    val fallbackReason: String? = null,
    val decisionChain: List<MetaDecisionStep> = emptyList(),
    val suggestedActionCount: Int = 0,
    val errorMessage: String? = null,
) {
    val canSend: Boolean
        get() = question.trim().length >= MIN_QUESTION_LENGTH && !isLoading

    companion object {
        private const val MIN_QUESTION_LENGTH = 2
    }
}

@HiltViewModel
class AskSollViewModel @Inject constructor(
    private val sollGateway: SollGateway,
    private val capabilityRegistry: CapabilityRegistry,
    private val assistantEventLogger: AssistantEventLogger,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskSollUiState())
    val uiState: StateFlow<AskSollUiState> = _uiState.asStateFlow()

    fun updateQuestion(value: String) {
        _uiState.update {
            it.copy(
                question = value.take(MAX_QUESTION_LENGTH),
                errorMessage = null,
            )
        }
    }

    fun clearAnswer() {
        _uiState.update {
            it.copy(
                answer = "",
                serverAvailable = null,
                fallbackReason = null,
                decisionChain = emptyList(),
                suggestedActionCount = 0,
                errorMessage = null,
            )
        }
    }

    fun ask() {
        val question = _uiState.value.question.trim()
        if (question.length < MIN_QUESTION_LENGTH) {
            _uiState.update { it.copy(errorMessage = "Введите вопрос для Soll.") }
            return
        }

        val decision = capabilityRegistry.checkCommand(ASK_SOLL_CAPABILITY_ID)
        if (!decision.allowed) {
            val message = decision.message.ifBlank { "Ask Soll отключен политикой возможностей." }
            _uiState.update { it.copy(errorMessage = message) }
            viewModelScope.launch {
                assistantEventLogger.logEvent(
                    AssistantEvent(
                        type = "ask_soll_blocked",
                        source = "ask_soll",
                        summary = message,
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    answer = "",
                    serverAvailable = null,
                    fallbackReason = null,
                    decisionChain = emptyList(),
                    suggestedActionCount = 0,
                    errorMessage = null,
                )
            }

            val request = MetaCoordinatorRequest(
                userRequestSummary = question,
                currentScreen = "ask_soll",
                allowedCapabilities = allowedCapabilitiesForServer(),
                context = listOf(
                    MetaCoordinatorContextItem("client", "soll_app_android", private = false),
                    MetaCoordinatorContextItem("entry_point", "tools.ask_soll", private = false),
                ),
            )

            sollGateway.askMetaCoordinator(request).fold(
                onSuccess = { response ->
                    showResponse(response)
                    assistantEventLogger.logEvent(MetaCoordinatorAudit.eventFor(response))
                },
                onFailure = { error ->
                    val message = error.message ?: "Не удалось спросить Soll."
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = message,
                        )
                    }
                    assistantEventLogger.logEvent(
                        AssistantEvent(
                            type = "ask_soll_failed",
                            source = "ask_soll",
                            summary = message.take(160),
                            payloadJson = JSONObject()
                                .put("question_length", question.length)
                                .toString(),
                        )
                    )
                },
            )
        }
    }

    private fun showResponse(response: MetaCoordinatorResponse) {
        _uiState.update {
            it.copy(
                isLoading = false,
                answer = response.answer,
                serverAvailable = response.serverAvailable,
                fallbackReason = response.fallbackReason,
                decisionChain = response.decisionChain,
                suggestedActionCount = response.suggestedActions.size,
                errorMessage = null,
            )
        }
    }

    private fun allowedCapabilitiesForServer(): List<MetaAllowedCapability> =
        capabilityRegistry.capabilities.mapNotNull { capability ->
            val decision = capabilityRegistry.checkCommand(capability.id)
            if (!decision.allowed) return@mapNotNull null
            MetaAllowedCapability(
                id = capability.id,
                name = capability.name,
                riskTier = capability.riskTier,
                requiresConfirmation = capability.requiresConfirmation,
            )
        }

    private companion object {
        const val ASK_SOLL_CAPABILITY_ID = "ask_soll"
        const val MIN_QUESTION_LENGTH = 2
        const val MAX_QUESTION_LENGTH = 4000
    }
}
