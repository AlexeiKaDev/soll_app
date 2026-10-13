package com.soll.presentation.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soll.domain.soll.SollMonitoredSource
import com.soll.domain.soll.SollRoadmapLine

@Composable
internal fun RoadmapEditorDialog(stageId: String, item: SollRoadmapLine?, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var line by rememberSaveable(stageId, item?.line) { mutableStateOf(item?.line ?: "item_${System.currentTimeMillis()}") }
    var text by rememberSaveable(stageId, item?.line) { mutableStateOf(item?.text.orEmpty()) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (item == null) "Новый пункт Roadmap" else "Изменить пункт") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(text, { text = it }, label = { Text("Описание") }, enabled = !busy, minLines = 3, maxLines = 8)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { Button(onClick = { onSave(line, text) }, enabled = !busy && text.isNotBlank() && line.isNotBlank()) {
            Text(if (busy) "Сохраняю…" else "Сохранить")
        } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } })
}

@Composable
internal fun SourceEditorDialog(source: SollMonitoredSource, busy: Boolean, error: String?,
    onDismiss: () -> Unit, onSave: (String, String, String, Boolean) -> Unit) {
    var name by rememberSaveable(source.id) { mutableStateOf(source.name) }
    var description by rememberSaveable(source.id) { mutableStateOf(source.description) }
    var tags by rememberSaveable(source.id) { mutableStateOf(source.tags.joinToString(", ")) }
    var enabled by rememberSaveable(source.id) { mutableStateOf(source.enabled) }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Изменить источник") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название") }, enabled = !busy, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Описание") }, enabled = !busy, maxLines = 5)
            OutlinedTextField(tags, { tags = it }, label = { Text("Теги через запятую") }, enabled = !busy)
            Row { Checkbox(enabled, { enabled = it }, enabled = !busy); Text("Источник включён", Modifier.padding(top = 14.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { Button(onClick = { onSave(name, description, tags, enabled) }, enabled = !busy && name.isNotBlank()) { Text(if (busy) "Сохраняю…" else "Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } })
}
