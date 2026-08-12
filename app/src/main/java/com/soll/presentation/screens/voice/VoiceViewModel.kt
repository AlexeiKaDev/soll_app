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
import com.soll.domain.soll.SollGateway
import com.soll.domain.tts.TextToSpeechManager
import com.soll.domain.voice.MAX_PTT_DURATION_MS
import com.soll.domain.voice.SttRecognitionMode
import com.soll.domain.voice.VoiceActivationPolicy
import com.soll.domain.voice.VoiceAssistantTurn
import com.soll.domain.voice.VoiceCommandSession
import com.soll.domain.voice.VoiceCommandSessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
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
    val isSpeaking: Boolean = false,
    val isMuted: Boolean = false,
    val partialText: String = "",
    val recognizedText: String = "",
    val responseText: String = "",
    val session: VoiceCommandSession? = null,
    val errorMessage: String? = null,
    val permissionMessage: String? = null,
    val permissionPermanentlyDenied: Boolean = false,
    val preferOffline: Boolean = false,
    val onDeviceSttAvailable: Boolean = false,
    val activeSttMode: SttRecognitionMode = SttRecognitionMode.SYSTEM,
    val wakePhraseRequired: Boolean = false,
    val activationHint: String? = null,
    val recordingLimitReached: Boolean = false,
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sttAdapter: AndroidSpeechRecognizerAdapter,
    private val sollGateway: SollGateway,
    private val ttsManager: TextToSpeechManager,
    private val assistantEventLogger: AssistantEventLogger,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val activationPolicy = VoiceActivationPolicy()
    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()
    private var submittedSessionId: String? = null
    private var isScreenForeground: Boolean = false

    init {
        observeStt()
        observeTts()
    }

    fun startListening() {
        if (_uiState.value.isListening || _uiState.value.isProcessing) return
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

        ttsManager.stop()
        submittedSessionId = null
        _uiState.update {
            it.copy(
                errorMessage = null,
                permissionMessage = null,
                permissionPermanentlyDenied = false,
                responseText = "",
                recognizedText = "",
                partialText = "",
                activationHint = null,
                recordingLimitReached = false,
                preferOffline = settingsRepository.voiceLocalOnly,
                wakePhraseRequired = settingsRepository.voiceWakePhraseRequired,
                session = VoiceCommandSession(),
            )
        }
        sttAdapter.startListening(
            preferOffline = settingsRepository.voiceLocalOnly,
            holdUntilStop = true,
            maxDurationMillis = MAX_PTT_DURATION_MS,
        )
    }

    fun finishListening() {
        if (_uiState.value.isListening) sttAdapter.stopListening()
    }

    fun cancelListening() {
        val state = _uiState.value
        if (!state.isListening && state.session?.status != VoiceCommandSessionStatus.LISTENING) return
        submittedSessionId = state.session?.id
        sttAdapter.cancelListening()
        _uiState.update {
            it.copy(
                isListening = false,
                isProcessing = false,
                partialText = "",
                activationHint = "Запись отменена. Ничего не отправлено.",
                session = it.session?.cancelled(),
                errorMessage = null,
                recordingLimitReached = false,
            )
        }
    }

    fun onScreenStopped() {
        isScreenForeground = false
        cancelListening()
        ttsManager.stop()
    }

    fun onScreenStarted() {
        isScreenForeground = true
    }

    fun onMicrophonePermissionGranted() {
        _uiState.update {
            it.copy(
                permissionMessage = "Разрешение выдано. Удерживайте кнопку, чтобы говорить.",
                permissionPermanentlyDenied = false,
                errorMessage = null,
            )
        }
    }

    fun onMicrophonePermissionDenied(permanently: Boolean) {
        _uiState.update {
            it.copy(
                permissionMessage = if (permanently) {
                    "Микрофон запрещён. Разрешите доступ в настройках Android."
                } else {
                    "Без доступа к микрофону push-to-talk не работает."
                },
                permissionPermanentlyDenied = permanently,
                errorMessage = null,
            )
        }
    }

    fun speakResponse() {
        val response = _uiState.value.responseText.trim()
        if (response.isNotBlank() && !_uiState.value.isMuted) speak(response)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun toggleMute() {
        val muted = !_uiState.value.isMuted
        if (muted) ttsManager.stop()
        _uiState.update { it.copy(isMuted = muted) }
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
                        recordingLimitReached = stt.recordingLimitReached,
                        activationHint = if (stt.recordingLimitReached) {
                            "Достигнут лимит 30 секунд. Обрабатываю запись."
                        } else {
                            it.activationHint
                        },
                    )
                }

                stt.finalText?.let { text ->
                    sttAdapter.clearFinalResult()
                    handleRecognizedText(text)
                }
            }
        }
    }

    private fun observeTts() {
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }
    }

    private fun handleRecognizedText(text: String) {
        val session = _uiState.value.session ?: return
        if (session.status != VoiceCommandSessionStatus.LISTENING) return
        if (submittedSessionId == session.id) return
        submittedSessionId = session.id

        viewModelScope.launch {
            val activationDecision = activationPolicy.prepare(
                text = text,
                requireWakePhrase = settingsRepository.voiceWakePhraseRequired,
            )
            if (!activationDecision.accepted) {
                val message = activationDecision.reason ?: "Запрос проигнорирован."
                logVoiceEvent(
                    type = "voice_activation_ignored",
                    summary = message,
                    payload = JSONObject()
                        .put("session_id", session.id)
                        .put("wake_phrase_required", settingsRepository.voiceWakePhraseRequired),
                )
                _uiState.update {
                    it.copy(
                        recognizedText = text,
                        isProcessing = false,
                        responseText = message,
                        activationHint = message,
                        session = session.completed(message),
                        errorMessage = null,
                    )
                }
                return@launch
            }

            val turn = runCatching {
                VoiceAssistantTurn.create(
                    transcript = activationDecision.commandText,
                    requestId = session.id,
                )
            }.getOrElse { error ->
                failSession(session, text, error.message ?: "Голосовой запрос пуст")
                return@launch
            }
            _uiState.update {
                it.copy(
                    recognizedText = text,
                    isProcessing = true,
                    responseText = "",
                    activationHint = null,
                    session = session.processing(turn.content),
                    errorMessage = null,
                )
            }

            sollGateway.sendChatTurn(
                content = turn.content,
                sessionId = turn.sessionId,
                runAssistant = turn.runAssistant,
                taskIntake = turn.taskIntake,
                allowActions = turn.allowActions,
                metadata = turn.metadata,
            ).fold(
                onSuccess = { (_, assistant) ->
                    val response = assistant?.content?.trim().orEmpty().ifBlank {
                        "Soll принял запрос. Ответ появится в чате."
                    }
                    logVoiceEvent(
                        type = "voice_assistant_turn",
                        summary = "Безопасный голосовой запрос обработан",
                        payload = JSONObject()
                            .put("session_id", session.id)
                            .put("request_id", turn.requestId)
                            .put("allow_actions", turn.allowActions)
                            .put("task_intake", turn.taskIntake)
                            .put("stt_mode", _uiState.value.activeSttMode.name),
                    )
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            responseText = response,
                            session = session.processing(turn.content).completed(response),
                            errorMessage = null,
                        )
                    }
                    if (isScreenForeground && !_uiState.value.isMuted && assistant != null) {
                        speak(response)
                    }
                },
                onFailure = { error ->
                    failSession(
                        session = session,
                        recognizedText = text,
                        message = error.message ?: "Не удалось получить ответ Soll",
                    )
                },
            )
        }
    }

    private fun failSession(
        session: VoiceCommandSession,
        recognizedText: String,
        message: String,
    ) {
        logVoiceEvent(
            type = "voice_assistant_turn_failed",
            summary = message.take(120),
            payload = JSONObject().put("session_id", session.id),
        )
        _uiState.update {
            it.copy(
                recognizedText = recognizedText,
                isProcessing = false,
                responseText = "",
                session = session.failed(message),
                errorMessage = message,
            )
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
        logVoiceEvent(
            type = "voice_policy_blocked",
            summary = reason,
            payload = JSONObject()
                .put("session_id", sessionId)
                .put("requires_unlocked_device", settingsRepository.voiceRequiresUnlockedDevice)
                .put("requires_headset", settingsRepository.voiceRequiresHeadset)
                .put("local_only", settingsRepository.voiceLocalOnly),
        )
    }

    private fun logVoiceEvent(type: String, summary: String, payload: JSONObject) {
        viewModelScope.launch {
            assistantEventLogger.logEvent(
                AssistantEvent(
                    type = type,
                    source = "voice",
                    summary = summary,
                    payloadJson = payload.toString(),
                )
            )
        }
    }

    private fun speak(text: String) {
        val clean = text.trim().take(MAX_SPOKEN_RESPONSE_CHARS)
        if (clean.isNotBlank()) ttsManager.speakAssistantResponse(clean)
    }

    override fun onCleared() {
        sttAdapter.cancelListening()
        sttAdapter.destroy()
        ttsManager.stop()
        super.onCleared()
    }

    private companion object {
        private const val MAX_SPOKEN_RESPONSE_CHARS = 1_200

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
