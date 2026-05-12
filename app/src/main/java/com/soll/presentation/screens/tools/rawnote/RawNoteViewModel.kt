package com.soll.presentation.screens.tools.rawnote

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soll.data.repository.NoteListItem
import com.soll.data.repository.NoteRepository
import com.soll.data.repository.SettingsRepository
import com.soll.domain.notes.NoteFilter
import com.soll.domain.notes.NoteSettings
import com.soll.domain.notes.NoteSort
import com.soll.domain.notes.NoteSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RawNoteUiState(
    val notes: List<NoteListItem> = emptyList(),
    val query: String = "",
    val filter: NoteFilter = NoteFilter.ALL,
    val sort: NoteSort = NoteSort.UPDATED,
    val openSyncCount: Int = 0,
    val settings: NoteSettings = NoteSettings(),
    val showSettings: Boolean = false,
    val editorOpen: Boolean = false,
    val editorNoteId: String? = null,
    val editorTitle: String = "",
    val editorContent: String = "",
    val editorTags: String = "",
    val editorPinned: Boolean = false,
    val editorArchived: Boolean = false,
    val isSaving: Boolean = false,
    val isSyncing: Boolean = false,
    val isAttaching: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val canSave: Boolean
        get() = editorContent.isNotBlank()
}

private data class NoteListRequest(
    val filter: NoteFilter = NoteFilter.ALL,
    val query: String = "",
    val sort: NoteSort = NoteSort.UPDATED,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RawNoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RawNoteUiState(settings = settingsRepository.getNoteSettings()))
    val uiState: StateFlow<RawNoteUiState> = _uiState.asStateFlow()
    private val listRequest = MutableStateFlow(NoteListRequest())

    init {
        observeNotes()
        observeSyncCount()
        retryReadyOnOpen()
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        listRequest.update { it.copy(query = value) }
    }

    fun selectFilter(filter: NoteFilter) {
        _uiState.update { it.copy(filter = filter) }
        listRequest.update { it.copy(filter = filter) }
    }

    fun selectSort(sort: NoteSort) {
        _uiState.update { it.copy(sort = sort) }
        listRequest.update { it.copy(sort = sort) }
    }

    fun openNewEditor() {
        _uiState.update {
            it.copy(
                editorOpen = true,
                editorNoteId = null,
                editorTitle = "",
                editorContent = "",
                editorTags = "",
                editorPinned = false,
                editorArchived = false,
                message = null,
                isError = false,
            )
        }
    }

    fun editNote(note: NoteListItem) {
        _uiState.update {
            it.copy(
                editorOpen = true,
                editorNoteId = note.id,
                editorTitle = note.title,
                editorContent = note.content,
                editorTags = note.tags.joinToString(", "),
                editorPinned = note.pinned,
                editorArchived = note.archived,
                message = null,
                isError = false,
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(
                editorOpen = false,
                editorNoteId = null,
                editorTitle = "",
                editorContent = "",
                editorTags = "",
                editorPinned = false,
                editorArchived = false,
            )
        }
    }

    fun updateEditorTitle(value: String) {
        _uiState.update { it.copy(editorTitle = value, message = null, isError = false) }
    }

    fun updateEditorContent(value: String) {
        _uiState.update { it.copy(editorContent = value, message = null, isError = false) }
    }

    fun updateEditorTags(value: String) {
        _uiState.update { it.copy(editorTags = value, message = null, isError = false) }
    }

    fun toggleEditorPinned() {
        _uiState.update { it.copy(editorPinned = !it.editorPinned) }
    }

    fun toggleEditorArchived() {
        _uiState.update { it.copy(editorArchived = !it.editorArchived) }
    }

    fun saveNote(closeAfterSave: Boolean = true) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null, isError = false) }
            runCatching {
                noteRepository.upsertNote(
                    id = state.editorNoteId,
                    title = state.editorTitle,
                    content = state.editorContent,
                    tagsInput = state.editorTags,
                    pinned = state.editorPinned,
                    archived = state.editorArchived,
                )
            }.onSuccess { result ->
                _uiState.update {
                    val base = it.copy(
                        isSaving = false,
                        editorNoteId = result.noteId,
                        message = if (result.syncStatus == NoteSyncStatus.QUEUED) {
                            "Заметка сохранена и поставлена в очередь Soll"
                        } else {
                            "Заметка сохранена"
                        },
                        isError = false,
                    )
                    if (closeAfterSave) {
                        base.copy(
                            editorOpen = false,
                            editorNoteId = null,
                            editorTitle = "",
                            editorContent = "",
                            editorTags = "",
                            editorPinned = false,
                            editorArchived = false,
                        )
                    } else {
                        base
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = error.message ?: "Не удалось сохранить заметку",
                        isError = true,
                    )
                }
            }
        }
    }

    fun saveAndSend() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, isError = false) }
            runCatching {
                val save = noteRepository.upsertNote(
                    id = state.editorNoteId,
                    title = state.editorTitle,
                    content = state.editorContent,
                    tagsInput = state.editorTags,
                    pinned = state.editorPinned,
                    archived = state.editorArchived,
                    queueForSync = true,
                )
                noteRepository.sendNoteNow(save.noteId)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        editorOpen = false,
                        editorNoteId = null,
                        editorTitle = "",
                        editorContent = "",
                        editorTags = "",
                        editorPinned = false,
                        editorArchived = false,
                        message = when (result.syncStatus) {
                            NoteSyncStatus.SYNCED -> "Заметка отправлена в Soll: ${result.filename}"
                            else -> "Заметка сохранена. Отправка повторится автоматически."
                        },
                        isError = result.syncStatus == NoteSyncStatus.ERROR,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        message = error.message ?: "Не удалось отправить заметку",
                        isError = true,
                    )
                }
            }
        }
    }

    fun attachFile(uri: Uri) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isAttaching = true, message = null, isError = false) }
            runCatching {
                val noteId = state.editorNoteId ?: noteRepository.upsertNote(
                    id = null,
                    title = state.editorTitle,
                    content = state.editorContent,
                    tagsInput = state.editorTags,
                    pinned = state.editorPinned,
                    archived = state.editorArchived,
                    queueForSync = true,
                ).noteId
                noteRepository.addAttachment(noteId, uri)
                noteId
            }.onSuccess { noteId ->
                _uiState.update {
                    it.copy(
                        isAttaching = false,
                        editorNoteId = noteId,
                        message = "Вложение добавлено и поставлено в очередь отправки",
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAttaching = false,
                        message = error.message ?: "Не удалось добавить вложение",
                        isError = true,
                    )
                }
            }
        }
    }

    fun togglePinned(note: NoteListItem) {
        viewModelScope.launch {
            noteRepository.setPinned(note.id, !note.pinned)
        }
    }

    fun toggleArchived(note: NoteListItem) {
        viewModelScope.launch {
            noteRepository.setArchived(note.id, !note.archived)
        }
    }

    fun deleteNote(note: NoteListItem) {
        viewModelScope.launch {
            noteRepository.deleteNote(note.id)
            _uiState.update { it.copy(message = "Заметка удалена", isError = false) }
        }
    }

    fun retryNote(note: NoteListItem) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, isError = false) }
            runCatching { noteRepository.sendNoteNow(note.id) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            message = if (result.syncStatus == NoteSyncStatus.SYNCED) {
                                "Заметка отправлена в Soll"
                            } else {
                                "Заметка остается в очереди"
                            },
                            isError = result.syncStatus == NoteSyncStatus.ERROR,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            message = error.message ?: "Повтор не выполнен",
                            isError = true,
                        )
                    }
                }
        }
    }

    fun retryAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, isError = false) }
            val summary = noteRepository.retryAllReady()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    message = when {
                        summary.processed == 0 -> "Готовых к отправке заметок нет"
                        summary.failed == 0 -> "Отправлено: ${summary.succeeded}"
                        else -> "Отправлено: ${summary.succeeded}, с ошибкой: ${summary.failed}"
                    },
                    isError = summary.failed > 0,
                )
            }
        }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun updateSettings(settings: NoteSettings) {
        settingsRepository.saveNoteSettings(settings)
        _uiState.update { it.copy(settings = settings) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    private fun observeNotes() {
        viewModelScope.launch {
            listRequest
                .flatMapLatest { request ->
                    noteRepository.observeNotes(
                        filter = request.filter,
                        query = request.query,
                        sort = request.sort,
                    )
                }
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            message = "Не удалось прочитать заметки: ${error.message}",
                            isError = true,
                        )
                    }
                }
                .collect { notes ->
                    _uiState.update { it.copy(notes = notes) }
                }
        }
    }

    private fun observeSyncCount() {
        viewModelScope.launch {
            noteRepository.observeOpenSyncCount()
                .catch { error ->
                    _uiState.update {
                        it.copy(message = "Не удалось прочитать очередь: ${error.message}", isError = true)
                    }
                }
                .collect { count ->
                    _uiState.update { it.copy(openSyncCount = count) }
                }
        }
    }

    private fun retryReadyOnOpen() {
        viewModelScope.launch {
            noteRepository.retryAllReady(limit = 5)
        }
    }
}
