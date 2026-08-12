package com.soll.presentation.screens.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.local.entity.SyncQueueEntity
import com.soll.data.repository.SollSyncQueueRepository
import com.soll.domain.soll.SollFeedImportResult
import com.soll.presentation.navigation.SharedLinkPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ShareImportStatus { IDLE, QUEUED, SUBMITTING, SUCCESS, ERROR }

data class ShareImportUiState(
    val status: ShareImportStatus = ShareImportStatus.IDLE,
    val payload: SharedLinkPayload? = null,
    val result: SollFeedImportResult? = null,
    val message: String = "",
)

@HiltViewModel
class ShareImportViewModel @Inject constructor(
    private val syncQueueRepository: SollSyncQueueRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShareImportUiState())
    val uiState: StateFlow<ShareImportUiState> = _uiState.asStateFlow()

    private var activePayload: SharedLinkPayload? = null
    private var activeQueueId: String? = null
    private var submissionJob: Job? = null

    fun submit(payload: SharedLinkPayload) {
        val current = _uiState.value
        if (activePayload?.clientId == payload.clientId &&
            current.status in setOf(
                ShareImportStatus.QUEUED,
                ShareImportStatus.SUBMITTING,
                ShareImportStatus.SUCCESS,
            )
        ) {
            return
        }
        observeDurableSubmission(payload)
    }

    fun retry() {
        val queueId = activeQueueId ?: return
        viewModelScope.launch {
            syncQueueRepository.retryNow(queueId)
        }
    }

    private fun observeDurableSubmission(payload: SharedLinkPayload) {
        activePayload = payload
        submissionJob?.cancel()

        if (!payload.canSubmit) {
            activeQueueId = null
            _uiState.value = ShareImportUiState(
                status = ShareImportStatus.ERROR,
                payload = payload,
                message = payload.validationError ?: "Не удалось прочитать отправленную ссылку",
            )
            return
        }

        _uiState.value = ShareImportUiState(
            status = ShareImportStatus.QUEUED,
            payload = payload,
            message = "Сохраняю ссылку в надёжную очередь Soll…",
        )
        submissionJob = viewModelScope.launch {
            val queueId = try {
                syncQueueRepository.enqueueFeedImport(
                    url = payload.url,
                    title = payload.title,
                    sharedText = payload.sharedText,
                    clientId = payload.clientId,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _uiState.value = ShareImportUiState(
                    status = ShareImportStatus.ERROR,
                    payload = payload,
                    message = error.message ?: "Не удалось сохранить ссылку в очередь Soll",
                )
                return@launch
            }
            activeQueueId = queueId
            syncQueueRepository.observeItem(queueId).collectLatest { item ->
                _uiState.value = item.toShareImportUiState(payload, syncQueueRepository)
            }
        }
    }
}

internal fun SyncQueueEntity?.toShareImportUiState(
    payload: SharedLinkPayload,
    repository: SollSyncQueueRepository,
): ShareImportUiState {
    if (this == null) {
        return ShareImportUiState(
            status = ShareImportStatus.QUEUED,
            payload = payload,
            message = "Ссылка сохранена в очереди Soll",
        )
    }
    val result = repository.feedImportResult(this)
    return when (status) {
        SyncQueueEntity.STATUS_RUNNING -> ShareImportUiState(
            status = ShareImportStatus.SUBMITTING,
            payload = payload,
            message = "Soll получает материал…",
        )
        SyncQueueEntity.STATUS_DONE -> ShareImportUiState(
            status = ShareImportStatus.SUCCESS,
            payload = payload,
            result = result,
            message = when {
                result?.duplicate == true -> "Эта ссылка уже есть в Soll"
                !result?.message.isNullOrBlank() -> result?.message.orEmpty()
                else -> "Ссылка принята Soll для разбора"
            },
        )
        SyncQueueEntity.STATUS_FAILED, SyncQueueEntity.STATUS_REJECTED -> ShareImportUiState(
            status = ShareImportStatus.ERROR,
            payload = payload,
            result = result,
            message = lastError ?: "Не удалось связаться с Soll",
        )
        else -> ShareImportUiState(
            status = ShareImportStatus.QUEUED,
            payload = payload,
            message = "Ссылка сохранена и будет отправлена при доступной сети",
        )
    }
}
