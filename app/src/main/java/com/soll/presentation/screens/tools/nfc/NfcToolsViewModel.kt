package com.soll.presentation.screens.tools.nfc

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.nfc.NfcAccessDiagnostics
import com.soll.domain.nfc.NfcRecordSnapshot
import com.soll.domain.nfc.NfcTagSnapshot
import com.soll.domain.nfc.NfcTagTools
import com.soll.domain.nfc.NfcWritePayloadType
import com.soll.domain.nfc.NfcWriteRequest
import com.soll.domain.securitylab.DualUsePolicy
import com.soll.domain.securitylab.DualUsePolicyRequest
import com.soll.domain.securitylab.SecurityLabActivity
import com.soll.domain.securitylab.SecurityLabAudit
import com.soll.domain.securitylab.SecurityLabOwnership
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NfcToolsUiState(
    val writeMode: Boolean = false,
    val ownedLabConfirmed: Boolean = false,
    val payloadType: NfcWritePayloadType = NfcWritePayloadType.TEXT,
    val payloadInput: String = "",
    val lastTag: NfcTagUiState? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

data class NfcTagUiState(
    val uid: String,
    val technologies: List<String>,
    val accessDiagnostics: NfcAccessDiagnostics,
    val ndefType: String?,
    val maxSizeBytes: Int?,
    val isWritable: Boolean,
    val supportsNdef: Boolean,
    val supportsFormat: Boolean,
    val records: List<NfcRecordSnapshot>,
)

@HiltViewModel
class NfcToolsViewModel @Inject constructor(
    private val capabilityRegistry: CapabilityRegistry,
    private val assistantEventLogger: AssistantEventLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NfcToolsUiState())
    val uiState: StateFlow<NfcToolsUiState> = _uiState.asStateFlow()

    fun setWriteMode(enabled: Boolean) {
        _uiState.update { it.copy(writeMode = enabled, message = null, isError = false) }
    }

    fun setOwnedLabConfirmed(confirmed: Boolean) {
        _uiState.update { it.copy(ownedLabConfirmed = confirmed, message = null, isError = false) }
    }

    fun setPayloadType(type: NfcWritePayloadType) {
        _uiState.update { it.copy(payloadType = type, message = null, isError = false) }
    }

    fun updatePayload(value: String) {
        _uiState.update { it.copy(payloadInput = value, message = null, isError = false) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    fun clearTag() {
        _uiState.update { it.copy(lastTag = null, message = null, isError = false) }
    }

    fun onTagDiscovered(tag: Tag) {
        if (!ensureNfcCapability()) return
        val state = _uiState.value
        val request = state.writeRequestOrNull()
        if (request != null && !ensureOwnedLabForWrite(state)) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false) }
            runCatching {
                if (request == null) {
                    NfcTagTools.inspect(tag) to "Метка прочитана"
                } else {
                    NfcTagTools.write(tag, request) to "Метка записана"
                }
            }.onSuccess { (snapshot, message) ->
                _uiState.update {
                    it.copy(
                        lastTag = snapshot.toUiState(),
                        isBusy = false,
                        message = message,
                        isError = false,
                    )
                }
                assistantEventLogger.logEvent(
                    AssistantEvent(
                        type = if (request == null) "nfc_read" else "nfc_write",
                        source = "nfc",
                        summary = "$message: ${snapshot.uid}",
                    )
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = error.message ?: "NFC-операция не выполнена",
                        isError = true,
                    )
                }
            }
        }
    }

    private fun ensureNfcCapability(): Boolean {
        val decision = capabilityRegistry.checkCommand(NFC_CAPABILITY_ID)
        if (decision.allowed) return true
        val message = decision.message.ifBlank {
            "NFC-инструменты заблокированы политикой возможностей."
        }
        _uiState.update { it.copy(message = message, isError = true) }
        viewModelScope.launch {
            assistantEventLogger.logEvent(
                AssistantEvent(
                    type = "nfc_capability_blocked",
                    source = "nfc",
                    summary = message,
                )
            )
        }
        return false
    }

    private fun ensureOwnedLabForWrite(state: NfcToolsUiState): Boolean {
        val policyRequest = DualUsePolicyRequest(
            title = "NFC NDEF запись",
            objective = "Записать обычный текст или URL на свою NFC Forum метку.",
            activity = SecurityLabActivity.SOURCE_ANALYSIS,
            ownership = if (state.ownedLabConfirmed) {
                SecurityLabOwnership.OWNED_LAB
            } else {
                SecurityLabOwnership.UNKNOWN
            },
        )
        val decision = DualUsePolicy.review(policyRequest)
        val allowed = decision.allowed && state.ownedLabConfirmed
        val message = when {
            allowed -> null
            decision.reason.isNotBlank() -> decision.reason
            else -> "Перед записью подтвердите, что это ваша метка или разрешенный стенд."
        }

        viewModelScope.launch {
            assistantEventLogger.logEvent(SecurityLabAudit.eventFor(policyRequest, decision))
        }

        if (allowed) return true
        _uiState.update {
            it.copy(
                message = message,
                isError = true,
            )
        }
        return false
    }

    private fun NfcToolsUiState.writeRequestOrNull(): NfcWriteRequest? {
        if (!writeMode) return null
        return NfcWriteRequest(type = payloadType, payload = payloadInput)
    }

    private fun NfcTagSnapshot.toUiState(): NfcTagUiState =
        NfcTagUiState(
            uid = uid,
            technologies = technologies,
            accessDiagnostics = accessDiagnostics,
            ndefType = ndefType,
            maxSizeBytes = maxSizeBytes,
            isWritable = isWritable,
            supportsNdef = supportsNdef,
            supportsFormat = supportsFormat,
            records = records,
        )

    private companion object {
        const val NFC_CAPABILITY_ID = "nfc"
    }
}
