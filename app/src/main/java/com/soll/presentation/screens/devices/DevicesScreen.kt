package com.soll.presentation.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.domain.device.AquikDeviceProfile
import com.soll.domain.device.AquikProvisioningDefaults
import com.soll.domain.device.DeviceConnectionStatus
import com.soll.domain.device.DeviceEvent
import com.soll.domain.device.DeviceProfile
import com.soll.domain.device.DeviceProvisioningPlan
import com.soll.domain.device.DeviceSensorValue
import com.soll.domain.device.DeviceSensorStatus
import com.soll.domain.device.GadgetConfigSummary
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.device.GadgetDiagnosticSummary
import com.soll.domain.device.GadgetKeyValue
import com.soll.domain.device.GadgetProfileCatalog
import com.soll.domain.device.GadgetProfileDescriptor
import com.soll.domain.device.GadgetRouteStatus
import com.soll.domain.device.GadgetScheduleItem
import com.soll.domain.device.GadgetScheduleSummary
import com.soll.domain.device.KnownDevice
import com.soll.ui.components.PassiveChip

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showToken by remember { mutableStateOf(false) }
    var showWifiPassword by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    val selectedProfile = remember(uiState.selectedProfileId, uiState.profiles) {
        uiState.profiles.firstOrNull { it.id == uiState.selectedProfileId }
    }
    val selectedDescriptor = remember(uiState.selectedProfileId, uiState.profiles) {
        selectedProfile?.let(GadgetProfileCatalog::forProfile)
            ?: GadgetProfileCatalog.byProfileId(uiState.selectedProfileId)
    }
    val supportsInfo = selectedProfile.supportsAny(
        AquikDeviceProfile.COMMAND_GET_INFO,
        AquikDeviceProfile.COMMAND_GET_INFO_LEGACY,
    )
    val supportsSensors = selectedProfile.supports(AquikDeviceProfile.COMMAND_GET_SENSORS)
    val supportsConfig = selectedProfile.supportsAny(
        AquikDeviceProfile.COMMAND_GET_CONFIG,
        AquikDeviceProfile.COMMAND_GET_SETTINGS,
    )

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Гаджеты",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Универсальные гаджеты: Aquik и ESP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DeviceStatusChip(uiState.connectionState.status)
            }

            selectedDescriptor?.let { descriptor ->
                GadgetProfileSummary(descriptor)
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null)
                        Text(
                            text = "Подключение гаджета",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    if (uiState.profiles.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            uiState.profiles.forEach { profile ->
                                val selected = profile.id == uiState.selectedProfileId
                                AssistChip(
                                    onClick = { viewModel.selectProfile(profile.id) },
                                    label = { Text(profile.name) },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Memory,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.hostInput,
                        onValueChange = viewModel::updateHost,
                        label = { Text("IP или имя хоста") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.portInput,
                            onValueChange = viewModel::updatePort,
                            label = { Text("Порт") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = uiState.pathInput,
                            onValueChange = viewModel::updatePath,
                            label = { Text("Путь") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }

                    OutlinedTextField(
                        value = uiState.tokenInput,
                        onValueChange = viewModel::updateToken,
                        label = { Text("Токен") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showToken) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(
                                    imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Показать или скрыть токен",
                                )
                            }
                        },
                    )

                    uiState.connectionState.message?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.connectionState.status == DeviceConnectionStatus.ERROR) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::connect,
                            enabled = !uiState.isBusy,
                        ) {
                            if (uiState.isBusy && uiState.connectionState.status == DeviceConnectionStatus.CONNECTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Подключить")
                        }
                        OutlinedButton(onClick = viewModel::disconnect) {
                            Icon(Icons.Default.LinkOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отключить")
                        }
                    }
                }
            }

            GadgetServiceCard(
                configSummary = uiState.configSummary,
                scheduleSummary = uiState.scheduleSummary,
                diagnosticSummary = uiState.diagnosticSummary,
                isBusy = uiState.isBusy,
                onRefreshConfig = viewModel::refreshConfig,
                onRefreshSchedules = viewModel::refreshSchedules,
                onScanI2c = viewModel::scanI2c,
            )

            GadgetServerCard(
                routeStatus = uiState.serverRouteStatus,
                statusMessage = uiState.serverStatusMessage,
                snapshots = uiState.serverSnapshots,
                selectedSnapshot = uiState.selectedServerSnapshot,
                telemetry = uiState.serverTelemetry?.values.orEmpty(),
                events = uiState.serverEvents,
                isBusy = uiState.isServerBusy,
                onRefresh = { viewModel.refreshServerGadgets() },
                onRefreshSelected = viewModel::refreshSelectedServerGadget,
                onSelectSnapshot = viewModel::selectServerSnapshot,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Text(
                            text = "Настройка Aquik-гаджета",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    DeviceProvisioningPlan.aquikSetupSteps().forEach { step ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.provisioningApHostInput,
                        onValueChange = viewModel::updateProvisioningApHost,
                        label = { Text("Адрес настройки") },
                        placeholder = { Text(AquikProvisioningDefaults.setupApHost) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    OutlinedTextField(
                        value = uiState.provisioningSsidInput,
                        onValueChange = viewModel::updateProvisioningSsid,
                        label = { Text("SSID домашней Wi-Fi") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.provisioningPasswordInput,
                            onValueChange = viewModel::updateProvisioningPassword,
                            label = { Text("Пароль Wi-Fi") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showWifiPassword) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(onClick = { showWifiPassword = !showWifiPassword }) {
                                    Icon(
                                        imageVector = if (showWifiPassword) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = "Показать или скрыть пароль Wi-Fi",
                                    )
                                }
                            },
                        )
                        OutlinedTextField(
                            value = uiState.provisioningTimeoutInput,
                            onValueChange = viewModel::updateProvisioningTimeout,
                            label = { Text("Таймаут") },
                            modifier = Modifier.width(112.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::configureWifiViaSetupAp,
                            enabled = !uiState.isProvisioningBusy,
                        ) {
                            if (uiState.isProvisioningBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Отправить Wi-Fi")
                        }
                        OutlinedButton(
                            onClick = viewModel::startSmartConfigOnSetupAp,
                            enabled = !uiState.isProvisioningBusy,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Настроить сеть")
                        }
                        OutlinedButton(
                            onClick = viewModel::refreshProvisioningStatus,
                            enabled = !uiState.isProvisioningBusy,
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Статус")
                        }
                    }

                    if (uiState.provisioningResultText.isNotBlank()) {
                        Text(
                            text = uiState.provisioningResultText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.knownDevices.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Известные гаджеты",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        uiState.knownDevices.forEach { device ->
                            KnownDeviceRow(
                                device = device,
                                selected = device.id == uiState.selectedDeviceId,
                                onClick = { viewModel.selectDevice(device) },
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null)
                        Text(
                            text = "Телеметрия",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = viewModel::refreshSensors,
                            enabled = !uiState.isBusy && supportsSensors,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Датчики")
                        }
                        OutlinedButton(
                            onClick = viewModel::refreshInfo,
                            enabled = !uiState.isBusy && supportsInfo,
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Инфо")
                        }
                        OutlinedButton(
                            onClick = viewModel::refreshConfig,
                            enabled = !uiState.isBusy && supportsConfig,
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Конфиг")
                        }
                    }

                    uiState.telemetry?.let { telemetry ->
                        SensorValues(values = telemetry.values)
                    } ?: Text(
                        text = "Нет данных датчиков",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Text(
                            text = "Актуаторы",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::refreshActuators,
                            enabled = !uiState.isBusy,
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Состояние")
                        }
                    }

                    ActuatorSwitch(
                        label = "Воздушный насос",
                        checked = uiState.airPumpEnabled,
                        enabled = !uiState.isBusy,
                        onCheckedChange = viewModel::setAirPump,
                    )
                    ActuatorSwitch(
                        label = "Водяной насос",
                        checked = uiState.waterPumpEnabled,
                        enabled = !uiState.isBusy,
                        onCheckedChange = viewModel::setWaterPump,
                    )
                    ActuatorSwitch(
                        label = "Вентилятор",
                        checked = uiState.fanEnabled,
                        enabled = !uiState.isBusy,
                        onCheckedChange = viewModel::setFan,
                    )

                    LedSlider(
                        label = "Полный спектр",
                        value = uiState.fullLedValue,
                        enabled = !uiState.isBusy,
                        onValueChange = viewModel::updateFullLedValue,
                        onApply = viewModel::applyFullLedValue,
                    )
                    LedSlider(
                        label = "Белый светодиод",
                        value = uiState.whiteLedValue,
                        enabled = !uiState.isBusy,
                        onValueChange = viewModel::updateWhiteLedValue,
                        onApply = viewModel::applyWhiteLedValue,
                    )

                    if (uiState.actuatorText.isNotBlank()) {
                        Text(
                            text = uiState.actuatorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.infoText.isNotBlank() || uiState.configText.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Text(
                                    text = "Ответ гаджета",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "Конфиг",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Switch(checked = showConfig, onCheckedChange = { showConfig = it })
                            }
                        }
                        Text(
                            text = if (showConfig) {
                                uiState.configText.ifBlank { "Конфигурация еще не получена" }
                            } else {
                                uiState.infoText.ifBlank { "Информация еще не получена" }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (uiState.events.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Журнал гаджета",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        uiState.events.take(8).forEach { event ->
                            DeviceEventRow(event)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetServerCard(
    routeStatus: GadgetRouteStatus,
    statusMessage: String,
    snapshots: List<GadgetCloudSnapshot>,
    selectedSnapshot: GadgetCloudSnapshot?,
    telemetry: List<DeviceSensorValue>,
    events: List<GadgetCloudEvent>,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onRefreshSelected: () -> Unit,
    onSelectSnapshot: (GadgetCloudSnapshot) -> Unit,
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Text(
                        text = "Сервер Soll",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                PassiveChip(text = routeStatus.label)
            }

            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (routeStatus == GadgetRouteStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRefresh,
                    enabled = !isBusy,
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Обновить")
                }
                OutlinedButton(
                    onClick = onRefreshSelected,
                    enabled = !isBusy && selectedSnapshot != null,
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Состояние")
                }
            }

            if (snapshots.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    snapshots.forEach { snapshot ->
                        val selected = snapshot.id == selectedSnapshot?.id
                        AssistChip(
                            onClick = { onSelectSnapshot(snapshot) },
                            label = { Text(snapshot.name) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            selectedSnapshot?.let { snapshot ->
                SummaryBlock(
                    title = "Сводка",
                    items = listOf(
                        GadgetKeyValue("Идентификатор", snapshot.id),
                        GadgetKeyValue("Профиль", snapshot.profileId.ifBlank { "не задан" }),
                        GadgetKeyValue("Пульс связи", snapshot.lastHeartbeatAt ?: "нет"),
                        GadgetKeyValue("Телеметрия", snapshot.lastTelemetryAt ?: "нет"),
                    ),
                )
            }

            if (telemetry.isNotEmpty()) {
                SensorValues(values = telemetry)
            } else {
                Text(
                    text = "Сервер еще не получил телеметрию выбранного гаджета.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (events.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "События сервера",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    events.take(6).forEach { event ->
                        ServerEventRow(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerEventRow(event: GadgetCloudEvent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = event.summary.ifBlank { event.type },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "${event.type} · ${event.createdAt}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetServiceCard(
    configSummary: GadgetConfigSummary,
    scheduleSummary: GadgetScheduleSummary,
    diagnosticSummary: GadgetDiagnosticSummary,
    isBusy: Boolean,
    onRefreshConfig: () -> Unit,
    onRefreshSchedules: () -> Unit,
    onScanI2c: () -> Unit,
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
                Icon(Icons.Default.CloudSync, contentDescription = null)
                Text(
                    text = "Сервис и автоматика",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Блок для автономных ESP-гаджетов: спецпротокол, конфиг, расписания и диагностика без веб-интерфейса на контроллере.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRefreshConfig,
                    enabled = !isBusy,
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Конфиг")
                }
                OutlinedButton(
                    onClick = onRefreshSchedules,
                    enabled = !isBusy,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Расписания")
                }
                OutlinedButton(
                    onClick = onScanI2c,
                    enabled = !isBusy,
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I2C")
                }
            }

            if (configSummary.items.isNotEmpty()) {
                SummaryBlock(title = "Конфигурация", items = configSummary.items)
            }
            if (scheduleSummary.items.isNotEmpty()) {
                ScheduleBlock(scheduleSummary.items)
            }
            if (diagnosticSummary.items.isNotEmpty()) {
                SummaryBlock(title = "Диагностика", items = diagnosticSummary.items)
            }
            if (
                configSummary.items.isEmpty() &&
                scheduleSummary.items.isEmpty() &&
                diagnosticSummary.items.isEmpty()
            ) {
                Text(
                    text = "Подключи гаджет и запроси нужный раздел.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SummaryBlock(
    title: String,
    items: List<GadgetKeyValue>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        items.forEach { item ->
            KeyValueRow(item)
        }
    }
}

@Composable
private fun KeyValueRow(item: GadgetKeyValue) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ScheduleBlock(items: List<GadgetScheduleItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Расписания",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        items.forEach { item ->
            ScheduleRow(item)
        }
    }
}

@Composable
private fun ScheduleRow(item: GadgetScheduleItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${item.type} · ${item.time} · ${item.action}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            PassiveChip(
                text = if (item.enabled) "вкл" else "выкл",
                containerColor = if (item.enabled) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetProfileSummary(descriptor: GadgetProfileDescriptor) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                        text = descriptor.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = descriptor.domain.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PassiveChip(text = "Профиль")
            }
            Text(
                text = descriptor.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = descriptor.setupHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PassiveChip(text = descriptor.protocolName)
            ProfileChipRow("Сценарии", descriptor.primaryUseCases)
            ProfileChipRow("Связь", descriptor.communicationOptions.map { it.chipText })
            ProfileChipRow("Датчики", descriptor.expectedSensors.take(6))
            ProfileChipRow("Актуаторы", descriptor.expectedActuators.take(6))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileChipRow(
    title: String,
    values: List<String>,
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            values.forEach { value ->
                PassiveChip(text = value)
            }
        }
    }
}

@Composable
private fun ActuatorSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (checked) "Включено" else "Выключено",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun LedSlider(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onApply: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${value.toInt()}/255",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Slider(
                value = value.coerceIn(0f, 255f),
                onValueChange = onValueChange,
                valueRange = 0f..255f,
                enabled = enabled,
            )
            OutlinedButton(
                onClick = onApply,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Применить")
            }
        }
    }
}

@Composable
private fun DeviceStatusChip(status: DeviceConnectionStatus) {
    PassiveChip(
        text = status.label(),
        icon = if (status == DeviceConnectionStatus.ERROR) {
            Icons.Default.Security
        } else {
            Icons.Default.Memory
        },
    )
}

@Composable
private fun KnownDeviceRow(
    device: KnownDevice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = device.endpointUrl(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = device.lastStatus.statusText(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SensorValues(values: List<DeviceSensorValue>) {
    if (values.isEmpty()) {
        Text(
            text = "Гаджет вернул пустой набор датчиков",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { sensor ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = sensor.label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (sensor.status != DeviceSensorStatus.UNKNOWN) {
                            PassiveChip(
                                text = sensor.status.label,
                                containerColor = sensor.status.containerColor(),
                                contentColor = sensor.status.contentColor(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = sensor.value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceEventRow(event: DeviceEvent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = event.summary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = event.type,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun DeviceConnectionStatus.label(): String = when (this) {
    DeviceConnectionStatus.DISCONNECTED -> "Отключено"
    DeviceConnectionStatus.CONNECTING -> "Подключение"
    DeviceConnectionStatus.CONNECTED -> "Подключено"
    DeviceConnectionStatus.AUTHENTICATED -> "Авторизовано"
    DeviceConnectionStatus.ERROR -> "Ошибка"
}

private fun String.statusText(): String =
    runCatching { DeviceConnectionStatus.valueOf(this).label() }.getOrDefault(this)

private fun DeviceProfile?.supports(command: String): Boolean =
    this?.capabilities?.contains(command) == true

private fun DeviceProfile?.supportsAny(vararg commands: String): Boolean =
    commands.any { supports(it) }

@Composable
private fun DeviceSensorStatus.containerColor() = when (this) {
    DeviceSensorStatus.NORMAL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
    DeviceSensorStatus.WARNING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    DeviceSensorStatus.CRITICAL -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f)
    DeviceSensorStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
}

@Composable
private fun DeviceSensorStatus.contentColor() = when (this) {
    DeviceSensorStatus.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
    DeviceSensorStatus.NORMAL,
    DeviceSensorStatus.WARNING,
    DeviceSensorStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
