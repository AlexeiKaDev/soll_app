package com.soll.presentation.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soll.domain.soll.SollTask
import com.soll.domain.soll.SollTaskInput
import com.soll.domain.soll.SollTaskProject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal fun TaskTab.label(): String = when (this) {
    TaskTab.ALL -> "Открытые"
    TaskTab.TODAY -> "Сегодня"
    TaskTab.BLOCKED -> "Заблокированные"
    TaskTab.INBOX -> "Входящие"
    TaskTab.STALE -> "Зависшие"
    TaskTab.DEFERRED -> "Отложенные"
    TaskTab.IDEAS -> "Идеи"
    TaskTab.DONE -> "Завершённые"
}

@Composable
internal fun CompactTaskRow(task: SollTask, pending: Boolean, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(task.priority, Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(listOfNotNull(task.projectName?.takeIf { it.isNotBlank() }, task.status.statusLabel(),
                task.dueDate?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pending) Text("Ожидает синхронизации", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            task.shortHoldReason()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TaskFilterSheet(state: TaskBoardUiState, onPriority: (TaskPriorityFilter) -> Unit,
    onProject: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Фильтры", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text("Приоритет", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TaskPriorityFilter.entries.forEach { priority ->
                    FilterChip(selected = state.selectedPriority == priority, onClick = { onPriority(priority) },
                        label = { Text(priority.label) })
                }
            }
            Text("Проект", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { onProject(null) }) { Text(if (state.selectedProjectId == null) "✓ Все проекты" else "Все проекты") }
            state.projects.forEach { project ->
                TextButton(onClick = { onProject(project.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (state.selectedProjectId == project.id) "✓ " else "") + project.name,
                        modifier = Modifier.fillMaxWidth())
                }
            }
            if (state.projects.isEmpty()) Text("Список проектов ещё не получен с сервера.",
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onPriority(TaskPriorityFilter.ALL); onProject(null) }) { Text("Сбросить") }
                Button(onClick = onDismiss) { Text("Готово") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditorSheet(input: SollTaskInput, projects: List<SollTaskProject>, editing: Boolean,
    saving: Boolean, error: String?, onChange: (SollTaskInput) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    var projectMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val currentSaving by rememberUpdatedState(saving)
    if (showDatePicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = runCatching {
            LocalDate.parse(input.dueDate).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }.getOrNull())
        DatePickerDialog(onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = {
                picker.selectedDateMillis?.let { millis -> onChange(input.copy(dueDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())) }
                showDatePicker = false
            }) { Text("Выбрать") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } }) {
            DatePicker(state = picker)
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { value -> !currentSaving || value != SheetValue.Hidden })) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (editing) "Редактировать задачу" else "Новая задача", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            OutlinedTextField(value = input.title, onValueChange = { onChange(input.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Название *") }, maxLines = 3, enabled = !saving,
                isError = input.title.isBlank() && error != null, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = input.description, onValueChange = { onChange(input.copy(description = it)) },
                modifier = Modifier.fillMaxWidth(), label = { Text("Описание") }, minLines = 2, maxLines = 8,
                enabled = !saving, shape = RoundedCornerShape(12.dp))
            Text("Приоритет", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("A", "B", "C", "D").forEach { priority -> FilterChip(selected = input.priority == priority,
                    onClick = { onChange(input.copy(priority = priority)) }, enabled = !saving, label = { Text(priority) }) }
            }
            Box {
                OutlinedButton(onClick = { projectMenu = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(input.projectName.ifBlank { "Без проекта" }, Modifier.weight(1f)); Icon(Icons.Default.KeyboardArrowDown, null)
                }
                DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                    DropdownMenuItem(text = { Text("Без проекта") }, onClick = { onChange(input.copy(projectId = "", projectName = "")); projectMenu = false })
                    projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = {
                        onChange(input.copy(projectId = project.id, projectName = project.name)); projectMenu = false
                    }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showDatePicker = true }, enabled = !saving, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(input.dueDate.ifBlank { "Добавить срок" })
                }
                if (input.dueDate.isNotBlank()) IconButton(onClick = { onChange(input.copy(dueDate = "")) }, enabled = !saving) {
                    Icon(Icons.Default.Close, contentDescription = "Убрать срок")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (saving) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (saving) "Сохраняю…" else if (editing) "Сохранить" else "Создать задачу")
            }
            TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Закрыть · черновик сохранится") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailSheet(task: SollTask, busy: Boolean, pendingAction: Boolean, pendingEvidence: Boolean,
    evidenceBusy: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit, onAttach: () -> Unit,
    onToday: () -> Unit, onStart: () -> Unit, onDone: () -> Unit, onDefer: () -> Unit, onReject: () -> Unit) {
    var confirmReject by remember(task.id) { mutableStateOf(false) }
    if (confirmReject) AlertDialog(onDismissRequest = { confirmReject = false }, title = { Text("Отклонить задачу?") },
        text = { Text("Она будет отмечена как отклонённая. История сохранится.") },
        confirmButton = { TextButton(onClick = { confirmReject = false; onReject() }) { Text("Отклонить") } },
        dismissButton = { TextButton(onClick = { confirmReject = false }) { Text("Отмена") } })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 680.dp), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(task.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text(listOfNotNull(task.projectName, task.status.statusLabel(), "Приоритет ${task.priority}", task.dueDate).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { TaskActions(task.status, task.id, busy || pendingAction, onToday, onStart, onDone, onDefer, { confirmReject = true }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Изменить") }
                    OutlinedButton(onClick = onAttach, enabled = !evidenceBusy && !pendingEvidence) {
                        if (evidenceBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.AttachFile, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Вложить")
                    }
                }
                if (pendingAction || pendingEvidence) Text("Есть действия в очереди синхронизации.", style = MaterialTheme.typography.bodySmall)
            }
            if (task.description.isNotBlank()) item { Text(task.description, style = MaterialTheme.typography.bodyLarge) }
            item { HorizontalDivider() }
            item { TaskDetailSection(task) }
        }
    }
}
