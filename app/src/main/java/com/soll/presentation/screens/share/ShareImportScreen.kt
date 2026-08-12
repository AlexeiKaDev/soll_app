package com.soll.presentation.screens.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.presentation.navigation.SharedLinkPayload

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareImportScreen(
    payload: SharedLinkPayload?,
    onBack: () -> Unit,
    onOpenToday: () -> Unit,
    viewModel: ShareImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(payload?.clientId, payload?.url, payload?.validationError) {
        payload?.let(viewModel::submit)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отправить в Soll") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val displayedPayload = state.payload ?: payload
            Icon(
                imageVector = when (state.status) {
                    ShareImportStatus.SUCCESS -> Icons.Default.CheckCircle
                    ShareImportStatus.ERROR -> Icons.Default.ErrorOutline
                    else -> Icons.Default.Link
                },
                contentDescription = null,
                tint = when (state.status) {
                    ShareImportStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                    ShareImportStatus.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.secondary
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = when (state.status) {
                    ShareImportStatus.QUEUED -> "Сохранено для отправки"
                    ShareImportStatus.SUBMITTING -> "Soll получает материал"
                    ShareImportStatus.SUCCESS -> "Готово"
                    ShareImportStatus.ERROR -> "Не удалось отправить"
                    ShareImportStatus.IDLE -> "Подготовка ссылки"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            if (displayedPayload != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        displayedPayload.title.takeIf(String::isNotBlank)?.let { title ->
                            Text(title, style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            text = displayedPayload.url.ifBlank { "Корректная HTTP(S)-ссылка не найдена" },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.status == ShareImportStatus.SUBMITTING) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = state.message.ifBlank { "Проверяю отправленные данные…" },
                color = if (state.status == ShareImportStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.status == ShareImportStatus.ERROR && displayedPayload?.canSubmit == true) {
                    OutlinedButton(onClick = viewModel::retry) {
                        Text("Повторить")
                    }
                }
                Button(
                    onClick = onOpenToday,
                    enabled = state.status != ShareImportStatus.SUBMITTING,
                ) {
                    Text("К экрану «Сегодня»")
                }
            }
        }
    }
}
