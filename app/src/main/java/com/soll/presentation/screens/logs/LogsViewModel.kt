package com.soll.presentation.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.entity.CommandLogEntity
import com.soll.data.local.entity.MessageLogEntity
import com.soll.data.repository.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val selectedTab: Int = 0,
    val messageLogs: List<MessageLogEntity> = emptyList(),
    val commandLogs: List<CommandLogEntity> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val telegramRepository: TelegramRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    init {
        loadLogs()
    }

    private fun loadLogs() {
        viewModelScope.launch {
            telegramRepository.getMessageLogs(100).collect { messages ->
                _uiState.update { it.copy(messageLogs = messages, isLoading = false) }
            }
        }

        viewModelScope.launch {
            telegramRepository.getCommandLogs(100).collect { commands ->
                _uiState.update { it.copy(commandLogs = commands) }
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun clearLogs() {
        viewModelScope.launch {
            telegramRepository.clearLogs()
        }
    }
}
