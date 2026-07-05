package com.soll.presentation.screens.tools.portablessd

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.portablessd.PortableSsdEntry
import com.soll.domain.portablessd.PortableSsdEntryContent
import com.soll.domain.portablessd.PortableSsdEntryContentSource
import com.soll.domain.portablessd.PortableSsdSection
import com.soll.domain.portablessd.PortableSsdSnapshotStatus
import com.soll.ui.components.PassiveChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortableSsdScreen(
    onBack: () -> Unit,
    viewModel: PortableSsdViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let(viewModel::selectTree)
    }

    uiState.selectedEntry?.let { entry ->
        EntryReaderScreen(
            entry = entry,
            content = uiState.selectedEntryContent,
            isLoading = uiState.isOpeningEntry,
            error = uiState.entryError,
            onBack = viewModel::closeEntry,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SSD Wiki") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Выбрать SSD")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SsdStatusCard(
                    uiState = uiState,
                    onPick = { folderPicker.launch(null) },
                    onRefresh = viewModel::refresh,
                    onClear = viewModel::clearSelection,
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::updateQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Поиск по SSD") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }

            item {
                SectionTabs(
                    uiState = uiState,
                    onSelect = viewModel::selectSection,
                )
            }

            if (uiState.isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.currentEntries.isEmpty()) {
                item {
                    EmptySsdCard(uiState)
                }
            } else {
                items(uiState.currentEntries, key = { it.id }) { entry ->
                    SsdEntryCard(
                        entry = entry,
                        onOpen = { viewModel.openEntry(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SsdStatusCard(
    uiState: PortableSsdUiState,
    onPick: () -> Unit,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (uiState.snapshot.status) {
                        PortableSsdSnapshotStatus.READY -> "Portable SSD подключен"
                        PortableSsdSnapshotStatus.INVALID -> "SSD не распознан"
                        PortableSsdSnapshotStatus.NO_ROOT -> "SSD не выбран"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                }
            }
            Text(
                text = uiState.error ?: uiState.snapshot.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.error == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PassiveChip(text = "Wiki: ${uiState.snapshot.wiki.size}")
                PassiveChip(text = "Daily: ${uiState.snapshot.daily.size}")
                PassiveChip(text = "Tasks: ${uiState.snapshot.tasks.size}")
                uiState.snapshot.vaultPath.takeIf { it.isNotBlank() }?.let { PassiveChip(text = it) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick) {
                    Text("Выбрать SSD")
                }
                OutlinedButton(onClick = onRefresh, enabled = uiState.selectedTreeUri != null) {
                    Text("Обновить")
                }
                TextButton(onClick = onClear, enabled = uiState.selectedTreeUri != null) {
                    Text("Сбросить")
                }
            }
        }
    }
}

@Composable
private fun SectionTabs(
    uiState: PortableSsdUiState,
    onSelect: (PortableSsdSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PortableSsdSection.entries.forEach { section ->
            val count = when (section) {
                PortableSsdSection.WIKI -> uiState.snapshot.wiki.size
                PortableSsdSection.DAILY -> uiState.snapshot.daily.size
                PortableSsdSection.TASKS -> uiState.snapshot.tasks.size
            }
            FilterChip(
                selected = uiState.selectedSection == section,
                onClick = { onSelect(section) },
                label = { Text("${section.label} ($count)") },
            )
        }
    }
}

@Composable
private fun EmptySsdCard(uiState: PortableSsdUiState) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = if (uiState.query.isBlank()) "Нет записей в выбранном разделе" else "Поиск ничего не нашел",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Android v1 читает SSD только в режиме просмотра. Для обновления данных запусти sync на desktop.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SsdEntryCard(
    entry: PortableSsdEntry,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.relativePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.preview.isNotBlank()) {
                Text(
                    text = entry.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EntryReaderScreen(
    entry: PortableSsdEntry,
    content: PortableSsdEntryContent?,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = entry.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = entry.relativePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PassiveChip(text = entry.section.label)
                            content?.let { PassiveChip(text = contentSourceText(it.source)) }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            error?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            content?.let { loaded ->
                val visibleText = loaded.text.ifBlank { "Файл пустой" }.take(READER_TEXT_LIMIT)
                item {
                    SelectionContainer {
                        Text(
                            text = visibleText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (loaded.text.length > READER_TEXT_LIMIT) {
                    item {
                        Text(
                            text = "Показан первый фрагмент файла. Полная копия сохранена в памяти телефона.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }
        }
    }
}

private fun contentSourceText(source: PortableSsdEntryContentSource): String =
    when (source) {
        PortableSsdEntryContentSource.SSD -> "Скопировано на телефон"
        PortableSsdEntryContentSource.PHONE_CACHE -> "Из памяти телефона"
        PortableSsdEntryContentSource.SNAPSHOT -> "Из текущего списка"
    }

private const val READER_TEXT_LIMIT = 80_000
