package com.soll.presentation.screens.tools.fieldmap

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.ActivityTrackingRepository
import com.soll.data.repository.FieldMapRepository
import com.soll.data.service.ActivityTrackingService
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.activity.ActivityTrackingSummary
import com.soll.domain.field.FieldDistance
import com.soll.domain.field.FieldLocationSnapshot
import com.soll.domain.field.FieldPoint
import com.soll.domain.field.FieldPointStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FieldMapUiState(
    val points: List<FieldPoint> = emptyList(),
    val currentLocation: FieldLocationSnapshot? = null,
    val isLoadingLocation: Boolean = false,
    val isImportingTasks: Boolean = false,
    val actionPointId: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    val activitySummary: ActivityTrackingSummary = ActivityTrackingSummary(),
    val isActivityTrackerRunning: Boolean = false,
) {
    val plannedCount: Int = points.count { it.status == FieldPointStatus.PLANNED }
    val activeCount: Int = points.count { it.status == FieldPointStatus.ACTIVE }
    val doneCount: Int = points.count { it.status == FieldPointStatus.DONE }
}

@HiltViewModel
class FieldMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: FieldMapRepository,
    private val activityTrackingRepository: ActivityTrackingRepository,
    private val capabilityRegistry: CapabilityRegistry,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FieldMapUiState())
    val uiState: StateFlow<FieldMapUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(repository.observePoints(), repository.currentLocation) { points, location ->
                points.withDistance(location).sortedForField()
            }.collect { points ->
                _uiState.update {
                    it.copy(
                        points = points,
                        currentLocation = repository.currentLocation.value,
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(
                activityTrackingRepository.observeSummary(),
                ActivityTrackingService.isRunning,
            ) { summary, running -> summary to running }
                .collect { (summary, running) ->
                    _uiState.update {
                        it.copy(
                            activitySummary = summary,
                            isActivityTrackerRunning = running,
                        )
                    }
                }
        }
    }

    fun refreshLocation() {
        if (!ensureFieldMapCapability()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLocation = true, message = null, isError = false) }
            runCatching { repository.refreshCurrentLocation() }
                .onSuccess { location ->
                    _uiState.update {
                        it.copy(
                            currentLocation = location,
                            isLoadingLocation = false,
                            message = "Геолокация обновлена",
                            isError = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingLocation = false,
                            message = error.message ?: "Не удалось получить геолокацию",
                            isError = true,
                        )
                    }
                }
        }
    }

    fun saveCurrentPoint(title: String, note: String) {
        if (!ensureFieldMapCapability()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLocation = true, message = null, isError = false) }
            runCatching { repository.saveCurrentLocationPoint(title, note) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoadingLocation = false,
                            message = "Точка сохранена",
                            isError = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingLocation = false,
                            message = error.message ?: "Не удалось сохранить точку",
                            isError = true,
                        )
                    }
                }
        }
    }

    fun publishCurrentLocationToSoll() {
        if (!ensureFieldMapCapability()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingLocation = true, message = null, isError = false) }
            runCatching { repository.publishCurrentLocationToSoll() }
                .onSuccess { label ->
                    _uiState.update {
                        it.copy(
                            isLoadingLocation = false,
                            message = "Геопозиция отправлена в Soll: $label",
                            isError = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingLocation = false,
                            message = error.message ?: "Не удалось отправить геопозицию в Soll",
                            isError = true,
                        )
                    }
                }
        }
    }

    fun saveManualPoint(title: String, note: String, latitude: String, longitude: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(message = null, isError = false) }
            runCatching { repository.saveManualPoint(title, note, latitude, longitude) }
                .onSuccess {
                    _uiState.update {
                        it.copy(message = "Точка добавлена", isError = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(message = error.message ?: "Не удалось добавить точку", isError = true)
                    }
                }
        }
    }

    fun importTaskPoints() {
        if (!ensureFieldMapCapability()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingTasks = true, message = null, isError = false) }
            runCatching { repository.importTaskPoints() }
                .onSuccess { count ->
                    _uiState.update {
                        it.copy(
                            isImportingTasks = false,
                            message = if (count > 0) {
                                "Импортировано точек из задач: $count"
                            } else {
                                "В кэше задач не найдено координат"
                            },
                            isError = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isImportingTasks = false,
                            message = error.message ?: "Не удалось импортировать задачи",
                            isError = true,
                        )
                    }
                }
        }
    }

    fun setStatus(point: FieldPoint, status: FieldPointStatus) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionPointId = point.id, message = null, isError = false) }
            runCatching { repository.setStatus(point.id, status) }
                .onSuccess {
                    _uiState.update {
                        it.copy(actionPointId = null, message = "Статус обновлен", isError = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(actionPointId = null, message = error.message ?: "Не удалось обновить статус", isError = true)
                    }
                }
        }
    }

    fun deletePoint(point: FieldPoint) {
        viewModelScope.launch {
            _uiState.update { it.copy(actionPointId = point.id, message = null, isError = false) }
            runCatching { repository.deletePoint(point.id) }
                .onSuccess {
                    _uiState.update {
                        it.copy(actionPointId = null, message = "Точка удалена", isError = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(actionPointId = null, message = error.message ?: "Не удалось удалить точку", isError = true)
                    }
                }
        }
    }

    fun exportToNote(point: FieldPoint) {
        if (!ensureFieldMapCapability()) return
        viewModelScope.launch {
            _uiState.update { it.copy(actionPointId = point.id, message = null, isError = false) }
            runCatching { repository.exportPointToNote(point.id) }
                .onSuccess {
                    _uiState.update {
                        it.copy(actionPointId = null, message = "Точка отправлена в заметки", isError = false)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(actionPointId = null, message = error.message ?: "Не удалось создать заметку", isError = true)
                    }
                }
        }
    }

    fun startActivityTracking() {
        if (!ensureFieldMapCapability()) return
        activityTrackingRepository.setEnabled(true)
        val started = ActivityTrackingService.start(context)
        if (!started) {
            activityTrackingRepository.setEnabled(false)
        }
        _uiState.update {
            it.copy(
                message = if (started) "Фоновый трекер активности запущен" else "Android заблокировал старт фонового трекера",
                isError = !started,
            )
        }
    }

    fun stopActivityTracking() {
        activityTrackingRepository.setEnabled(false)
        ActivityTrackingService.stop(context)
        _uiState.update {
            it.copy(
                isActivityTrackerRunning = false,
                message = "Фоновый трекер активности остановлен",
                isError = false,
            )
        }
    }

    fun showMessage(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(message = message, isError = isError) }
    }

    fun ensureFieldMapCapability(): Boolean {
        val decision = capabilityRegistry.checkCommand(FIELD_MAP_CAPABILITY_ID)
        if (decision.allowed) return true
        val message = decision.message.ifBlank {
            "Карта и поле заблокированы политикой возможностей."
        }
        _uiState.update {
            it.copy(
                isLoadingLocation = false,
                isImportingTasks = false,
                actionPointId = null,
                message = "$message Включите: Настройки -> Возможности -> Карта и поле.",
                isError = true,
            )
        }
        return false
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    private fun List<FieldPoint>.withDistance(location: FieldLocationSnapshot?): List<FieldPoint> {
        val current = location?.coordinate ?: return this
        return map { point ->
            point.copy(distanceMeters = FieldDistance.metersBetween(current, point.coordinate))
        }
    }

    private fun List<FieldPoint>.sortedForField(): List<FieldPoint> =
        sortedWith(
            compareBy<FieldPoint> { it.status.sortRank }
                .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
                .thenByDescending { it.updatedAt }
        )

    private companion object {
        const val FIELD_MAP_CAPABILITY_ID = "field_map"
    }
}
