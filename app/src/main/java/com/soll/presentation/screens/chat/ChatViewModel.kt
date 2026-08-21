package com.soll.presentation.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.SettingsRepository
import com.soll.data.voice.AndroidSpeechRecognizerAdapter
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.soll.SollChatActionPolicyRegistry
import com.soll.domain.soll.SollChatMessage
import com.soll.domain.soll.SollChatTurnResult
import com.soll.domain.soll.SollGateway
import com.soll.domain.tts.AssistantVoicePlaybackPhase
import com.soll.domain.tts.AssistantVoicePlaybackState
import com.soll.domain.tts.AssistantVoicePlayer
import com.soll.domain.tts.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class ChatUiState(
    val sessionId: String = "soll-main",
    val messages: List<SollChatMessage> = emptyList(),
    val input: String = "",
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMoreHistory: Boolean = false,
    val scrollToBottomToken: Int = 0,
    val scrollToBottomReason: ChatScrollReason = ChatScrollReason.NONE,
    val isSending: Boolean = false,
    val isVoiceAvailable: Boolean = true,
    val isVoiceListening: Boolean = false,
    val voicePartialText: String = "",
    val voiceError: String? = null,
    val voiceLoadingMessageId: Long? = null,
    val voicePlayback: AssistantVoicePlaybackState = AssistantVoicePlaybackState(),
    val error: String? = null,
    val actionFeedback: String? = null,
    val actionInFlightId: String? = null,
    val completedActionIds: Set<String> = emptySet(),
    val pendingActionsCount: Int = 0,
    val encrypted: Boolean = false,
    // When set, the UI shows a text-answer dialog (e.g. task.clarify).
    val pendingTextAction: ChatActionUi? = null,
)

enum class ChatScrollReason {
    NONE,
    INITIAL_LOAD,
    SESSION_CHANGED,
    REMOTE_APPEND,
    USER_SEND,
}

data class ChatActionUi(
    val id: String,
    val type: String,
    val taskId: String?,
    val label: String,
    val prompt: String? = null,
    val completionGroupKey: String? = null,
    // task.clarify needs the owner to type an answer before executing.
    val requiresText: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sollGateway: SollGateway,
    private val sttAdapter: AndroidSpeechRecognizerAdapter,
    private val settingsRepository: SettingsRepository,
    private val capabilityRegistry: CapabilityRegistry,
    private val ttsManager: TextToSpeechManager,
    private val assistantVoicePlayer: AssistantVoicePlayer,
) : ViewModel() {
    private val turnIntentStore = ChatTurnIntentStore(savedStateHandle)
    private val restoredTurn = turnIntentStore.restore()
    private val _uiState = MutableStateFlow(
        ChatUiState(
            sessionId = restoredTurn?.sessionId ?: "soll-main",
            input = restoredTurn?.content.orEmpty(),
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var refreshInFlight = false
    private var lastSpokenMessageId = 0L
    private var voiceRequestJob: Job? = null
    private var voiceRequestGeneration = 0L
    private var voiceFallbackMessageId: Long? = null
    private var voiceFallbackText = ""
    private var voiceFallbackHandled = false
    private val pendingTurnStatusJobs = mutableMapOf<String, Job>()

    init {
        observeVoiceInput()
        observeVoicePlayback()
        refresh()
        observeServerUpdates()
    }

    fun onInputChanged(value: String) {
        turnIntentStore.invalidateIfContentChanged(value)
        _uiState.update { it.copy(input = value, error = null) }
    }

    fun onSearchChanged(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun toggleSearch() {
        _uiState.update {
            it.copy(
                isSearchOpen = !it.isSearchOpen,
                searchQuery = if (it.isSearchOpen) "" else it.searchQuery,
            )
        }
    }

    fun closeSearch() {
        _uiState.update { it.copy(isSearchOpen = false, searchQuery = "") }
    }

    fun refresh(showLoading: Boolean = true, afterIdOverride: Long? = null) {
        viewModelScope.launch {
            if (refreshInFlight) return@launch
            refreshInFlight = true
            try {
                if (showLoading) {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }
                val sync = sollGateway.getAndroidSyncStatus().getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Сервер Soll недоступен",
                        )
                    }
                    return@launch
                }
                val sessionId = sync.chat.primarySessionId.ifBlank { "soll-main" }
                val current = _uiState.value
                val afterId = afterIdOverride
                    ?: current.messages.maxOfOrNull { it.id }
                        ?.takeIf { !showLoading && current.sessionId == sessionId && current.messages.isNotEmpty() }
                val sessionMessages = sollGateway.getChatSession(
                    sessionId = sessionId,
                    limit = if (afterId == null) CHAT_PAGE_SIZE else null,
                    afterId = afterId,
                )
                val messages = sessionMessages.fold(
                    onSuccess = { fetched ->
                        chatMessagesWithSyncFallback(
                            sessionMessages = fetched,
                            syncRecentMessages = sync.chat.recentMessages,
                            sessionId = sessionId,
                            afterId = afterId,
                        )
                    },
                    onFailure = { error ->
                        val fallback = chatMessagesWithSyncFallback(
                            sessionMessages = emptyList(),
                            syncRecentMessages = sync.chat.recentMessages,
                            sessionId = sessionId,
                            afterId = afterId,
                        )
                        if (fallback.isNotEmpty() || afterId != null) {
                            fallback
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    sessionId = sessionId,
                                    pendingActionsCount = sync.chat.pendingActionsCount,
                                    encrypted = sync.chat.encryptionRequired,
                                    error = error.message ?: "Не удалось загрузить чат",
                                )
                            }
                            return@launch
                        }
                    },
                )
                val displayable = messages.filter { message -> message.isDisplayableChatMessage() }
                _uiState.update {
                    val merged = if (afterId == null) {
                        displayable
                    } else {
                        mergeChatMessages(it.messages, displayable)
                    }
                    val scrollReason = chatScrollReasonForRefresh(
                        previousMessages = it.messages,
                        nextMessages = merged,
                        previousSessionId = it.sessionId,
                        nextSessionId = sessionId,
                        fetchedAfterId = afterId,
                        fetchedMessages = displayable,
                    )
                    it.copy(
                        isLoading = false,
                        sessionId = sessionId,
                        messages = merged,
                        hasMoreHistory = if (afterId == null) displayable.size >= CHAT_PAGE_SIZE else it.hasMoreHistory,
                        pendingActionsCount = sync.chat.pendingActionsCount,
                        encrypted = sync.chat.encryptionRequired,
                        error = null,
                        scrollToBottomToken = if (scrollReason != ChatScrollReason.NONE) {
                            it.scrollToBottomToken + 1
                        } else {
                            it.scrollToBottomToken
                        },
                        scrollToBottomReason = scrollReason,
                    )
                }
                speakLatestAssistantMessage(messages = displayable, afterId = afterId)
            } finally {
                refreshInFlight = false
            }
        }
    }

    fun loadOlderMessages() {
        val state = _uiState.value
        if (state.isLoadingOlder || state.isLoading || !state.hasMoreHistory || state.searchQuery.isNotBlank()) return
        val oldestId = state.messages.minOfOrNull { it.id } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlder = true, error = null) }
            sollGateway.getChatSession(
                sessionId = state.sessionId,
                limit = CHAT_PAGE_SIZE,
                beforeId = oldestId,
            ).fold(
                onSuccess = { messages ->
                    val displayable = messages.filter { message -> message.isDisplayableChatMessage() }
                    _uiState.update {
                        it.copy(
                            isLoadingOlder = false,
                            messages = mergeChatMessages(displayable, it.messages),
                            hasMoreHistory = displayable.size >= CHAT_PAGE_SIZE,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingOlder = false,
                            error = error.message ?: "Не удалось загрузить историю",
                        )
                    }
                },
            )
        }
    }

    fun send() {
        val content = _uiState.value.input.trim()
        if (content.isBlank()) return
        viewModelScope.launch {
            val restoredIntent = turnIntentStore.restore()?.takeIf { it.content == content }
            val sessionId = restoredIntent?.sessionId ?: _uiState.value.sessionId
            val pendingTurn = turnIntentStore.resolve(content, sessionId)
            val previousLastId = _uiState.value.messages.maxOfOrNull { it.id }
            _uiState.update {
                it.copy(
                    isSending = true,
                    error = null,
                    actionFeedback = null,
                    input = "",
                )
            }
            sollGateway.sendChatTurn(
                content = content,
                sessionId = sessionId,
                runAssistant = true,
                taskIntake = false,
                allowActions = false,
                metadata = mapOf(
                    "client_turn_id" to pendingTurn.clientTurnId,
                    "request_id" to pendingTurn.clientTurnId,
                ),
                encryptionNonceSeed = pendingTurn.encryptionNonceSeed,
            ).fold(
                onSuccess = { result ->
                    turnIntentStore.complete(pendingTurn.clientTurnId)
                    val assistant = result.immediateAssistantForChat()
                    val appended = mergeChatMessages(
                        _uiState.value.messages,
                        result.immediateMessagesForChat(),
                    )
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            messages = appended,
                            input = if (result.isFailed) content else it.input,
                            error = result.failureMessageForChat(),
                            actionFeedback = when {
                                result.isQueued -> "Запрос принят. Жду ответ Soll Core…"
                                result.isFailed -> "Ошибка Soll Core: ${result.failureMessageForChat()}"
                                else -> null
                            },
                            scrollToBottomToken = it.scrollToBottomToken + 1,
                            scrollToBottomReason = ChatScrollReason.USER_SEND,
                        )
                    }
                    assistant?.let(::speakAutomaticallyIfNew)
                    if (result.isQueued && result.turnId.isNotBlank()) {
                        observeQueuedChatTurn(result.turnId, content, previousLastId)
                    } else {
                        refresh(showLoading = false, afterIdOverride = previousLastId)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            input = content,
                            error = error.message ?: "Не удалось отправить сообщение",
                        )
                    }
                },
            )
        }
    }

    private fun observeQueuedChatTurn(turnId: String, content: String, previousLastId: Long?) {
        pendingTurnStatusJobs.remove(turnId)?.cancel()
        pendingTurnStatusJobs[turnId] = viewModelScope.launch {
            try {
                repeat(CHAT_TURN_STATUS_POLL_ATTEMPTS) {
                    delay(CHAT_TURN_STATUS_POLL_INTERVAL_MS)
                    val statusResult = sollGateway.getChatTurnStatus(turnId)
                    val error = statusResult.exceptionOrNull()
                    if (error is HttpException && error.code() == 404) {
                        _uiState.update {
                            it.copy(
                                actionFeedback = chatTurnTimeoutMessage(),
                                error = null,
                            )
                        }
                        refresh(showLoading = false, afterIdOverride = previousLastId)
                        return@launch
                    }
                    val result = statusResult.getOrNull() ?: return@repeat
                    if (result.isFailed) {
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                input = content,
                                actionFeedback = "Ошибка Soll Core: ${result.failureMessageForChat()}",
                                error = result.failureMessageForChat(),
                            )
                        }
                        return@launch
                    }
                    val assistant = result.immediateAssistantForChat()
                    if (assistant != null) {
                        _uiState.update {
                            it.copy(
                                messages = mergeChatMessages(it.messages, listOf(assistant)),
                                actionFeedback = null,
                                error = null,
                                scrollToBottomToken = it.scrollToBottomToken + 1,
                                scrollToBottomReason = ChatScrollReason.REMOTE_APPEND,
                            )
                        }
                        speakAutomaticallyIfNew(assistant)
                        refresh(showLoading = false, afterIdOverride = previousLastId)
                        return@launch
                    }
                }
                _uiState.update {
                    it.copy(
                        actionFeedback = chatTurnTimeoutMessage(),
                        error = null,
                    )
                }
            } finally {
                pendingTurnStatusJobs.remove(turnId)
            }
        }
    }

    fun startVoiceInput() {
        if (!_uiState.value.isVoiceAvailable) {
            _uiState.update { it.copy(voiceError = "Распознавание речи недоступно на этом устройстве") }
            return
        }
        _uiState.update { it.copy(voiceError = null, voicePartialText = "") }
        sttAdapter.startListening(
            preferOffline = settingsRepository.voiceLocalOnly,
            holdUntilStop = true,
        )
    }

    fun stopVoiceInput() {
        sttAdapter.stopListening()
    }

    fun onVoicePermissionDenied() {
        _uiState.update { it.copy(voiceError = "Нет разрешения на микрофон") }
    }

    fun dismissVoiceError() {
        _uiState.update { it.copy(voiceError = null) }
    }

    fun speakMessage(message: SollChatMessage) {
        if (message.isFromUser) return
        val spoken = assistantSpeechText(message.content)
        if (spoken.isBlank()) return
        val current = _uiState.value
        val playbackIsActive = current.voicePlayback.messageId == message.id &&
            current.voicePlayback.phase in setOf(
                AssistantVoicePlaybackPhase.PREPARING,
                AssistantVoicePlaybackPhase.PLAYING,
            )
        if (current.voiceLoadingMessageId == message.id || playbackIsActive) {
            stopVoiceOutput()
            return
        }
        lastSpokenMessageId = maxOf(lastSpokenMessageId, message.id)
        stopVoiceOutput()
        val generation = ++voiceRequestGeneration
        voiceFallbackMessageId = message.id
        voiceFallbackText = spoken
        voiceFallbackHandled = false
        _uiState.update { it.copy(voiceLoadingMessageId = message.id) }
        voiceRequestJob = viewModelScope.launch {
            val audioResult = sollGateway.synthesizeVoice(spoken)
            if (generation != voiceRequestGeneration) return@launch
            _uiState.update { it.copy(voiceLoadingMessageId = null) }
            val audio = audioResult.getOrElse {
                fallbackToSystemVoice(message.id, spoken)
                return@launch
            }
            if (!assistantVoicePlayer.play(message.id, audio)) {
                fallbackToSystemVoice(message.id, spoken)
            }
        }
    }

    private fun stopVoiceOutput() {
        voiceRequestGeneration += 1
        voiceRequestJob?.cancel()
        voiceRequestJob = null
        voiceFallbackMessageId = null
        voiceFallbackText = ""
        voiceFallbackHandled = false
        assistantVoicePlayer.stop()
        ttsManager.stop()
        _uiState.update { it.copy(voiceLoadingMessageId = null) }
    }

    fun onScrollRequestHandled(token: Int) {
        _uiState.update {
            if (it.scrollToBottomToken == token) {
                it.copy(scrollToBottomReason = ChatScrollReason.NONE)
            } else {
                it
            }
        }
    }

    fun executeAction(action: ChatActionUi) {
        // Actions that need a typed answer (task.clarify) open a dialog first.
        if (action.requiresText) {
            _uiState.update { it.copy(pendingTextAction = action, error = null) }
            return
        }
        runChatAction(action, note = "")
    }

    /** Submit the typed answer for a pending text action (e.g. task.clarify). */
    fun submitPendingTextAction(text: String) {
        val action = _uiState.value.pendingTextAction ?: return
        val answer = text.trim()
        if (answer.isBlank()) {
            _uiState.update { it.copy(error = "Введите ответ на вопросы.") }
            return
        }
        _uiState.update { it.copy(pendingTextAction = null) }
        runChatAction(action, note = answer)
    }

    fun dismissPendingTextAction() {
        _uiState.update { it.copy(pendingTextAction = null) }
    }

    private fun runChatAction(action: ChatActionUi, note: String) {
        val policy = SollChatActionPolicyRegistry.resolve(action.type)
        val capabilityDecision = policy?.let { capabilityRegistry.checkCommand(it.capabilityId) }
        if (policy == null || capabilityDecision?.allowed != true) {
            val reason = capabilityDecision?.message
                ?.takeIf { it.isNotBlank() }
                ?: "Действие не разрешено локальной политикой Android."
            _uiState.update {
                it.copy(
                    isSending = false,
                    actionFeedback = "Действие заблокировано",
                    actionInFlightId = null,
                    error = reason,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    error = null,
                    actionFeedback = "Выполняю: ${action.label}",
                    actionInFlightId = action.id,
                )
            }
            sollGateway.executeChatAction(
                actionId = action.id,
                action = policy.type,
                taskId = action.taskId,
                sessionId = _uiState.value.sessionId,
                note = note,
            ).fold(
                onSuccess = { result ->
                    val completedIds = result.completedActionIds(
                        requestedActionId = action.id,
                        requestedTaskId = action.taskId,
                    )
                    val actionAccepted = result.isAcceptedPendingAction()
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            actionFeedback = if (actionAccepted) {
                                "Принято, ожидает Soll Core: ${action.label}"
                            } else {
                                "Готово: ${action.label}"
                            },
                            actionInFlightId = null,
                            completedActionIds = it.completedActionIds + completedIds,
                        )
                    }
                    refresh(showLoading = false)
                },
                onFailure = { error ->
                    if (shouldUseClarificationChatFallback(action, note, error)) {
                        submitClarificationChatFallback(action, note)
                    } else {
                        val message = error.message ?: "Не удалось выполнить действие"
                        _uiState.update {
                            it.copy(
                                isSending = false,
                                actionFeedback = "Ошибка: ${action.label}",
                                actionInFlightId = null,
                                error = message,
                            )
                        }
                    }
                },
            )
        }
    }

    private suspend fun submitClarificationChatFallback(action: ChatActionUi, note: String) {
        val taskId = action.taskId.orEmpty().trim()
        val fallbackTurnId = "clarify-fallback:${action.id}".take(128)
        sollGateway.sendChatTurn(
            content = clarificationFallbackMessage(taskId, note),
            sessionId = _uiState.value.sessionId,
            runAssistant = true,
            taskIntake = false,
            allowActions = false,
            metadata = mapOf(
                "client_turn_id" to fallbackTurnId,
                "request_id" to fallbackTurnId,
                "task_id" to taskId,
                "clarification_action_id" to action.id,
            ),
        ).fold(
            onSuccess = { result ->
                val failure = result.failureMessageForChat()
                val accepted = !result.isFailed
                val completedIds = if (accepted) {
                    listOfNotNull(
                        action.id.takeIf { it.isNotBlank() },
                        action.completionGroupKey?.takeIf { it.isNotBlank() },
                    ).toSet()
                } else {
                    emptySet()
                }
                _uiState.update {
                    it.copy(
                        isSending = false,
                        actionFeedback = when {
                            !accepted -> "Ошибка Soll Core: $failure"
                            result.isQueued -> "Ответ принят. Жду Soll Core…"
                            else -> "Готово: ${action.label}"
                        },
                        actionInFlightId = null,
                        completedActionIds = it.completedActionIds + completedIds,
                        error = failure.takeIf { !accepted },
                    )
                }
                if (accepted) {
                    refresh(showLoading = false)
                }
            },
            onFailure = { fallbackError ->
                val message = fallbackError.message ?: "Не удалось отправить ответ через чат Soll"
                _uiState.update {
                    it.copy(
                        isSending = false,
                        actionFeedback = "Ошибка: ${action.label}",
                        actionInFlightId = null,
                        error = message,
                    )
                }
            },
        )
    }

    private fun observeVoiceInput() {
        viewModelScope.launch {
            sttAdapter.state.collect { stt ->
                _uiState.update {
                    it.copy(
                        isVoiceAvailable = stt.isAvailable,
                        isVoiceListening = stt.isListening,
                        voicePartialText = stt.partialText,
                        voiceError = stt.errorMessage,
                    )
                }

                stt.finalText?.let { text ->
                    sttAdapter.clearFinalResult()
                    _uiState.update {
                        val updatedInput = appendDictatedChatText(it.input, text)
                        turnIntentStore.invalidateIfContentChanged(updatedInput)
                        it.copy(
                            input = updatedInput,
                            voicePartialText = "",
                            voiceError = null,
                        )
                    }
                }
            }
        }
    }

    private fun observeServerUpdates() {
        viewModelScope.launch {
            while (isActive) {
                delay(CHAT_REFRESH_INTERVAL_MS)
                if (!_uiState.value.isSending && !_uiState.value.isLoading) {
                    refresh(showLoading = false)
                }
            }
        }
    }

    private fun observeVoicePlayback() {
        viewModelScope.launch {
            assistantVoicePlayer.state.collect { playback ->
                _uiState.update { it.copy(voicePlayback = playback) }
                when (playback.phase) {
                    AssistantVoicePlaybackPhase.ERROR -> {
                        val messageId = playback.messageId
                        if (messageId != null && messageId == voiceFallbackMessageId) {
                            fallbackToSystemVoice(messageId, voiceFallbackText)
                        }
                    }
                    AssistantVoicePlaybackPhase.IDLE -> {
                        if (_uiState.value.voiceLoadingMessageId == null) {
                            voiceFallbackMessageId = null
                            voiceFallbackText = ""
                            voiceFallbackHandled = false
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun fallbackToSystemVoice(messageId: Long, text: String) {
        if (voiceFallbackHandled || voiceFallbackMessageId != messageId || text.isBlank()) return
        voiceFallbackHandled = true
        ttsManager.speakAssistantResponse(text)
    }

    private fun speakAutomaticallyIfNew(message: SollChatMessage) {
        if (
            shouldAutomaticallySpeakChatMessage(
                enabled = settingsRepository.voiceChatResponsesEnabled,
                messageId = message.id,
                lastSpokenMessageId = lastSpokenMessageId,
            )
        ) {
            speakMessage(message)
        }
    }

    private fun speakLatestAssistantMessage(messages: List<SollChatMessage>, afterId: Long?) {
        if (!settingsRepository.voiceChatResponsesEnabled || afterId == null) return
        messages.asSequence()
            .filterNot { it.isFromUser }
            .filter { it.id > afterId && it.id > lastSpokenMessageId }
            .filter { it.requestsVoicePlayback() }
            .maxByOrNull { it.id }
            ?.let(::speakMessage)
    }

    override fun onCleared() {
        voiceRequestGeneration += 1
        voiceRequestJob?.cancel()
        pendingTurnStatusJobs.values.forEach(Job::cancel)
        pendingTurnStatusJobs.clear()
        voiceFallbackMessageId = null
        voiceFallbackText = ""
        assistantVoicePlayer.stop()
        sttAdapter.destroy()
        ttsManager.stop()
        super.onCleared()
    }
}

private const val CHAT_REFRESH_INTERVAL_MS = 10_000L
private const val CHAT_PAGE_SIZE = 80
internal const val CHAT_TURN_STATUS_POLL_INTERVAL_MS = 2_000L
internal const val CHAT_TURN_STATUS_POLL_ATTEMPTS = 90

internal fun chatTurnTimeoutMessage(): String =
    "Ответ всё ещё обрабатывается и появится в чате позже."

internal fun clarificationFallbackMessage(taskId: String, note: String): String {
    val cleanTaskId = taskId.trim()
    val cleanNote = note.trim()
    require(cleanTaskId.isNotBlank()) { "ID задачи не задан" }
    require(cleanNote.isNotBlank()) { "Ответ на уточнение пуст" }
    return "#${cleanTaskId.take(6)} $cleanNote"
}

private fun shouldUseClarificationChatFallback(
    action: ChatActionUi,
    note: String,
    error: Throwable,
): Boolean =
    action.type == "task.clarify" &&
        !action.taskId.isNullOrBlank() &&
        note.isNotBlank() &&
        error is HttpException &&
        error.code() == 400

internal fun shouldAutomaticallySpeakChatMessage(
    enabled: Boolean,
    messageId: Long,
    lastSpokenMessageId: Long,
): Boolean = enabled && messageId > lastSpokenMessageId

internal fun SollChatTurnResult.immediateAssistantForChat(): SollChatMessage? =
    assistant
        ?.takeIf { permitsAssistantPayload() }
        ?.takeIf { it.content.isNotBlank() && it.isDisplayableChatMessage() }

internal fun SollChatTurnResult.immediateMessagesForChat(): List<SollChatMessage> =
    listOfNotNull(
        message.takeIf { it.content.isNotBlank() && it.isDisplayableChatMessage() },
        immediateAssistantForChat(),
    )

internal fun SollChatTurnResult.failureMessageForChat(): String? {
    if (!isFailed) return null
    return error?.message?.trim()?.takeIf { it.isNotBlank() }
        ?: "Soll Core не смог обработать запрос."
}

internal fun mergeChatMessages(left: List<SollChatMessage>, right: List<SollChatMessage>): List<SollChatMessage> =
    (left + right)
        .associateBy { it.id }
        .values
        .sortedBy { it.id }

internal fun chatMessagesWithSyncFallback(
    sessionMessages: List<SollChatMessage>,
    syncRecentMessages: List<SollChatMessage>,
    sessionId: String,
    afterId: Long?,
): List<SollChatMessage> {
    if (sessionMessages.isNotEmpty()) return sessionMessages
    val targetSession = sessionId.ifBlank { "soll-main" }
    return syncRecentMessages
        .asSequence()
        .filter { message -> message.sessionId.ifBlank { "soll-main" } == targetSession }
        .filter { message -> afterId == null || message.id > afterId }
        .sortedBy { it.id }
        .toList()
}

internal fun shouldAdvanceChatScroll(
    previousMessages: List<SollChatMessage>,
    nextMessages: List<SollChatMessage>,
    previousSessionId: String,
    nextSessionId: String,
    fetchedAfterId: Long?,
    fetchedMessages: List<SollChatMessage>,
): Boolean {
    return chatScrollReasonForRefresh(
        previousMessages = previousMessages,
        nextMessages = nextMessages,
        previousSessionId = previousSessionId,
        nextSessionId = nextSessionId,
        fetchedAfterId = fetchedAfterId,
        fetchedMessages = fetchedMessages,
    ) != ChatScrollReason.NONE
}

internal fun chatScrollReasonForRefresh(
    previousMessages: List<SollChatMessage>,
    nextMessages: List<SollChatMessage>,
    previousSessionId: String,
    nextSessionId: String,
    fetchedAfterId: Long?,
    fetchedMessages: List<SollChatMessage>,
): ChatScrollReason {
    if (nextMessages.isEmpty()) return ChatScrollReason.NONE
    if (previousMessages.isEmpty()) return ChatScrollReason.INITIAL_LOAD
    if (previousSessionId != nextSessionId) return ChatScrollReason.SESSION_CHANGED
    if (fetchedAfterId != null) {
        return if (fetchedMessages.isNotEmpty()) {
            ChatScrollReason.REMOTE_APPEND
        } else {
            ChatScrollReason.NONE
        }
    }

    val previousLastId = previousMessages.maxOfOrNull { it.id } ?: return ChatScrollReason.INITIAL_LOAD
    val nextLastId = nextMessages.maxOfOrNull { it.id } ?: return ChatScrollReason.NONE
    return if (nextLastId > previousLastId) {
        ChatScrollReason.REMOTE_APPEND
    } else {
        ChatScrollReason.NONE
    }
}

internal fun appendDictatedChatText(current: String, recognized: String): String {
    val clean = recognized.trim().replace(Regex("\\s+"), " ")
    if (clean.isBlank()) return current

    val base = current.trimEnd()
    return if (base.isBlank()) clean else "$base $clean"
}

internal fun assistantSpeechText(content: String, maxChars: Int = 1_200): String {
    val clean = content
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("!\\[[^]]*]\\([^)]+\\)"), " ")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("[`#>*_~-]+"), " ")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+([,.!?;:])"), "$1")
        .trim()
    if (clean.length <= maxChars) return clean
    val prefix = clean.take(maxChars)
    return prefix.substringBeforeLast(' ', prefix).trimEnd() + "."
}

internal fun SollChatMessage.requestsVoicePlayback(): Boolean {
    val direct = metadata["send_voice"]
    val nested = (metadata["extra"] as? Map<*, *>)?.get("send_voice")
    return direct.asBooleanFlag() || nested.asBooleanFlag()
}

private fun Any?.asBooleanFlag(): Boolean =
    when (this) {
        is Boolean -> this
        is Number -> toInt() != 0
        is String -> trim().lowercase() in setOf("1", "true", "yes", "on")
        else -> false
    }

fun SollChatMessage.actionUis(): List<ChatActionUi> =
    buildList {
        metadata["action"].asActionMapOrNull()?.let(::add)
        metadata["actions"].asActionMaps().forEach(::add)
        val taskIntake = metadata["task_intake"] as? Map<*, *>
        taskIntake?.get("action").asActionMapOrNull()?.let(::add)
        taskIntake?.get("actions").asActionMaps().forEach(::add)
    }
        .mapNotNull { action -> action.toChatActionUiOrNull() }
        .distinctBy { it.id }

fun SollChatMessage.actionUiOrNull(): ChatActionUi? = actionUis().firstOrNull()

fun completedChatActionIds(messages: List<SollChatMessage>): Set<String> =
    messages.flatMap { it.completedChatActionIds() }.toSet()

fun ChatActionUi.isCompletedBy(completedActionIds: Set<String>): Boolean =
    id in completedActionIds || completionGroupKey?.let { it in completedActionIds } == true

private fun Any?.asActionMapOrNull(): Map<*, *>? = this as? Map<*, *>

private fun Any?.asActionMaps(): List<Map<*, *>> =
    (this as? List<*>)
        ?.mapNotNull { item -> item as? Map<*, *> }
        .orEmpty()

private fun Map<*, *>.toChatActionUiOrNull(): ChatActionUi? {
    val action = this
    if (action.isCompletedActionMap()) return null
    val rawType = action["type"]?.toString().orEmpty().ifBlank {
        action["action"]?.toString().orEmpty()
    }
    val type = SollChatActionPolicyRegistry.resolve(rawType)?.type ?: return null
    val taskId = action["task_id"]?.toString()?.takeIf { it.isNotBlank() }
    val approvalId = action["approval_id"]?.toString()?.takeIf { it.isNotBlank() }
    val id = action["id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: if (type.startsWith("task.") && taskId != null) {
            "task:$taskId:${type.substringAfter('.')}"
        } else {
            return null
        }
    return ChatActionUi(
        id = id,
        type = type,
        taskId = taskId,
        label = action["label"]?.toString()?.takeIf { it.isNotBlank() } ?: type.defaultActionLabel(),
        prompt = action["prompt"]?.toString()?.takeIf { it.isNotBlank() },
        completionGroupKey = taskActionGroupKey(taskId) ?: approvalActionGroupKey(approvalId),
        requiresText = type == "task.clarify",
    )
}

private fun SollChatMessage.completedChatActionIds(): List<String> =
    listOf(
        metadata["action_result"].asActionMapOrNull()?.completedActionIds().orEmpty(),
        metadata["yii2_task_action"].asActionMapOrNull()?.completedActionIds().orEmpty(),
        metadata["extra"].asActionMapOrNull()?.completedActionIds().orEmpty(),
    ).flatten()

private fun Map<*, *>.completedActionIds(): List<String> {
    val status = this["status"]?.toString()?.trim()?.lowercase().orEmpty()
    if (status !in COMPLETED_ACTION_STATUSES) return emptyList()
    val actionId = this["action_id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: this["id"]?.toString()?.takeIf { it.isNotBlank() }
    val taskId = this["task_id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: actionId?.taskIdFromActionId()
    val approvalId = this["approval_id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: actionId?.approvalIdFromActionId()
    return listOfNotNull(actionId, taskActionGroupKey(taskId), approvalActionGroupKey(approvalId))
}

private fun Map<*, *>.isCompletedActionMap(): Boolean {
    val status = this["status"]?.toString()?.trim()?.lowercase().orEmpty()
    return status in COMPLETED_ACTION_STATUSES
}

internal fun com.soll.domain.soll.SollChatActionResult.completedActionIds(
    requestedActionId: String,
    requestedTaskId: String?,
): Set<String> {
    if (status.trim().lowercase() !in COMPLETED_ACTION_STATUSES) return emptySet()
    return listOfNotNull(
        requestedActionId.takeIf { it.isNotBlank() },
        actionId.takeIf { it.isNotBlank() },
        taskActionGroupKey(taskId ?: requestedTaskId ?: requestedActionId.taskIdFromActionId()),
        approvalActionGroupKey(actionId.approvalIdFromActionId() ?: requestedActionId.approvalIdFromActionId()),
    ).toSet()
}

internal fun com.soll.domain.soll.SollChatActionResult.isAcceptedPendingAction(): Boolean =
    status.trim().lowercase() in PENDING_ACTION_STATUSES

private fun taskActionGroupKey(taskId: String?): String? =
    taskId?.takeIf { it.isNotBlank() }?.let { "task:$it:*" }

private fun approvalActionGroupKey(approvalId: String?): String? =
    approvalId?.takeIf { it.isNotBlank() }?.let { "approval:$it:*" }

private fun String.taskIdFromActionId(): String? {
    if (!startsWith("task:")) return null
    val taskId = removePrefix("task:").substringBefore(":", missingDelimiterValue = "")
    return taskId.takeIf { it.isNotBlank() }
}

private fun String.approvalIdFromActionId(): String? {
    if (!startsWith("approval:")) return null
    val approvalId = removePrefix("approval:").substringBefore(":", missingDelimiterValue = "")
    return approvalId.takeIf { it.isNotBlank() }
}

private val COMPLETED_ACTION_STATUSES = setOf(
    "ack", "acked", "done", "completed", "executed", "success",
    "approved", "rejected", "answered",
)
private val PENDING_ACTION_STATUSES = setOf("accepted", "pending", "queued")

private fun String.defaultActionLabel(): String =
    when (this) {
        "task.complete", "task.done" -> "Готово"
        "task.defer" -> "Отложить"
        "task.reject" -> "Отклонить"
        "task.today" -> "Сегодня"
        "notice.ack" -> "Принято"
        "approval.approve" -> "Подтвердить"
        "approval.reject" -> "Отклонить"
        else -> "Выполнить"
    }

internal fun SollChatMessage.matchesChatQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isBlank()) return true

    return content.contains(needle, ignoreCase = true) ||
        role.contains(needle, ignoreCase = true) ||
        createdAt.contains(needle, ignoreCase = true) ||
        metadata.matchesChatMetadataQuery(needle)
}

internal fun SollChatMessage.isDisplayableChatMessage(): Boolean =
    !content.looksLikePlaceholderNoise() && !isServerStubMessage()

private fun SollChatMessage.isServerStubMessage(): Boolean =
    metadata["source"]?.toString() == "yii2_soll_api" &&
        metadata["assistant"]?.toString() == "stub"

private fun String.looksLikePlaceholderNoise(): Boolean {
    val compact = filterNot { it.isWhitespace() }
    if (compact.length < 80) return false

    val mostCommon = compact
        .groupingBy { it }
        .eachCount()
        .values
        .maxOrNull() ?: return false

    return mostCommon.toDouble() / compact.length >= 0.98
}

private fun Map<*, *>.matchesChatMetadataQuery(query: String): Boolean =
    entries.any { (key, value) ->
        key?.toString()?.contains(query, ignoreCase = true) == true ||
            value.matchesChatMetadataQuery(query)
    }

private fun Any?.matchesChatMetadataQuery(query: String): Boolean =
    when (this) {
        null -> false
        is Map<*, *> -> matchesChatMetadataQuery(query)
        is Iterable<*> -> any { item -> item.matchesChatMetadataQuery(query) }
        is Array<*> -> any { item -> item.matchesChatMetadataQuery(query) }
        else -> toString().contains(query, ignoreCase = true)
    }
