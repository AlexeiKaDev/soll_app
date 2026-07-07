package com.soll.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.SettingsRepository
import com.soll.data.voice.AndroidSpeechRecognizerAdapter
import com.soll.domain.soll.SollChatMessage
import com.soll.domain.soll.SollGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val error: String? = null,
    val actionFeedback: String? = null,
    val actionInFlightId: String? = null,
    val completedActionIds: Set<String> = emptySet(),
    val pendingActionsCount: Int = 0,
    val encrypted: Boolean = false,
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
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sollGateway: SollGateway,
    private val sttAdapter: AndroidSpeechRecognizerAdapter,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var refreshInFlight = false

    init {
        observeVoiceInput()
        refresh()
        observeServerUpdates()
    }

    fun onInputChanged(value: String) {
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

    fun refresh(showLoading: Boolean = true) {
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
                val afterId = current.messages.maxOfOrNull { it.id }
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
            val sessionId = _uiState.value.sessionId
            _uiState.update { it.copy(isSending = true, error = null, input = "") }
            sollGateway.sendChatTurn(
                content = content,
                sessionId = sessionId,
                runAssistant = false,
            ).fold(
                onSuccess = { (user, assistant) ->
                    val appended = buildList {
                        addAll(_uiState.value.messages)
                        add(user)
                        assistant?.let(::add)
                    }
                        .filter { message -> message.isDisplayableChatMessage() }
                        .distinctBy { it.id }
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            messages = appended,
                            scrollToBottomToken = it.scrollToBottomToken + 1,
                            scrollToBottomReason = ChatScrollReason.USER_SEND,
                        )
                    }
                    refresh(showLoading = false)
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
                action = action.type,
                taskId = action.taskId,
                sessionId = _uiState.value.sessionId,
            ).fold(
                onSuccess = { result ->
                    val completedIds = result.completedActionIds(action.id)
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            actionFeedback = "Готово: ${action.label}",
                            actionInFlightId = null,
                            completedActionIds = it.completedActionIds + completedIds,
                        )
                    }
                    refresh(showLoading = false)
                },
                onFailure = { error ->
                    val message = error.message ?: "Не удалось выполнить действие"
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
                        it.copy(
                            input = appendDictatedChatText(it.input, text),
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

    override fun onCleared() {
        sttAdapter.destroy()
        super.onCleared()
    }
}

private const val CHAT_REFRESH_INTERVAL_MS = 10_000L
private const val CHAT_PAGE_SIZE = 80

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

private fun Any?.asActionMapOrNull(): Map<*, *>? = this as? Map<*, *>

private fun Any?.asActionMaps(): List<Map<*, *>> =
    (this as? List<*>)
        ?.mapNotNull { item -> item as? Map<*, *> }
        .orEmpty()

private fun Map<*, *>.toChatActionUiOrNull(): ChatActionUi? {
    val action = this
    if (action.isCompletedActionMap()) return null
    val type = action["type"]?.toString().orEmpty().ifBlank {
        action["action"]?.toString().orEmpty()
    }
    if (type.isBlank()) return null
    val taskId = action["task_id"]?.toString()?.takeIf { it.isNotBlank() }
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
    )
}

private fun SollChatMessage.completedChatActionIds(): List<String> =
    listOfNotNull(
        metadata["action_result"].asActionMapOrNull()?.completedActionIdOrNull(),
        metadata["yii2_task_action"].asActionMapOrNull()?.completedActionIdOrNull(),
    )

private fun Map<*, *>.completedActionIdOrNull(): String? {
    val status = this["status"]?.toString()?.trim()?.lowercase().orEmpty()
    if (status !in COMPLETED_ACTION_STATUSES) return null
    return this["action_id"]?.toString()?.takeIf { it.isNotBlank() }
        ?: this["id"]?.toString()?.takeIf { it.isNotBlank() }
}

private fun Map<*, *>.isCompletedActionMap(): Boolean {
    val status = this["status"]?.toString()?.trim()?.lowercase().orEmpty()
    return status in COMPLETED_ACTION_STATUSES
}

private fun com.soll.domain.soll.SollChatActionResult.completedActionIds(requestedActionId: String): Set<String> {
    if (status.trim().lowercase() in FAILED_ACTION_STATUSES) return emptySet()
    return setOf(requestedActionId, actionId).filter { it.isNotBlank() }.toSet()
}

private val COMPLETED_ACTION_STATUSES = setOf("ack", "acked", "done", "completed", "executed", "success")
private val FAILED_ACTION_STATUSES = setOf("failed", "error", "rejected")

private fun String.defaultActionLabel(): String =
    when (this) {
        "task.complete", "task.done" -> "Готово"
        "task.defer" -> "Отложить"
        "task.reject" -> "Отклонить"
        "task.today" -> "Сегодня"
        "notice.ack" -> "Принято"
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
