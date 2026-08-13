package com.soll.presentation.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.calendar.AndroidCalendarReader
import com.soll.data.repository.DailyIntelligenceRepository
import com.soll.data.repository.SollSyncQueueRepository
import com.soll.domain.soll.SollFeedItem
import com.soll.domain.soll.SollTodaySnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class TodayUiState(
    val snapshot: SollTodaySnapshot? = null,
    val feed: List<SollFeedItem> = emptyList(),
    val nextCursor: String = "",
    val feedHasMore: Boolean = false,
    val selectedTab: TodayTab = TodayTab.TODAY,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val offline: Boolean = false,
    val calendarPermissionGranted: Boolean = false,
    val calendarSyncing: Boolean = false,
    val feedbackItemId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

enum class TodayTab { TODAY, FEED }

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repository: DailyIntelligenceRepository,
    private val calendarReader: AndroidCalendarReader,
    private val syncQueueRepository: SollSyncQueueRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        TodayUiState(calendarPermissionGranted = calendarReader.hasPermission())
    )
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectTab(tab: TodayTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, message = null) }
            val cached = repository.cachedToday()
            if (cached != null) {
                _uiState.update { it.copy(snapshot = cached, offline = true) }
            }
            val today = repository.refreshToday()
            val feed = repository.feed(limit = 30)
            _uiState.update { current ->
                current.copy(
                    snapshot = today.getOrNull() ?: current.snapshot,
                    feed = feed.getOrNull()?.items ?: current.feed.ifEmpty { current.snapshot?.feedPreview.orEmpty() },
                    nextCursor = feed.getOrNull()?.nextCursor.orEmpty(),
                    feedHasMore = feed.getOrNull()?.hasMore == true,
                    loading = false,
                    offline = today.isFailure,
                    error = if (today.isFailure && current.snapshot == null) {
                        today.exceptionOrNull()?.message ?: "Soll недоступен и локального снимка ещё нет"
                    } else null,
                    message = if (today.isFailure && current.snapshot != null) "Показан сохранённый снимок" else null,
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.feedHasMore || state.nextCursor.isBlank() || state.loadingMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMore = true) }
            repository.feed(limit = 30, cursor = state.nextCursor).fold(
                onSuccess = { page ->
                    _uiState.update {
                        it.copy(
                            feed = (it.feed + page.items).distinctBy(SollFeedItem::id),
                            nextCursor = page.nextCursor,
                            feedHasMore = page.hasMore,
                            loadingMore = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(loadingMore = false, error = error.message) }
                },
            )
        }
    }

    fun sendFeedback(item: SollFeedItem, decision: String) {
        if (_uiState.value.feedbackItemId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(feedbackItemId = item.id, message = null, error = null) }
            runCatching {
                syncQueueRepository.enqueueFeedFeedback(
                    entityId = item.feedback.entityId.ifBlank { item.id },
                    decision = decision,
                    topic = item.feedback.topic,
                    source = item.feedback.source.ifBlank { item.sourceId },
                )
            }.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            feedbackItemId = null,
                            message = if (decision == "useful") {
                                "Отзыв сохранён и будет отправлен Soll"
                            } else {
                                "Отзыв сохранён: скорректирую ленту после синхронизации"
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(feedbackItemId = null, error = error.message) }
                },
            )
        }
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(calendarPermissionGranted = granted) }
        if (granted) syncCalendar()
    }

    fun syncCalendar() {
        if (!calendarReader.hasPermission() || _uiState.value.calendarSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(calendarSyncing = true, error = null, message = null) }
            val snapshot = runTodayCalendarRead {
                withContext(Dispatchers.IO) { calendarReader.readUpcoming() }
            }.getOrElse { error ->
                _uiState.update { it.copy(calendarSyncing = false, error = error.message) }
                return@launch
            }
            repository.syncCalendar(snapshot.timezone, snapshot.events).fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(calendarSyncing = false, message = "Календарь синхронизирован")
                    }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(calendarSyncing = false, error = error.message) }
                },
            )
        }
    }
}

internal suspend fun <T> runTodayCalendarRead(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
