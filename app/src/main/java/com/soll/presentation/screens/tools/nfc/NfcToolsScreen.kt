package com.soll.presentation.screens.tools.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.ui.components.PassiveChip
import com.soll.domain.nfc.NfcRecordSnapshot
import com.soll.domain.nfc.NfcWritePayloadType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcToolsScreen(
    onBack: () -> Unit,
    viewModel: NfcToolsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val nfcAvailable = nfcAdapter != null
    val nfcEnabled = nfcAdapter?.isEnabled == true
    val hceSupported = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
    }

    DisposableEffect(activity, nfcAdapter, nfcEnabled) {
        if (activity != null && nfcAdapter != null && nfcEnabled) {
            nfcAdapter.enableReaderMode(
                activity,
                { tag -> viewModel.onTagDiscovered(tag) },
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null,
            )
        }
        onDispose {
            if (activity != null && nfcAdapter != null) {
                runCatching { nfcAdapter.disableReaderMode(activity) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearTag, enabled = uiState.lastTag != null) {
                        Icon(Icons.Default.Clear, contentDescription = "Очистить")
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
            if (uiState.isBusy) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            uiState.message?.let { message ->
                item {
                    AssistChip(
                        onClick = viewModel::clearMessage,
                        label = { Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }

            item {
                NfcStatusCard(
                    available = nfcAvailable,
                    enabled = nfcEnabled,
                    hceSupported = hceSupported,
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                )
            }

            item {
                NfcAccessKeyInfoCard(hceSupported = hceSupported)
            }

            item {
                NfcWriteCard(
                    enabled = nfcAvailable && nfcEnabled,
                    uiState = uiState,
                    onReadMode = { viewModel.setWriteMode(false) },
                    onWriteMode = { viewModel.setWriteMode(true) },
                    onOwnedLabConfirmed = viewModel::setOwnedLabConfirmed,
                    onPayloadType = viewModel::setPayloadType,
                    onPayloadChange = viewModel::updatePayload,
                )
            }

            item {
                NfcSafetyCard()
            }

            uiState.lastTag?.let { tag ->
                item {
                    NfcTagCard(tag = tag)
                }
            }
        }
    }
}

@Composable
private fun NfcStatusCard(
    available: Boolean,
    enabled: Boolean,
    hceSupported: Boolean,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Nfc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            !available -> "NFC-модуль не найден"
                            enabled -> "NFC включен"
                            else -> "NFC выключен"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (enabled) {
                            "Поднеси метку к задней части телефона. Телефон читает NFC/HF 13,56 МГц."
                        } else {
                            "Включи NFC в системных настройках."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PassiveChip(text = if (hceSupported) "HCE есть" else "HCE нет")
                if (available && !enabled) {
                    OutlinedButton(onClick = onOpenSettings) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Открыть")
                    }
                }
            }
        }
    }
}

@Composable
private fun NfcAccessKeyInfoCard(hceSupported: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Подъездной ключ",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (hceSupported) {
                            "Телефон поддерживает HCE, но это не клонирование брелока. Нужен официальный мобильный пропуск или свой совместимый считыватель."
                        } else {
                            "На этом устройстве HCE не найден: телефон не сможет работать как программная NFC-карта."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DetailRow(label = "Можно", value = "Диагностировать тип метки и проверить путь к официальному мобильному доступу")
            DetailRow(label = "Нельзя", value = "Копировать UID, секреты MIFARE/DESFire или обходить систему доступа")
            DetailRow(label = "125 кГц", value = "Телефон обычно не видит такие брелоки физически")
        }
    }
}

@Composable
private fun NfcWriteCard(
    enabled: Boolean,
    uiState: NfcToolsUiState,
    onReadMode: () -> Unit,
    onWriteMode: () -> Unit,
    onOwnedLabConfirmed: (Boolean) -> Unit,
    onPayloadType: (NfcWritePayloadType) -> Unit,
    onPayloadChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !uiState.writeMode,
                    onClick = onReadMode,
                    enabled = enabled,
                    label = { Text("Читать") },
                    leadingIcon = { Icon(Icons.Default.Nfc, contentDescription = null) },
                )
                FilterChip(
                    selected = uiState.writeMode,
                    onClick = onWriteMode,
                    enabled = enabled,
                    label = { Text("Писать") },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                )
            }

            if (uiState.writeMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.16f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(
                            checked = uiState.ownedLabConfirmed,
                            onCheckedChange = onOwnedLabConfirmed,
                            enabled = enabled,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Пишу только на свою метку",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Не записывать данные на чужие пропуска, ключи, карты доступа или метки без явного разрешения.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.payloadType == NfcWritePayloadType.TEXT,
                        onClick = { onPayloadType(NfcWritePayloadType.TEXT) },
                        enabled = enabled,
                        label = { Text("Текст") },
                    )
                    FilterChip(
                        selected = uiState.payloadType == NfcWritePayloadType.URI,
                        onClick = { onPayloadType(NfcWritePayloadType.URI) },
                        enabled = enabled,
                        label = { Text("URL") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    )
                }
                OutlinedTextField(
                    value = uiState.payloadInput,
                    onValueChange = onPayloadChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (uiState.payloadType == NfcWritePayloadType.URI) {
                                "https://..."
                            } else {
                                "Текст для метки"
                            },
                        )
                    },
                    minLines = 2,
                    maxLines = 5,
                )
                Text(
                    text = if (uiState.ownedLabConfirmed) {
                        "Запись начнется при касании метки."
                    } else {
                        "Перед касанием метки нужно подтвердить свой стенд."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NfcSafetyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Без разрешения администратора СКУД модуль не помогает копировать ключи, подбирать доступ или обходить домофон. Для своего доступа используй выданный мобильный пропуск.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NfcTagCard(tag: NfcTagUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Последняя метка",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            DetailRow(label = "UID", value = tag.uid.ifBlank { "нет" })
            DetailRow(label = "Технологии", value = tag.technologies.joinToString(", "))
            DetailRow(label = "Семейство", value = tag.accessDiagnostics.detectedFamily)
            DetailRow(label = "Частота", value = tag.accessDiagnostics.frequencyBand)
            DetailRow(label = "Телефон как ключ", value = tag.accessDiagnostics.phoneAsKeyVerdict)
            DetailRow(label = "Рабочий путь", value = tag.accessDiagnostics.officialMobilePath)
            DetailRow(label = "NDEF", value = tag.ndefType ?: if (tag.supportsFormat) "можно форматировать" else "нет")
            DetailRow(label = "Запись", value = if (tag.isWritable || tag.supportsFormat) "доступна" else "нет")
            tag.maxSizeBytes?.let { DetailRow(label = "Размер", value = "$it байт") }
            tag.accessDiagnostics.notes.forEach { note ->
                Text(
                    text = "• $note",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tag.records.isNotEmpty()) {
                Text(
                    text = "Записи",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                tag.records.forEach { record ->
                    NfcRecordRow(record)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.65f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NfcRecordRow(record: NfcRecordSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = record.kind,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = record.value.ifBlank { "пусто" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
