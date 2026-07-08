package com.soll.presentation.screens.todo

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.FieldMapRepository
import com.soll.domain.soll.SollDailyTask
import com.soll.domain.soll.SollGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DailyTodoUiState(
    val tasks: List<SollDailyTask> = emptyList(),
    val sourcePath: String = "",
    val isLoading: Boolean = true,
    val isAdding: Boolean = false,
    val actionTaskId: String? = null,
    val attachmentTaskId: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class DailyTodoViewModel @Inject constructor(
    private val sollGateway: SollGateway,
    private val fieldMapRepository: FieldMapRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DailyTodoUiState())
    val uiState: StateFlow<DailyTodoUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(isLoading = true, message = null, isError = false) }
            }
            sollGateway.getTodayDailyTasks().fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            tasks = list.tasks,
                            sourcePath = list.sourcePath,
                            isLoading = false,
                            message = null,
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = error.message ?: "Не удалось загрузить список дел",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun addTask(text: String, attachmentUri: Uri?, attachLocation: Boolean = true) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            _uiState.update { it.copy(message = "Введите дело", isError = true) }
            return
        }

        viewModelScope.launch {
            val existingTaskIds = _uiState.value.tasks.map { it.id }.toSet()
            _uiState.update {
                it.copy(
                    isAdding = true,
                    attachmentTaskId = null,
                    message = null,
                    isError = false,
                )
            }

            var locationWarning: String? = null
            val locationLabel = if (attachLocation) {
                runCatching { fieldMapRepository.publishCurrentLocationToSoll() }
                    .getOrElse { error ->
                        locationWarning = error.message ?: "геопозиция недоступна"
                        ""
                    }
            } else {
                ""
            }

            val createdList = sollGateway.addTodayDailyTask(cleanText, locationLabel)
                .getOrElse { error ->
                    _uiState.update {
                        it.copy(
                            isAdding = false,
                            message = error.message ?: "Не удалось добавить дело",
                            isError = true,
                        )
                    }
                    return@launch
                }

            var currentTasks = createdList.tasks
            var currentSourcePath = createdList.sourcePath
            val createdTaskId = createdList.createdTaskId
                ?: findCreatedTaskId(createdList.tasks, existingTaskIds)
            var message = "Дело добавлено"
            var isError = false

            if (attachmentUri != null) {
                if (createdTaskId.isNullOrBlank()) {
                    message = "Дело добавлено, но файл не прикреплен: не найден ID дела"
                    isError = true
                } else {
                    _uiState.update {
                        it.copy(
                            tasks = currentTasks,
                            sourcePath = currentSourcePath,
                            attachmentTaskId = createdTaskId,
                        )
                    }
                    sollGateway.uploadTodayDailyTaskAttachment(createdTaskId, attachmentUri).fold(
                        onSuccess = { attachment ->
                            message = "Дело добавлено. ${attachment.analysisStatus.dailyAttachmentMessage()}"
                            sollGateway.getTodayDailyTasks().onSuccess { refreshed ->
                                currentTasks = refreshed.tasks
                                currentSourcePath = refreshed.sourcePath
                            }
                        },
                        onFailure = { error ->
                            message = "Дело добавлено, но файл не прикреплен: ${error.message ?: "ошибка загрузки"}"
                            isError = true
                        },
                    )
                }
            }

            locationWarning?.let {
                message = "$message. Геопозиция недоступна"
            }

            _uiState.update {
                it.copy(
                    tasks = currentTasks,
                    sourcePath = currentSourcePath,
                    isAdding = false,
                    attachmentTaskId = null,
                    message = message,
                    isError = isError,
                )
            }
        }
    }

    fun setTaskDone(task: SollDailyTask, done: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionTaskId = task.id, message = null, isError = false) }
            sollGateway.updateTodayDailyTask(task.id, done).fold(
                onSuccess = { list ->
                    _uiState.update {
                        it.copy(
                            tasks = list.tasks,
                            sourcePath = list.sourcePath,
                            actionTaskId = null,
                            message = if (done) "Дело закрыто" else "Дело возвращено",
                            isError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            actionTaskId = null,
                            message = error.message ?: "Не удалось обновить дело",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun attachFile(task: SollDailyTask, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(attachmentTaskId = task.id, message = null, isError = false) }
            sollGateway.uploadTodayDailyTaskAttachment(task.id, uri).fold(
                onSuccess = { attachment ->
                    sollGateway.getTodayDailyTasks().fold(
                        onSuccess = { list ->
                            _uiState.update {
                                it.copy(
                                    tasks = list.tasks,
                                    sourcePath = list.sourcePath,
                                    attachmentTaskId = null,
                                    message = attachment.analysisStatus.dailyAttachmentMessage(),
                                    isError = false,
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    attachmentTaskId = null,
                                    message = error.message ?: "Файл прикреплен, но список не обновился",
                                    isError = true,
                                )
                            }
                        },
                    )
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            attachmentTaskId = null,
                            message = error.message ?: "Не удалось прикрепить файл",
                            isError = true,
                        )
                    }
                },
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }
}

private fun findCreatedTaskId(tasks: List<SollDailyTask>, existingTaskIds: Set<String>): String? =
    tasks.lastOrNull { it.id !in existingTaskIds }?.id ?: tasks.lastOrNull()?.id

private fun String.dailyAttachmentMessage(): String =
    when (this) {
        "parsed" -> "Файл прикреплен и разобран"
        "ocr_only" -> "Фото прикреплено, текст распознан"
        "vision_unavailable" -> "Фото прикреплено, для объекта нужна локальная vision-модель"
        "unsupported" -> "Файл прикреплен, анализ недоступен"
        else -> "Вложение прикреплено"
    }
