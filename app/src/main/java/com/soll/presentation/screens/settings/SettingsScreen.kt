package com.soll.presentation.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.data.notification.SystemNotificationImportanceMode
import com.soll.domain.assistant.RiskTier
import com.soll.domain.deviceqa.DeviceQaCategory
import com.soll.domain.deviceqa.DeviceQaCheck
import com.soll.domain.deviceqa.DeviceQaCheckId
import com.soll.domain.deviceqa.DeviceQaStatus
import com.soll.domain.deviceqa.DeviceQaSummary
import com.soll.domain.notification.SollNotificationChannel
import com.soll.ui.theme.SollThemeVariant
import com.soll.ui.components.PassiveChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenDeviceQa: () -> Unit = {},
    onScanSollPairingQr: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSollToken by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshBatteryStatus()
    }

    // Show snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            // Soll Backend Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Сервер Soll",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Основной канал приложения: чат Soll, действия, задачи, push-уведомления и синхронизация с локальным сервером.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onScanSollPairingQr,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сканировать QR")
                    }

                    OutlinedTextField(
                        value = uiState.sollServerUrl,
                        onValueChange = viewModel::updateSollServerUrl,
                        label = { Text("URL сервера") },
                        placeholder = { Text("https://sales.monolith-ost.com/") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )

                    OutlinedTextField(
                        value = uiState.sollApiPathPrefix,
                        onValueChange = viewModel::updateSollApiPathPrefix,
                        label = { Text("API путь") },
                        placeholder = { Text("api/v1/soll") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = uiState.sollAccessToken,
                        onValueChange = viewModel::updateSollAccessToken,
                        label = { Text("Bearer-токен") },
                        placeholder = { Text("access_token из /api/v1/auth/login") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showSollToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showSollToken = !showSollToken }) {
                                Icon(
                                    imageVector = if (showSollToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Показать или скрыть токен Soll"
                                )
                            }
                        },
                    )

                    OutlinedTextField(
                        value = uiState.sollSyncIntervalMinutes,
                        onValueChange = viewModel::updateSollSyncInterval,
                        label = { Text("Интервал синхронизации, минут") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Загружать файлы только по Wi-Fi",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Для будущих загрузок заметок и медиа",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.sollWifiOnlyUpload,
                            onCheckedChange = viewModel::setSollWifiOnlyUpload
                        )
                    }

                    uiState.sollHealthStatus?.let { status ->
                        PassiveChip(
                            text = status,
                            icon = if (status == "Работает") Icons.Default.CheckCircle else Icons.Default.Warning,
                        )
                    }

                    uiState.sollHealthMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    uiState.sollSyncSummary?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.saveSollSettings() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Сохранить")
                        }
                        Button(
                            onClick = { viewModel.checkSollHealth() },
                            enabled = !uiState.isCheckingSollHealth && !uiState.isSyncingSoll,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isCheckingSollHealth) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Проверить")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.resetSollEndpointToRecommended() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Подставить рекомендуемый адрес")
                    }

                    Button(
                        onClick = { viewModel.syncSollNow() },
                        enabled = !uiState.isCheckingSollHealth && !uiState.isSyncingSoll,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.isSyncingSoll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Синхронизировать сейчас")
                    }
                }
            }

            DeviceQaSummaryCard(
                checks = uiState.deviceQaChecks,
                isPostingNotification = uiState.isPostingDeviceQaNotification,
                onRefresh = viewModel::refreshDeviceQa,
                onOpenDeviceQa = onOpenDeviceQa,
                onTestNotification = viewModel::postDeviceQaNotification,
            )

            SystemNotificationFilterSection(
                importanceMode = uiState.systemNotificationImportanceMode,
                channels = uiState.systemNotificationChannels,
                pushTokenRegisteredAt = uiState.sollPushTokenRegisteredAt,
                pushTokenLastError = uiState.sollPushTokenLastError,
                isRetryingPushToken = uiState.isRetryingSollPushToken,
                onModeSelected = viewModel::setSystemNotificationImportanceMode,
                onChannelToggle = viewModel::setSystemNotificationChannelEnabled,
                onRetryPushToken = viewModel::retryAndroidPushTokenRegistration,
            )

            uiState.deviceQaReport?.let { report ->
                DeviceQaReportDialog(
                    report = report,
                    onShare = viewModel::shareDeviceQaReport,
                    onDismiss = viewModel::dismissDeviceQaReport,
                )
            }

            ThemeSection(
                selectedVariant = uiState.appThemeVariant,
                onVariantSelected = viewModel::setAppThemeVariant,
            )

            // Voice Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Голос",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    VoicePolicyRow(
                        icon = Icons.Default.Lock,
                        title = "Только на разблокированном устройстве",
                        description = "Блокировать запуск голоса, если экран заблокирован",
                        checked = uiState.voiceRequiresUnlockedDevice,
                        onCheckedChange = viewModel::setVoiceRequiresUnlockedDevice,
                    )

                    VoicePolicyRow(
                        icon = Icons.Default.Headset,
                        title = "Только с гарнитурой",
                        description = "Запускать голосовой ввод только при подключенных наушниках или гарнитуре",
                        checked = uiState.voiceRequiresHeadset,
                        onCheckedChange = viewModel::setVoiceRequiresHeadset,
                    )

                    VoicePolicyRow(
                        icon = Icons.Default.CloudOff,
                        title = "Локальное STT",
                        description = "Использовать on-device распознавание Android, если оно доступно",
                        checked = uiState.voiceLocalOnly,
                        onCheckedChange = viewModel::setVoiceLocalOnly,
                    )

                    VoicePolicyRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Фраза «Солл»",
                        description = "Выполнять ручную голосовую команду только после фразы «Солл»",
                        checked = uiState.voiceWakePhraseRequired,
                        onCheckedChange = viewModel::setVoiceWakePhraseRequired,
                    )
                }
            }

            // Proactive Suggestions Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Предложения",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Локальные подсказки на главном экране без пуш-спама",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.proactiveSuggestionsEnabled,
                            onCheckedChange = viewModel::setProactiveSuggestionsEnabled,
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Лимит в день: ${uiState.proactiveSuggestionsDailyLimit}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Slider(
                            value = uiState.proactiveSuggestionsDailyLimit.toFloat(),
                            onValueChange = {
                                viewModel.setProactiveSuggestionsDailyLimit(it.roundToInt())
                            },
                            valueRange = 1f..6f,
                            steps = 4,
                            enabled = uiState.proactiveSuggestionsEnabled,
                        )
                        Text(
                            text = "Принятые и скрытые предложения не появляются повторно в течение дня, отложенные возвращаются позже.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    ProactiveDeliveryRow(
                        icon = Icons.Default.Notifications,
                        title = "Системные уведомления",
                        description = "Показывать полезные предложения через Android не чаще одного раза в день",
                        checked = uiState.proactiveSystemDeliveryEnabled,
                        enabled = uiState.proactiveSuggestionsEnabled,
                        onCheckedChange = viewModel::setProactiveSystemDeliveryEnabled,
                    )

                    Text(
                        text = "Серверные предложения и действия теперь приходят в чат Soll и Android-уведомления через sync/outbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Assistant Memory Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Память ассистента",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Только локально: принятые предложения и будущие явные предпочтения. Просмотр, экспорт и очистка находятся в логах.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.assistantMemoryEnabled,
                        onCheckedChange = viewModel::setAssistantMemoryEnabled,
                    )
                }
            }

            // Background Sync Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Фоновая синхронизация",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "WorkManager и служебный sync проверяют чат и задачи, когда приложение свернуто.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = viewModel::checkSollHealth) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверить")
                    }
                }
            }

            // Battery Optimization Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (uiState.isBatteryOptimizationDisabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Оптимизация батареи",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = if (uiState.isBatteryOptimizationDisabled)
                            "Оптимизация батареи отключена для Soll. Чат и серверная синхронизация смогут надежнее работать в фоне."
                        else
                            "Оптимизация батареи включена. Из-за этого серверная синхронизация и системные уведомления могут задерживаться.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (!uiState.isBatteryOptimizationDisabled) {
                        Button(
                            onClick = { viewModel.requestBatteryOptimizationExemption() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отключить оптимизацию батареи")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.openBatterySettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Открыть настройки батареи")
                    }
                }
            }

            // Permissions Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Разрешения",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Выдайте разрешения, чтобы работали чат Soll, действия, задачи, активность и системные уведомления. Управление доступно в настройках приложения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = { viewModel.openAppSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Открыть разрешения приложения")
                    }

                    OutlinedButton(
                        onClick = { viewModel.openWriteSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Brightness6, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Разрешить изменение системных настроек")
                    }

                    Text(
                        text = "Нужно: уведомления, геолокация для активности, микрофон для голоса и доступы для включенных возможностей.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Capabilities Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null)
                                Text(
                                    text = "Возможности",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Управляйте тем, какие действия Soll можно выполнять с устройства и сервера.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.riskyCapabilitiesEnabled,
                            onCheckedChange = { viewModel.setRiskyCapabilitiesEnabled(it) }
                        )
                    }

                    Text(
                        text = if (uiState.riskyCapabilitiesEnabled)
                            "Рискованные действия доступны, если включен их отдельный переключатель."
                        else
                            "Рискованные действия заблокированы глобально. Безопасные информационные действия продолжают работать.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    uiState.capabilityGroups.forEach { group ->
                        CapabilityGroup(group = group, onToggle = viewModel::setCapabilityEnabled)
                    }
                }
            }

            // OEM Instructions Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Не останавливать приложение",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Некоторые устройства (Xiaomi, Huawei, Samsung, Oppo) агрессивно экономят батарею. " +
                                "Может понадобиться вручную настроить автозапуск и фоновую работу.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedButton(
                        onClick = { viewModel.openAutoStartSettings() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RocketLaunch, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Открыть настройки автозапуска")
                    }
                }
            }

            // About Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "О приложении",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Soll подключает Android к серверу, чату, действиям, задачам и локальным push-уведомлениям.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Версия 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceQaScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.refreshDeviceQa()
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Проверка устройства") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refreshDeviceQa) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить проверку")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DeviceQaSection(
                checks = uiState.deviceQaChecks,
                isPostingNotification = uiState.isPostingDeviceQaNotification,
                onRefresh = viewModel::refreshDeviceQa,
                onTestNotification = viewModel::postDeviceQaNotification,
                onOpenNotifications = viewModel::openNotificationSettings,
                onOpenBattery = viewModel::openBatterySettings,
                onPassed = viewModel::markDeviceQaPassed,
                onProblem = viewModel::markDeviceQaProblem,
                onClear = viewModel::clearDeviceQaResult,
                onReport = viewModel::showDeviceQaReport,
            )
        }
    }

    uiState.deviceQaReport?.let { report ->
        DeviceQaReportDialog(
            report = report,
            onShare = viewModel::shareDeviceQaReport,
            onDismiss = viewModel::dismissDeviceQaReport,
        )
    }
}

@Composable
private fun DeviceQaSummaryCard(
    checks: List<DeviceQaCheck>,
    isPostingNotification: Boolean,
    onRefresh: () -> Unit,
    onOpenDeviceQa: () -> Unit,
    onTestNotification: () -> Unit,
) {
    val effectiveStatuses = checks.map { it.effectiveStatus }
    val problemCount = effectiveStatuses.count {
        it == DeviceQaStatus.PROBLEM || it == DeviceQaStatus.MANUAL_PROBLEM
    }
    val warningCount = effectiveStatuses.count {
        it == DeviceQaStatus.WARNING || it == DeviceQaStatus.NEEDS_MANUAL_TEST
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Проверка устройства",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = DeviceQaSummary.headline(checks),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить проверку")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PassiveChip(text = "Проблем: $problemCount")
                PassiveChip(text = "Проверить: $warningCount")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenDeviceQa,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть")
                }
                OutlinedButton(
                    onClick = onTestNotification,
                    enabled = !isPostingNotification,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isPostingNotification) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Тест")
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    selectedVariant: SollThemeVariant,
    onVariantSelected: (SollThemeVariant) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Тема",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Выберите мобильную палитру приложения. Soll - светлая teal-тема, остальные варианты остаются темными.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SollThemeVariant.entries.forEach { variant ->
                    FilterChip(
                        selected = selectedVariant == variant,
                        onClick = { onVariantSelected(variant) },
                        label = { Text(variant.title) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(
                text = selectedVariant.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SystemNotificationFilterSection(
    importanceMode: SystemNotificationImportanceMode,
    channels: List<SystemNotificationChannelUiState>,
    pushTokenRegisteredAt: Long,
    pushTokenLastError: String,
    isRetryingPushToken: Boolean,
    onModeSelected: (SystemNotificationImportanceMode) -> Unit,
    onChannelToggle: (SollNotificationChannel, Boolean) -> Unit,
    onRetryPushToken: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Фильтр уведомлений",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "События остаются в журнале. По умолчанию в Android идут только чат и важное.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Важность",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SystemNotificationImportanceMode.entries.forEach { mode ->
                        FilterChip(
                            selected = importanceMode == mode,
                            onClick = { onModeSelected(mode) },
                            label = { Text(mode.shortLabel()) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    text = importanceMode.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "Push FCM",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = pushTokenStatusText(pushTokenRegisteredAt, pushTokenLastError),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pushTokenLastError.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = onRetryPushToken,
                        enabled = !isRetryingPushToken,
                    ) {
                        if (isRetryingPushToken) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRetryingPushToken) "Проверяю" else "Повторить")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Каналы",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                channels.forEach { item ->
                    SystemNotificationChannelRow(
                        item = item,
                        onCheckedChange = { enabled -> onChannelToggle(item.channel, enabled) },
                    )
                }
            }
        }
    }
}

private fun pushTokenStatusText(registeredAt: Long, lastError: String): String {
    if (lastError.isNotBlank()) {
        return "Ошибка регистрации: ${lastError.take(160)}"
    }
    if (registeredAt <= 0L) {
        return "Токен еще не подтвержден сервером"
    }
    val stamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(registeredAt))
    return "Последняя регистрация: $stamp"
}

@Composable
private fun SystemNotificationChannelRow(
    item: SystemNotificationChannelUiState,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = item.channel.settingsIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.channel.settingsLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = item.channel.settingsDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

private fun SystemNotificationImportanceMode.shortLabel(): String = when (this) {
    SystemNotificationImportanceMode.HIGH_ONLY -> "Критичное"
    SystemNotificationImportanceMode.DEFAULT_AND_HIGH -> "Рабочее"
    SystemNotificationImportanceMode.ALL -> "Все"
}

private fun SystemNotificationImportanceMode.description(): String = when (this) {
    SystemNotificationImportanceMode.HIGH_ONLY -> "В шторку идут только high/alert события из включенных каналов."
    SystemNotificationImportanceMode.DEFAULT_AND_HIGH -> "В шторку идут default/high события из включенных каналов; low остается в журнале."
    SystemNotificationImportanceMode.ALL -> "В шторку идут все включенные каналы, включая low и технический шум."
}

private fun SollNotificationChannel.settingsLabel(): String = when (this) {
    SollNotificationChannel.CHAT -> "Чат"
    SollNotificationChannel.ALERTS -> "Важное"
    SollNotificationChannel.TOOL_JOBS -> "Работы"
    SollNotificationChannel.EVENTS -> "Инфо"
    SollNotificationChannel.SERVER_SYNC -> "Синхронизация"
    SollNotificationChannel.BOT_SERVICE -> "Архив"
    SollNotificationChannel.TTS_PLAYBACK -> "Читалка"
    SollNotificationChannel.MUSIC_PLAYBACK -> "Музыка"
    SollNotificationChannel.ACTIVITY_TRACKING -> "Активность"
}

private fun SollNotificationChannel.settingsDescription(): String = when (this) {
    SollNotificationChannel.CHAT -> "Новые сообщения от сервера и FCM."
    SollNotificationChannel.ALERTS -> "Критичные проверки и ручные алерты."
    SollNotificationChannel.TOOL_JOBS -> "Финальные статусы фоновых работ; успехи могут быть частыми."
    SollNotificationChannel.EVENTS -> "Обычные предложения и информационные события; выключено по умолчанию."
    SollNotificationChannel.SERVER_SYNC -> "Технические события фоновой синхронизации; выключено по умолчанию."
    SollNotificationChannel.BOT_SERVICE -> "Архивные события Telegram-бота."
    SollNotificationChannel.TTS_PLAYBACK -> "Уведомления чтения вслух."
    SollNotificationChannel.MUSIC_PLAYBACK -> "Уведомления плеера."
    SollNotificationChannel.ACTIVITY_TRACKING -> "Уведомления трекера активности."
}

private fun SollNotificationChannel.settingsIcon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    SollNotificationChannel.CHAT -> Icons.Default.Notifications
    SollNotificationChannel.ALERTS -> Icons.Default.NotificationImportant
    SollNotificationChannel.TOOL_JOBS -> Icons.Default.Build
    SollNotificationChannel.EVENTS -> Icons.Default.NotificationsNone
    SollNotificationChannel.SERVER_SYNC -> Icons.Default.Sync
    SollNotificationChannel.BOT_SERVICE -> Icons.Default.Storage
    SollNotificationChannel.TTS_PLAYBACK -> Icons.Default.RecordVoiceOver
    SollNotificationChannel.MUSIC_PLAYBACK -> Icons.Default.MusicNote
    SollNotificationChannel.ACTIVITY_TRACKING -> Icons.AutoMirrored.Filled.DirectionsWalk
}

@Composable
private fun DeviceQaSection(
    checks: List<DeviceQaCheck>,
    isPostingNotification: Boolean,
    onRefresh: () -> Unit,
    onTestNotification: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBattery: () -> Unit,
    onPassed: (DeviceQaCheckId) -> Unit,
    onProblem: (DeviceQaCheckId) -> Unit,
    onClear: (DeviceQaCheckId) -> Unit,
    onReport: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Проверка устройства",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = DeviceQaSummary.headline(checks),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить проверку")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpenNotifications,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Уведомления")
                }
                Button(
                    onClick = onTestNotification,
                    enabled = !isPostingNotification,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isPostingNotification) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Тест")
                }
            }

            OutlinedButton(
                onClick = onReport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Отчет")
            }

            DeviceQaCategory.entries.forEach { category ->
                val group = checks.filter { it.category == category }
                if (group.isNotEmpty()) {
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    group.forEach { check ->
                        DeviceQaRow(
                            check = check,
                            onAction = {
                                when (check.id) {
                                    DeviceQaCheckId.NOTIFICATION_PERMISSION,
                                    DeviceQaCheckId.NOTIFICATION_CHANNELS,
                                    DeviceQaCheckId.NOTIFICATION_ANDROID13_FLOW -> onOpenNotifications()
                                    DeviceQaCheckId.NOTIFICATION_TAP_ROUTING -> onTestNotification()
                                    DeviceQaCheckId.BATTERY_OPTIMIZATION -> onOpenBattery()
                                    DeviceQaCheckId.THEME_VISUAL_PASS,
                                    DeviceQaCheckId.GADGET_PROTOCOL_SCHEMA,
                                    DeviceQaCheckId.GADGET_SERVER_LOCAL_BINDING,
                                    DeviceQaCheckId.GADGET_MESH_OUTBOX_WORKER,
                                    DeviceQaCheckId.GADGET_READ_ONLY_COMMAND_WORKER,
                                    DeviceQaCheckId.GADGET_MANUAL_WRITE_FLOW -> onRefresh()
                                }
                            },
                            onPassed = { onPassed(check.id) },
                            onProblem = { onProblem(check.id) },
                            onClear = { onClear(check.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceQaReportDialog(
    report: String,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Отчет проверки") },
        text = {
            SelectionContainer {
                Text(
                    text = report,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Поделиться")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
    )
}

@Composable
private fun DeviceQaRow(
    check: DeviceQaCheck,
    onAction: () -> Unit,
    onPassed: () -> Unit,
    onProblem: () -> Unit,
    onClear: () -> Unit,
) {
    val status = check.effectiveStatus
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = status.icon(),
                    contentDescription = null,
                    tint = status.tint(),
                    modifier = Modifier.size(22.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = check.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = check.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    check.expectedResult?.let { expected ->
                        Text(
                            text = "Ожидание: $expected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    check.roadmapRef?.let { ref ->
                        Text(
                            text = "План: $ref",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    check.lastManualResult?.let { result ->
                        Text(
                            text = "Ручная проверка: ${result.status.label}, ${formatQaTime(result.checkedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        result.deviceSummary?.let { device ->
                            Text(
                                text = "Устройство: $device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                PassiveChip(text = status.label)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                check.actionLabel?.let { label ->
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(label)
                    }
                }
                if (check.manual) {
                    OutlinedButton(
                        onClick = onPassed,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Прошло")
                    }
                    OutlinedButton(
                        onClick = onProblem,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Проблема")
                    }
                }
            }

            if (check.lastManualResult != null) {
                TextButton(onClick = onClear) {
                    Text("Сбросить ручную отметку")
                }
            }
        }
    }
}

@Composable
private fun DeviceQaStatus.icon(): androidx.compose.ui.graphics.vector.ImageVector = when (this) {
    DeviceQaStatus.OK,
    DeviceQaStatus.MANUAL_OK -> Icons.Default.CheckCircle
    DeviceQaStatus.WARNING,
    DeviceQaStatus.NEEDS_MANUAL_TEST -> Icons.Default.Warning
    DeviceQaStatus.PROBLEM,
    DeviceQaStatus.MANUAL_PROBLEM -> Icons.Default.Error
}

@Composable
private fun DeviceQaStatus.tint() = when (this) {
    DeviceQaStatus.OK,
    DeviceQaStatus.MANUAL_OK -> MaterialTheme.colorScheme.primary
    DeviceQaStatus.WARNING,
    DeviceQaStatus.NEEDS_MANUAL_TEST -> MaterialTheme.colorScheme.tertiary
    DeviceQaStatus.PROBLEM,
    DeviceQaStatus.MANUAL_PROBLEM -> MaterialTheme.colorScheme.error
}

private fun formatQaTime(timestamp: Long): String =
    SimpleDateFormat("dd.MM HH:mm", Locale.forLanguageTag("ru")).format(Date(timestamp))

@Composable
private fun VoicePolicyRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ProactiveDeliveryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun CapabilityGroup(
    group: CapabilityGroupUiState,
    onToggle: (String, Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = group.riskTier.title(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = group.riskTier.description(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        group.items.forEach { item ->
            CapabilityRow(item = item, onToggle = onToggle)
        }
    }
}

@Composable
private fun CapabilityRow(
    item: CapabilityItemUiState,
    onToggle: (String, Boolean) -> Unit
) {
    val enabled = !item.blockedByGlobalRiskToggle
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (item.auditRequired) {
                        PassiveChip(text = "Аудит", icon = Icons.Default.History)
                    }
                }
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.blockedByGlobalRiskToggle) {
                    Text(
                        text = "Заблокировано глобальным переключателем рискованных возможностей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (item.permissions.isNotEmpty()) {
                    Text(
                        text = item.permissions.joinToString(", ") { it.substringAfterLast(".") },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = item.configuredEnabled,
                enabled = enabled,
                onCheckedChange = { onToggle(item.id, it) }
            )
        }
    }
}

private fun RiskTier.title(): String = when (this) {
    RiskTier.SAFE_INFO -> "Безопасная информация"
    RiskTier.PERSONAL_DATA -> "Личные данные"
    RiskTier.DEVICE_CONTROL -> "Управление устройством"
    RiskTier.COMMUNICATION -> "Связь"
    RiskTier.FILE_MEDIA -> "Файлы и медиа"
    RiskTier.MONEY_OR_EXTERNAL_ACTION -> "Внешние действия"
    RiskTier.DUAL_USE_HARDWARE -> "Железо двойного назначения"
    RiskTier.BLOCKED -> "Заблокировано"
}

private fun RiskTier.description(): String = when (this) {
    RiskTier.SAFE_INFO -> "Статус и справка только на чтение."
    RiskTier.PERSONAL_DATA -> "Команды, которые могут открыть приватные локальные данные."
    RiskTier.DEVICE_CONTROL -> "Команды, которые меняют состояние устройства."
    RiskTier.COMMUNICATION -> "Команды, которые связываются с людьми или внешними целями."
    RiskTier.FILE_MEDIA -> "Команды, которые снимают, читают или отправляют локальные файлы/медиа."
    RiskTier.MONEY_OR_EXTERNAL_ACTION -> "Резерв для будущих инструментов с внешними последствиями."
    RiskTier.DUAL_USE_HARDWARE -> "Резерв для gated-инструментов железа и security lab."
    RiskTier.BLOCKED -> "Команды, которые никогда не должны запускаться."
}
