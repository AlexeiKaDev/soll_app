package com.soll.presentation.screens.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.TelegramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val token: String = "",
    val tokenMasked: String = "",
    val isTokenValid: Boolean = false,
    val isTokenVerified: Boolean = false,
    val botUsername: String? = null,
    val autoStartEnabled: Boolean = true,
    val isBatteryOptimizationDisabled: Boolean = false,
    val isLoading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val telegramRepository: TelegramRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val token = settingsRepository.botToken ?: ""
        val isValid = settingsRepository.validateToken(token)

        _uiState.update { state ->
            state.copy(
                token = token,
                tokenMasked = maskToken(token),
                isTokenValid = isValid,
                autoStartEnabled = settingsRepository.autoStartEnabled,
                isBatteryOptimizationDisabled = checkBatteryOptimization()
            )
        }

        if (isValid) {
            verifyToken()
        }
    }

    fun updateToken(newToken: String) {
        val isValid = settingsRepository.validateToken(newToken)
        _uiState.update { state ->
            state.copy(
                token = newToken,
                tokenMasked = maskToken(newToken),
                isTokenValid = isValid,
                isTokenVerified = false,
                botUsername = null
            )
        }
    }

    fun saveToken() {
        val token = _uiState.value.token.trim()
        if (!settingsRepository.validateToken(token)) {
            _uiState.update { state ->
                state.copy(
                    message = "Invalid token format",
                    isError = true
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            settingsRepository.botToken = token

            // Verify token with Telegram
            telegramRepository.getMe().fold(
                onSuccess = { botInfo ->
                    // Save bot config to database
                    settingsRepository.saveBotConfig(
                        name = botInfo.username,
                        token = token
                    )

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isTokenVerified = true,
                            botUsername = "@${botInfo.username}",
                            message = "Token verified! Bot: @${botInfo.username}",
                            isError = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isTokenVerified = false,
                            message = "Token verification failed: ${error.message}",
                            isError = true
                        )
                    }
                }
            )
        }
    }

    private fun verifyToken() {
        viewModelScope.launch {
            telegramRepository.getMe().onSuccess { botInfo ->
                _uiState.update { state ->
                    state.copy(
                        isTokenVerified = true,
                        botUsername = "@${botInfo.username}"
                    )
                }
            }
        }
    }

    fun setAutoStart(enabled: Boolean) {
        settingsRepository.autoStartEnabled = enabled
        _uiState.update { it.copy(autoStartEnabled = enabled) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun requestBatteryOptimizationExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${application.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        application.startActivity(intent)
    }

    fun openAutoStartSettings() {
        // Try to open auto-start settings for various OEMs
        val intents = listOf(
            // Xiaomi
            Intent().setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            // Huawei
            Intent().setClassName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            ),
            // Oppo
            Intent().setClassName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            ),
            // Vivo
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            ),
            // Samsung - open battery settings
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            // Fallback to app info
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${application.packageName}")
            }
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(application.packageManager) != null) {
                    application.startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                continue
            }
        }

        // Final fallback
        openAppSettings()
    }

    fun refreshBatteryStatus() {
        _uiState.update { it.copy(isBatteryOptimizationDisabled = checkBatteryOptimization()) }
    }

    private fun checkBatteryOptimization(): Boolean {
        val powerManager = application.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(application.packageName)
    }

    private fun maskToken(token: String): String {
        if (token.length < 10) return token
        val parts = token.split(":")
        return if (parts.size == 2) {
            "${parts[0]}:${"*".repeat(minOf(parts[1].length, 10))}..."
        } else {
            "${token.take(5)}...${"*".repeat(10)}"
        }
    }
}
