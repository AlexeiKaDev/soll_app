package com.soll.presentation.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.TelegramRepository
import com.soll.data.service.BotService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isRunning: Boolean = false,
    val hasToken: Boolean = false,
    val botUsername: String? = null,
    val messagesProcessed: Long = 0,
    val uptime: String = "",
    val lastError: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val telegramRepository: TelegramRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeServiceState()
        checkToken()
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            while (true) {
                val isRunning = BotService.isRunning.value
                val messagesProcessed = BotService.messagesProcessed
                val startTime = BotService.startTime
                val lastError = BotService.lastError

                val uptime = if (isRunning && startTime > 0) {
                    val uptimeMs = System.currentTimeMillis() - startTime
                    formatUptime(uptimeMs)
                } else ""

                _uiState.update { state ->
                    state.copy(
                        isRunning = isRunning,
                        messagesProcessed = messagesProcessed,
                        uptime = uptime,
                        lastError = lastError
                    )
                }
                delay(1000)
            }
        }
    }

    private fun checkToken() {
        viewModelScope.launch {
            val hasToken = settingsRepository.hasValidToken()
            _uiState.update { it.copy(hasToken = hasToken) }

            if (hasToken) {
                // Try to get bot info
                telegramRepository.getMe().onSuccess { botInfo ->
                    _uiState.update { it.copy(botUsername = "@${botInfo.username}") }
                }
            }
        }
    }

    fun startBot() {
        if (!settingsRepository.hasValidToken()) {
            _uiState.update { it.copy(lastError = "Please set bot token in Settings") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            BotService.start(application)
            delay(500)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun stopBot() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            BotService.stop(application)
            delay(500)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun formatUptime(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h ${minutes % 60}m"
            hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    fun refreshToken() {
        checkToken()
    }
}
