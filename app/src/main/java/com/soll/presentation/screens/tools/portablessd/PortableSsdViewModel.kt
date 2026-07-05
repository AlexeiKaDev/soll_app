package com.soll.presentation.screens.tools.portablessd

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.PortableSsdRepository
import com.soll.domain.portablessd.PortableSsdEntry
import com.soll.domain.portablessd.PortableSsdEntryContent
import com.soll.domain.portablessd.PortableSsdSection
import com.soll.domain.portablessd.PortableSsdSnapshot
import com.soll.domain.portablessd.PortableSsdSnapshotStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PortableSsdUiState(
    val snapshot: PortableSsdSnapshot = PortableSsdSnapshot(
        status = PortableSsdSnapshotStatus.NO_ROOT,
        message = "Выбери SSD через системный диалог",
    ),
    val selectedTreeUri: String? = null,
    val selectedSection: PortableSsdSection = PortableSsdSection.WIKI,
    val query: String = "",
    val selectedEntry: PortableSsdEntry? = null,
    val selectedEntryContent: PortableSsdEntryContent? = null,
    val isOpeningEntry: Boolean = false,
    val entryError: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val currentEntries: List<PortableSsdEntry>
        get() {
            val source = when (selectedSection) {
                PortableSsdSection.WIKI -> snapshot.wiki
                PortableSsdSection.DAILY -> snapshot.daily
                PortableSsdSection.TASKS -> snapshot.tasks
            }
            val needle = query.trim().lowercase()
            if (needle.isBlank()) return source
            return source.filter { entry ->
                entry.title.lowercase().contains(needle) ||
                    entry.relativePath.lowercase().contains(needle) ||
                    entry.preview.lowercase().contains(needle) ||
                    entry.searchText.lowercase().contains(needle)
            }
        }
}

@HiltViewModel
class PortableSsdViewModel @Inject constructor(
    private val repository: PortableSsdRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        PortableSsdUiState(selectedTreeUri = repository.selectedTreeUri),
    )
    val uiState: StateFlow<PortableSsdUiState> = _uiState.asStateFlow()

    init {
        if (repository.selectedTreeUri != null) {
            refresh()
        }
    }

    fun selectTree(uri: Uri) {
        loadSnapshot { repository.selectTree(uri) }
    }

    fun refresh() {
        loadSnapshot { repository.refresh() }
    }

    fun clearSelection() {
        repository.clearSelection()
        _uiState.value = PortableSsdUiState()
    }

    fun selectSection(section: PortableSsdSection) {
        _uiState.update { it.copy(selectedSection = section, selectedEntry = null) }
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun openEntry(entry: PortableSsdEntry) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedEntry = entry,
                    selectedEntryContent = null,
                    isOpeningEntry = true,
                    entryError = null,
                )
            }
            try {
                val content = repository.openEntry(entry)
                _uiState.update {
                    it.copy(
                        selectedEntryContent = content,
                        isOpeningEntry = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isOpeningEntry = false,
                        entryError = error.message ?: "Не удалось открыть статью",
                    )
                }
            }
        }
    }

    fun closeEntry() {
        _uiState.update {
            it.copy(
                selectedEntry = null,
                selectedEntryContent = null,
                isOpeningEntry = false,
                entryError = null,
            )
        }
    }

    private fun loadSnapshot(loader: suspend () -> PortableSsdSnapshot) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    selectedEntry = null,
                    selectedEntryContent = null,
                    isOpeningEntry = false,
                    entryError = null,
                )
            }
            try {
                val snapshot = loader()
                val section = when {
                    snapshot.wiki.isNotEmpty() -> PortableSsdSection.WIKI
                    snapshot.daily.isNotEmpty() -> PortableSsdSection.DAILY
                    snapshot.tasks.isNotEmpty() -> PortableSsdSection.TASKS
                    else -> _uiState.value.selectedSection
                }
                _uiState.update {
                    it.copy(
                        snapshot = snapshot,
                        selectedTreeUri = repository.selectedTreeUri,
                        selectedSection = section,
                        isLoading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Не удалось прочитать SSD",
                    )
                }
            }
        }
    }
}
