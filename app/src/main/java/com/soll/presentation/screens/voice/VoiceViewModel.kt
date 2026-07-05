package com.soll.presentation.screens.voice

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.SettingsRepository
import com.soll.data.voice.AndroidSpeechRecognizerAdapter
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.tts.TextToSpeechManager
import com.soll.domain.voice.SttRecognitionMode
import com.soll.domain.voice.VoiceActivationPolicy
import com.soll.domain.voice.VoiceCommandSession
import com.soll.domain.voice.VoiceCommandSessionStatus
import com.soll.domain.voice.VoiceCommandRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class VoiceUiState(
    val isAvailable: Boolean = true,
    val isListening: Boolean = false,
    val isProcessing: Boolean = false,
    val partialText: String = "",
    val recognizedText: String = "",
    val responseText: String = "",
    val session: VoiceCommandSession? = null,
    val errorMessage: String? = null,
    val preferOffline: Boolean = false,
    val onDeviceSttAvailable: Boolean = false,
    val activeSttMode: SttRecognitionMode = SttRecognitionMode.SYSTEM,
    val wakePhraseRequired: Boolean = false,
    val activationHint: String? = null,
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sttAdapter: AndroidSpeechRecognizerAdapter,
    private val voiceCommandRouter: VoiceCommandRouter,
    private val ttsManager: TextToSpeechManager,
    private val assistantEventLogger: AssistantEventLogger,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val activationPolicy = VoiceActivationPolicy()
    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        observeStt()
    }

    fun startListening() {
        voicePolicyBlockReason()?.let { reason ->
            val session = VoiceCommandSession().failed(reason)
            _uiState.update {
                it.copy(
                    isListening = false,
                    isProcessing = false,
                    responseText = "",
                    session = session,
                    errorMessage = reason,
                )
            }
            logPolicyBlock(reason, session.id)
            return
        }

        _uiState.update {
            it.copy(
                errorMessage = null,
                responseText = "",
                partialText = "",
                activationHint = null,
                preferOffline = settingsRepository.voiceLocalOnly,
                wakePhraseRequired = settingsRepository.voiceWakePhraseRequired,
                session = VoiceCommandSession(),
            )
        }
        sttAdapter.startListening(
            preferOffline = settingsRepository.voiceLocalOnly,
            holdUntilStop = true,
        )
    }

    fun stopListening() {
        sttAdapter.stopListening()
    }

    fun speakResponse() {
        val response = _uiState.value.responseText.trim()
        if (response.isNotBlank()) speak(response)
    }

    private fun observeStt() {
        viewModelScope.launch {
            sttAdapter.state.collect { stt ->
                _uiState.update {
                    val nextSession = if (
                        stt.errorMessage != null &&
                        it.session?.status == VoiceCommandSessionStatus.LISTENING
                    ) {
                        it.session.failed(stt.errorMessage)
                    } else {
                        it.session
                    }

                    it.copy(
                        isAvailable = stt.isAvailable,
                        isListening = stt.isListening,
                        partialText = stt.partialText,
                        session = nextSession,
                        errorMessage = stt.errorMessage,
                        preferOffline = stt.preferOffline,
                        onDeviceSttAvailable = stt.isOnDeviceRecognitionAvailable,
                        activeSttMode = stt.activeMode,
                        wakePhraseRequired = settingsRepository.voiceWakePhraseRequired,
                    )
                }

                stt.finalText?.let { text ->
                    sttAdapter.clearFinalResult()
                    handleRecognizedText(text)
                }
            }
        }
    }

    private fun handleRecognizedText(text: String) {
        viewModelScope.launch {
            val activationDecision = activationPolicy.prepare(
                text = text,
                requireWakePhrase = settingsRepository.voiceWakePhraseRequired,
            )
            if (!activationDecision.accepted) {
                val message = activationDecision.reason ?: "Команда проигнорирована."
                assistantEventLogger.logEvent(
                    AssistantEvent(
                        type = "voice_activation_ignored",
                        source = "voice",
                        summary = message,
                        payloadJson = JSONObject()
                            .put("session_id", _uiState.value.session?.id)
                            .put("recognized_text", text)
                            .put("wake_phrase_required", settingsRepository.voiceWakePhraseRequired)
                            .toString(),
                    )
                )
                _uiState.update {
                    it.copy(
                        recognizedText = text,
                        isProcessing = false,
                        responseText = message,
                        activationHint = message,
                        session = it.session?.completed(message),
                        errorMessage = null,
                    )
                }
                return@launch
            }

            val commandText = activationDecision.commandText
            _uiState.update {
                val session = (it.session ?: VoiceCommandSession()).processing(commandText)
                it.copy(
                    recognizedText = text,
                    isProcessing = true,
                    responseText = "",
                    activationHint = null,
                    session = session,
                    errorMessage = null,
                )
            }

            val routeResult = runCatching { voiceCommandRouter.route(commandText) }
            routeResult.getOrNull()?.let { result ->
                assistantEventLogger.logEvent(
                    AssistantEvent(
                        type = "voice_command",
                        source = "voice",
                        summary = "Голосовая команда: ${commandText.take(80)}",
                        payloadJson = JSONObject()
                            .put("session_id", _uiState.value.session?.id)
                            .put("recognized_text", text)
                            .put("command_text", commandText)
                            .put("wake_phrase", activationDecision.matchedPhrase)
                            .put("stt_mode", _uiState.value.activeSttMode.name)
                            .put("response", result.detailText)
                            .toString(),
                    )
                )
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        responseText = result.detailText,
                        session = it.session?.completed(result.detailText),
                        errorMessage = null,
                    )
                }
                speak(result.spokenText)
                return@launch
            }

            routeResult.exceptionOrNull()?.let { error ->
                val message = "Не удалось выполнить голосовую команду: ${error.message ?: "ошибка"}"
                assistantEventLogger.logEvent(
                    AssistantEvent(
                        type = "voice_command_failed",
                        source = "voice",
                        summary = message.take(120),
                        payloadJson = JSONObject()
                            .put("session_id", _uiState.value.session?.id)
                            .put("recognized_text", text)
                            .put("command_text", commandText)
                            .put("error", error.message)
                            .toString(),
                    )
                )
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        responseText = "",
                        session = it.session?.failed(message),
                        errorMessage = message,
                    )
                }
            }
        }
    }

    private fun voicePolicyBlockReason(): String? {
        if (settingsRepository.voiceRequiresUnlockedDevice && isDeviceLocked()) {
            return "Голос заблокирован: устройство должно быть разблокировано."
        }
        if (settingsRepository.voiceRequiresHeadset && !isHeadsetConnected()) {
            return "Голос заблокирован: подключите гарнитуру или наушники."
        }
        return null
    }

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguardManager?.isDeviceLocked == true
    }

    private fun isHeadsetConnected(): Boolean {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return false
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            device.type in headsetOutputTypes()
        }
    }

    private fun logPolicyBlock(reason: String, sessionId: String) {
        viewModelScope.launch {
            assistantEventLogger.logEvent(
                AssistantEvent(
                    type = "voice_policy_blocked",
                    source = "voice",
                    summary = reason,
                    payloadJson = JSONObject()
                        .put("session_id", sessionId)
                        .put("requires_unlocked_device", settingsRepository.voiceRequiresUnlockedDevice)
                        .put("requires_headset", settingsRepository.voiceRequiresHeadset)
                        .put("local_only", settingsRepository.voiceLocalOnly)
                        .put("wake_phrase_required", settingsRepository.voiceWakePhraseRequired)
                        .toString(),
                )
            )
        }
    }

    private fun speak(text: String) {
        if (text.isBlank()) return
        ttsManager.initialize()
        viewModelScope.launch {
            delay(250)
            ttsManager.setLanguage(Locale.forLanguageTag("ru-RU"))
            ttsManager.speakChapter(text)
        }
    }

    override fun onCleared() {
        sttAdapter.destroy()
        super.onCleared()
    }

    private companion object {
        @SuppressLint("InlinedApi")
        fun headsetOutputTypes(): Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
        }
    }
}
