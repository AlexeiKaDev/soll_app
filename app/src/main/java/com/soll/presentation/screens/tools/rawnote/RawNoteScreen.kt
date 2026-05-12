package com.soll.presentation.screens.tools.rawnote

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.data.repository.NoteListItem
import com.soll.domain.notes.NoteFilter
import com.soll.domain.notes.NoteSettings
import com.soll.domain.notes.NoteSort
import com.soll.domain.notes.NoteSyncStatus
import com.soll.ui.components.PassiveChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawNoteScreen(
    onBack: () -> Unit,
    viewModel: RawNoteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::attachFile)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заметки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::retryAll) {
                        Icon(Icons.Default.Sync, contentDescription = "Повторить отправку")
                    }
                    IconButton(onClick = viewModel::toggleSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки заметок")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openNewEditor) {
                Icon(Icons.Default.Add, contentDescription = "Новая заметка")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NotesHeader(
                    uiState = uiState,
                    onQueryChange = viewModel::updateQuery,
                    onFilterClick = viewModel::selectFilter,
                    onSortClick = viewModel::selectSort,
                    onClearMessage = viewModel::clearMessage,
                )
            }

            if (uiState.showSettings) {
                item {
                    NotesSettingsPanel(
                        settings = uiState.settings,
                        onSettingsChange = viewModel::updateSettings,
                    )
                }
            }

            if (uiState.editorOpen) {
                item {
                    NoteEditor(
                        state = uiState,
                        onTitleChange = viewModel::updateEditorTitle,
                        onContentChange = viewModel::updateEditorContent,
                        onTagsChange = viewModel::updateEditorTags,
                        onTogglePinned = viewModel::toggleEditorPinned,
                        onToggleArchived = viewModel::toggleEditorArchived,
                        onAttach = { filePicker.launch("*/*") },
                        onSave = { viewModel.saveNote(closeAfterSave = true) },
                        onSend = viewModel::saveAndSend,
                        onClose = viewModel::closeEditor,
                    )
                }
            }

            if (uiState.notes.isEmpty()) {
                item {
                    EmptyNotesCard(
                        hasQuery = uiState.query.isNotBlank() || uiState.filter != NoteFilter.ALL,
                        onNew = viewModel::openNewEditor,
                    )
                }
            } else {
                items(uiState.notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onOpen = { viewModel.editNote(note) },
                        onTogglePinned = { viewModel.togglePinned(note) },
                        onToggleArchived = { viewModel.toggleArchived(note) },
                        onRetry = { viewModel.retryNote(note) },
                        onDelete = { viewModel.deleteNote(note) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesHeader(
    uiState: RawNoteUiState,
    onQueryChange: (String) -> Unit,
    onFilterClick: (NoteFilter) -> Unit,
    onSortClick: (NoteSort) -> Unit,
    onClearMessage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Поиск") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NoteFilter.entries.forEach { filter ->
                FilterChip(
                    selected = uiState.filter == filter,
                    onClick = { onFilterClick(filter) },
                    label = { Text(filter.label) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NoteSort.entries.forEach { sort ->
                FilterChip(
                    selected = uiState.sort == sort,
                    onClick = { onSortClick(sort) },
                    label = { Text(sort.label) },
                )
            }
            PassiveChip(text = "Заметок: ${uiState.notes.size}")
            if (uiState.openSyncCount > 0) {
                PassiveChip(text = "К отправке: ${uiState.openSyncCount}")
            }
        }

        uiState.message?.let { message ->
            StatusLine(
                text = message,
                isError = uiState.isError,
                onClose = onClearMessage,
            )
        }
    }
}

@Composable
private fun NoteEditor(
    state: RawNoteUiState,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTagsChange: (String) -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit,
    onAttach: () -> Unit,
    onSave: () -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.editorNoteId == null) "Новая заметка" else "Редактирование",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть редактор")
                }
            }

            OutlinedTextField(
                value = state.editorTitle,
                onValueChange = onTitleChange,
                label = { Text("Название") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.editorContent,
                onValueChange = onContentChange,
                label = { Text("Текст") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp),
                minLines = 9,
            )

            OutlinedTextField(
                value = state.editorTags,
                onValueChange = onTagsChange,
                label = { Text("Теги") },
                placeholder = { Text("идея, проект, #важно") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.editorPinned,
                    onClick = onTogglePinned,
                    label = { Text("Закрепить") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = state.editorArchived,
                    onClick = onToggleArchived,
                    label = { Text("Архив") },
                    leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = onAttach,
                    label = { Text(if (state.isAttaching) "Добавляю..." else "Вложение") },
                    leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSave,
                    enabled = state.canSave && !state.isSaving && !state.isSyncing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сохранить")
                }
                Button(
                    onClick = onSend,
                    enabled = state.canSave && !state.isSaving && !state.isSyncing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("В Soll")
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: NoteListItem,
    onOpen: () -> Unit,
    onTogglePinned: () -> Unit,
    onToggleArchived: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (note.pinned) 0.42f else 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = note.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onTogglePinned) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = if (note.pinned) "Открепить" else "Закрепить",
                        tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(note.syncStatus, note.lastError)
                TextChip(formatDate(note.updatedAt))
                note.tags.take(5).forEach { tag ->
                    TextChip("#$tag")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (note.syncStatus == NoteSyncStatus.ERROR || note.syncStatus == NoteSyncStatus.QUEUED) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Повтор")
                    }
                }
                TextButton(onClick = onToggleArchived) {
                    Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (note.archived) "Вернуть" else "Архив")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }
        }
    }
}

@Composable
private fun NotesSettingsPanel(
    settings: NoteSettings,
    onSettingsChange: (NoteSettings) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Настройки заметок",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            SettingSwitchRow(
                title = "Автоотправка в Soll",
                detail = "После сохранения заметка сама попадет в очередь отправки",
                checked = settings.autoSync,
                onCheckedChange = { onSettingsChange(settings.copy(autoSync = it)) },
            )
            SettingSwitchRow(
                title = "Только по Wi-Fi",
                detail = "Фоновая отправка будет ждать Wi-Fi",
                checked = settings.wifiOnly,
                onCheckedChange = { onSettingsChange(settings.copy(wifiOnly = it)) },
            )
            SettingSwitchRow(
                title = "Оставлять локально",
                detail = "Отправленные заметки остаются в списке телефона",
                checked = settings.keepLocalAfterSync,
                onCheckedChange = { onSettingsChange(settings.copy(keepLocalAfterSync = it)) },
            )
            OutlinedTextField(
                value = settings.defaultTags,
                onValueChange = { onSettingsChange(settings.copy(defaultTags = it)) },
                label = { Text("Теги по умолчанию") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyNotesCard(
    hasQuery: Boolean,
    onNew: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (hasQuery) "Ничего не найдено" else "Заметок пока нет",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (hasQuery) {
                    "Измени поиск или фильтр."
                } else {
                    "Создай первую заметку, она сразу сохранится на телефоне."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasQuery) {
                Button(onClick = onNew) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Новая заметка")
                }
            }
        }
    }
}

@Composable
private fun StatusLine(
    text: String,
    isError: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Скрыть")
        }
    }
}

@Composable
private fun StatusChip(
    status: NoteSyncStatus,
    error: String?,
) {
    val color = when (status) {
        NoteSyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
        NoteSyncStatus.ERROR -> MaterialTheme.colorScheme.error
        NoteSyncStatus.SYNCING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    TextChip(
        text = if (status == NoteSyncStatus.ERROR && !error.isNullOrBlank()) {
            "${status.label}: ${error.take(40)}"
        } else {
            status.label
        },
        tint = color,
    )
}

@Composable
private fun TextChip(
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    PassiveChip(text = text, contentColor = tint)
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("ru")).format(Date(timestamp))
