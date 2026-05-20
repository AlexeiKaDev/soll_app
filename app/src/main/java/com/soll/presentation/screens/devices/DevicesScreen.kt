package com.soll.presentation.screens.devices

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.soll.domain.device.GadgetDeviceDetailTab
import com.soll.domain.device.GadgetAutomationRule
import com.soll.domain.device.GadgetAutomationSummary
import com.soll.domain.device.GadgetConfigSummary
import com.soll.domain.device.GadgetCloudCommand
import com.soll.domain.device.GadgetCloudEvent
import com.soll.domain.device.GadgetCloudSnapshot
import com.soll.domain.device.GadgetDiagnosticSummary
import com.soll.domain.device.GadgetDiscoveryCandidate
import com.soll.domain.device.GadgetDiscoveryMethod
import com.soll.domain.device.GadgetKeyValue
import com.soll.domain.device.GadgetProfileCatalog
import com.soll.domain.device.GadgetProfileDescriptor
import com.soll.domain.device.GadgetRouteStatus
import com.soll.domain.device.GadgetScheduleItem
import com.soll.domain.device.GadgetScheduleSummary
import com.soll.domain.device.GadgetScreenMode
import com.soll.domain.device.KnownDevice
import com.soll.domain.soll.SollMeshOutboxItem
import com.soll.domain.soll.SollMeshStatus
import com.soll.ui.components.PassiveChip
import androidx.core.content.ContextCompat

@Suppress("UNREACHABLE_CODE", "UNUSED_VARIABLE")
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showToken by remember { mutableStateOf(false) }
    var showWifiPassword by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    val wifiDiscoveryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.startDiscovery()
        }
    }
    fun startDiscoveryWithPermissions() {
        if (
            uiState.discoveryMethod == GadgetDiscoveryMethod.WIFI_AP &&
            !context.hasWifiDiscoveryPermissions()
        ) {
            wifiDiscoveryPermissionLauncher.launch(requiredWifiDiscoveryPermissions())
        } else {
            viewModel.startDiscovery()
        }
    }
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

            when (uiState.screenMode) {
                GadgetScreenMode.DEVICE_LIST -> {
                    GadgetDeviceListContent(
                        uiState = uiState,
                        onOpenDiscovery = viewModel::openDiscovery,
                        onOpenDevice = viewModel::openDeviceDetail,
                        onRefreshServer = { viewModel.refreshServerGadgets() },
                        onCheckProtocol = { viewModel.checkProtocolSchema() },
                        onUpdatePairingDeviceId = viewModel::updateSollPairingDeviceId,
                        onUpdatePairingSecret = viewModel::updateSollPairingSecret,
                        onIssueDeviceToken = viewModel::issueSollDeviceToken,
                        onRefreshDeviceToken = viewModel::refreshSollDeviceToken,
                        onSendServerCommand = viewModel::sendServerGadgetCommand,
                        onExecuteManualCommand = viewModel::executeManualServerCommand,
                        onSelectServer = viewModel::selectServerSnapshot,
                    )
                    return@Column
                }
                GadgetScreenMode.DISCOVERY -> {
                    GadgetDiscoveryContent(
                        uiState = uiState,
                        onBack = viewModel::openDeviceList,
                        onSelectMethod = viewModel::selectDiscoveryMethod,
                        onStart = ::startDiscoveryWithPermissions,
                        onStop = viewModel::stopDiscovery,
                        onUpdateManual = viewModel::updateDiscoveryManualInput,
                        onUpdateQr = viewModel::updateDiscoveryQrInput,
                        onAddCandidate = viewModel::addDiscoveredCandidate,
                        onUpdateProvisioningApHost = viewModel::updateProvisioningApHost,
                        onUpdateProvisioningSsid = viewModel::updateProvisioningSsid,
                        onUpdateProvisioningPassword = viewModel::updateProvisioningPassword,
                        onConfigureWifi = viewModel::configureWifiViaSetupAp,
                        showWifiPassword = showWifiPassword,
                        onToggleWifiPassword = { showWifiPassword = !showWifiPassword },
                    )
                    return@Column
                }
                GadgetScreenMode.DEVICE_DETAIL -> {
                    GadgetDeviceDetailContent(
                        uiState = uiState,
                        selectedProfile = selectedProfile,
                        selectedDescriptor = selectedDescriptor,
                        showToken = showToken,
                        onToggleToken = { showToken = !showToken },
                        showConfig = showConfig,
                        onToggleConfig = { showConfig = !showConfig },
                        onBack = viewModel::openDeviceList,
                        onSelectTab = viewModel::selectDetailTab,
                        onUpdateHost = viewModel::updateHost,
                        onUpdatePort = viewModel::updatePort,
                        onUpdatePath = viewModel::updatePath,
                        onUpdateToken = viewModel::updateToken,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onRefreshSensors = viewModel::refreshSensors,
                        onRefreshInfo = viewModel::refreshInfo,
                        onRefreshConfig = viewModel::refreshConfig,
                        onRefreshActuators = viewModel::refreshActuators,
                        onSetAirPump = viewModel::setAirPump,
                        onSetWaterPump = viewModel::setWaterPump,
                        onSetFan = viewModel::setFan,
                        onUpdateFullLed = viewModel::updateFullLedValue,
                        onUpdateWhiteLed = viewModel::updateWhiteLedValue,
                        onApplyFullLed = viewModel::applyFullLedValue,
                        onApplyWhiteLed = viewModel::applyWhiteLedValue,
                        onRefreshSchedules = viewModel::refreshSchedules,
                        onRefreshAutomation = viewModel::refreshAutomation,
                        onScanI2c = viewModel::scanI2c,
                        onUpdateSettingsDeviceName = viewModel::updateSettingsDeviceName,
                        onUpdateSettingsTimezone = viewModel::updateSettingsTimezone,
                        onUpdateSettingsSensorInterval = viewModel::updateSettingsSensorInterval,
                        onUpdateSettingsDisplayBrightness = viewModel::updateSettingsDisplayBrightness,
                        onUpdateSettingsAutoMode = viewModel::updateSettingsAutoMode,
                        onApplySettings = viewModel::applySettings,
                        onUpdateCalibrationSensor = viewModel::updateCalibrationSensor,
                        onUpdateCalibrationOffset = viewModel::updateCalibrationOffset,
                        onUpdateCalibrationReference = viewModel::updateCalibrationReference,
                        onApplyCalibration = viewModel::applyCalibration,
                        onUpdateScheduleId = viewModel::updateScheduleId,
                        onUpdateScheduleName = viewModel::updateScheduleName,
                        onUpdateScheduleType = viewModel::updateScheduleType,
                        onUpdateScheduleTime = viewModel::updateScheduleTime,
                        onUpdateScheduleAction = viewModel::updateScheduleAction,
                        onUpdateScheduleEnabled = viewModel::updateScheduleEnabled,
                        onSaveSchedule = viewModel::saveSchedule,
                        onDeleteSchedule = viewModel::deleteSchedule,
                        onEditSchedule = viewModel::editSchedule,
                        onUpdateAutomationId = viewModel::updateAutomationId,
                        onUpdateAutomationName = viewModel::updateAutomationName,
                        onUpdateAutomationSensor = viewModel::updateAutomationSensor,
                        onUpdateAutomationOperator = viewModel::updateAutomationOperator,
                        onUpdateAutomationThreshold = viewModel::updateAutomationThreshold,
                        onUpdateAutomationAction = viewModel::updateAutomationAction,
                        onUpdateAutomationEnabled = viewModel::updateAutomationEnabled,
                        onSaveAutomation = viewModel::saveAutomation,
                        onDeleteAutomation = viewModel::deleteAutomation,
                        onEditAutomation = viewModel::editAutomation,
                    )
                    return@Column
                }
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
                uiState = uiState,
                onRefreshConfig = viewModel::refreshConfig,
                onRefreshSchedules = viewModel::refreshSchedules,
                onRefreshAutomation = viewModel::refreshAutomation,
                onScanI2c = viewModel::scanI2c,
                onUpdateSettingsDeviceName = viewModel::updateSettingsDeviceName,
                onUpdateSettingsTimezone = viewModel::updateSettingsTimezone,
                onUpdateSettingsSensorInterval = viewModel::updateSettingsSensorInterval,
                onUpdateSettingsDisplayBrightness = viewModel::updateSettingsDisplayBrightness,
                onUpdateSettingsAutoMode = viewModel::updateSettingsAutoMode,
                onApplySettings = viewModel::applySettings,
                onUpdateCalibrationSensor = viewModel::updateCalibrationSensor,
                onUpdateCalibrationOffset = viewModel::updateCalibrationOffset,
                onUpdateCalibrationReference = viewModel::updateCalibrationReference,
                onApplyCalibration = viewModel::applyCalibration,
                onUpdateScheduleId = viewModel::updateScheduleId,
                onUpdateScheduleName = viewModel::updateScheduleName,
                onUpdateScheduleType = viewModel::updateScheduleType,
                onUpdateScheduleTime = viewModel::updateScheduleTime,
                onUpdateScheduleAction = viewModel::updateScheduleAction,
                onUpdateScheduleEnabled = viewModel::updateScheduleEnabled,
                onSaveSchedule = viewModel::saveSchedule,
                onDeleteSchedule = viewModel::deleteSchedule,
                onEditSchedule = viewModel::editSchedule,
                onUpdateAutomationId = viewModel::updateAutomationId,
                onUpdateAutomationName = viewModel::updateAutomationName,
                onUpdateAutomationSensor = viewModel::updateAutomationSensor,
                onUpdateAutomationOperator = viewModel::updateAutomationOperator,
                onUpdateAutomationThreshold = viewModel::updateAutomationThreshold,
                onUpdateAutomationAction = viewModel::updateAutomationAction,
                onUpdateAutomationEnabled = viewModel::updateAutomationEnabled,
                onSaveAutomation = viewModel::saveAutomation,
                onDeleteAutomation = viewModel::deleteAutomation,
                onEditAutomation = viewModel::editAutomation,
            )

            GadgetServerCard(
                routeStatus = uiState.serverRouteStatus,
                statusMessage = uiState.serverStatusMessage,
                snapshots = uiState.serverSnapshots,
                selectedSnapshot = uiState.selectedServerSnapshot,
                telemetry = uiState.serverTelemetry?.values.orEmpty(),
                events = uiState.serverEvents,
                commands = uiState.serverCommands,
                meshStatus = uiState.meshStatus,
                meshRouteStatus = uiState.meshRouteStatus,
                meshStatusMessage = uiState.meshStatusMessage,
                meshOutbox = uiState.meshOutbox,
                isBusy = uiState.isServerBusy,
                isMeshBusy = uiState.isMeshBusy,
                isServerCommandBusy = uiState.isServerCommandBusy,
                onRefresh = { viewModel.refreshServerGadgets() },
                onRefreshSelected = viewModel::refreshSelectedServerGadget,
                onSelectSnapshot = viewModel::selectServerSnapshot,
                onExecuteManualCommand = viewModel::executeManualServerCommand,
                onRefreshMesh = { viewModel.refreshMeshWorker() },
                onClaimNextMesh = viewModel::claimNextMeshOutbox,
                onAckMesh = viewModel::ackMeshOutbox,
                onRetryMesh = viewModel::retryMeshOutbox,
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
private fun GadgetDeviceListContent(
    uiState: DevicesUiState,
    onOpenDiscovery: (GadgetDiscoveryMethod) -> Unit,
    onOpenDevice: (KnownDevice) -> Unit,
    onRefreshServer: () -> Unit,
    onCheckProtocol: () -> Unit,
    onUpdatePairingDeviceId: (String) -> Unit,
    onUpdatePairingSecret: (String) -> Unit,
    onIssueDeviceToken: () -> Unit,
    onRefreshDeviceToken: () -> Unit,
    onSendServerCommand: (String) -> Unit,
    onExecuteManualCommand: (String) -> Unit,
    onSelectServer: (GadgetCloudSnapshot) -> Unit,
) {
    if (uiState.knownDevices.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Устройств пока нет",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Найди Soll/Aquik устройство в сети, подключись к AP настройки или добавь IP вручную.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { onOpenDiscovery(GadgetDiscoveryMethod.LAN_MDNS) }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Найти")
                    }
                    OutlinedButton(onClick = { onOpenDiscovery(GadgetDiscoveryMethod.MANUAL) }) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("IP")
                    }
                    OutlinedButton(onClick = { onOpenDiscovery(GadgetDiscoveryMethod.QR) }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("QR")
                    }
                }
            }
        }
    } else {
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
                    Text(
                        text = "Устройства",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedButton(onClick = { onOpenDiscovery(GadgetDiscoveryMethod.LAN_MDNS) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить")
                    }
                }
                uiState.knownDevices.forEach { device ->
                    KnownDeviceRow(
                        device = device,
                        selected = false,
                        onClick = { onOpenDevice(device) },
                    )
                }
            }
        }
    }

    GadgetServerListCard(
        uiState = uiState,
        onRefresh = onRefreshServer,
        onCheckProtocol = onCheckProtocol,
        onUpdatePairingDeviceId = onUpdatePairingDeviceId,
        onUpdatePairingSecret = onUpdatePairingSecret,
        onIssueDeviceToken = onIssueDeviceToken,
        onRefreshDeviceToken = onRefreshDeviceToken,
        onSendCommand = onSendServerCommand,
        onExecuteManualCommand = onExecuteManualCommand,
        onSelect = onSelectServer,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SollDevicePairingSection(
    uiState: DevicesUiState,
    onUpdateDeviceId: (String) -> Unit,
    onUpdateSecret: (String) -> Unit,
    onIssueToken: () -> Unit,
    onRefreshToken: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Device-token pairing",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = uiState.deviceTokenStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PassiveChip(
                    text = when {
                        uiState.hasSollDeviceToken -> "device bearer"
                        uiState.hasSollPairingSecret -> "secret saved"
                        else -> "manual"
                    },
                )
            }
            OutlinedTextField(
                value = uiState.devicePairingDeviceIdInput,
                onValueChange = onUpdateDeviceId,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Device ID") },
                placeholder = { Text("soll-demo-android") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.devicePairingSecretInput,
                onValueChange = onUpdateSecret,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Pairing secret") },
                placeholder = {
                    Text(if (uiState.hasSollPairingSecret) "секрет сохранен, можно оставить пустым" else "из Desktop Automation")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onIssueToken,
                    enabled = !uiState.isPairingBusy,
                ) {
                    if (uiState.isPairingBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Security, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Получить device-token")
                }
                OutlinedButton(
                    onClick = onRefreshToken,
                    enabled = uiState.hasSollDeviceToken && !uiState.isPairingBusy,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Обновить bearer")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetServerListCard(
    uiState: DevicesUiState,
    onRefresh: () -> Unit,
    onCheckProtocol: () -> Unit,
    onUpdatePairingDeviceId: (String) -> Unit,
    onUpdatePairingSecret: (String) -> Unit,
    onIssueDeviceToken: () -> Unit,
    onRefreshDeviceToken: () -> Unit,
    onSendCommand: (String) -> Unit,
    onExecuteManualCommand: (String) -> Unit,
    onSelect: (GadgetCloudSnapshot) -> Unit,
) {
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
                        text = "Сервер Soll",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                PassiveChip(text = uiState.serverRouteStatus.label)
            }
            Text(
                text = uiState.serverStatusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.serverRouteStatus == GadgetRouteStatus.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            SollDevicePairingSection(
                uiState = uiState,
                onUpdateDeviceId = onUpdatePairingDeviceId,
                onUpdateSecret = onUpdatePairingSecret,
                onIssueToken = onIssueDeviceToken,
                onRefreshToken = onRefreshDeviceToken,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
                shape = MaterialTheme.shapes.small,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Контракт",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        PassiveChip(text = uiState.protocolRouteStatus.label)
                    }
                    Text(
                        text = uiState.protocolStatusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.protocolRouteStatus == GadgetRouteStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onRefresh, enabled = !uiState.isServerBusy) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Обновить")
                }
                OutlinedButton(onClick = onCheckProtocol, enabled = !uiState.isProtocolBusy) {
                    if (uiState.isProtocolBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Security, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Контракт")
                }
            }
            uiState.serverSnapshots.forEach { snapshot ->
                val selected = snapshot.id == uiState.selectedServerGadgetId
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(snapshot) },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
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
                            Text(snapshot.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                text = snapshot.lastTelemetryAt ?: "телеметрии пока нет",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PassiveChip(text = if (snapshot.stale) "устарел" else "онлайн")
                    }
                }
            }
            uiState.selectedServerSnapshot?.let { snapshot ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
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
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "Команды сервера",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = snapshot.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            uiState.lastServerCommand?.let { command ->
                                PassiveChip(text = command.status.serverCommandStatusLabel())
                            }
                        }
                        Text(
                            text = "Команда попадает в очередь Soll и будет забрана гаджетом по server relay.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                AquikDeviceProfile.COMMAND_GET_SENSORS,
                                AquikDeviceProfile.COMMAND_GET_ACTUATORS,
                                AquikDeviceProfile.COMMAND_GET_INFO,
                            ).forEach { command ->
                                OutlinedButton(
                                    onClick = { onSendCommand(command) },
                                    enabled = !uiState.isServerCommandBusy,
                                ) {
                                    if (uiState.isServerCommandBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.CloudSync, contentDescription = null)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(command.serverCommandButtonLabel())
                                }
                            }
                        }
                        if (uiState.serverCommands.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "История",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                uiState.serverCommands.take(5).forEach { command ->
                                    ServerCommandRow(
                                        command = command,
                                        isBusy = uiState.isServerCommandBusy,
                                        onExecuteManual = onExecuteManualCommand,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetDiscoveryContent(
    uiState: DevicesUiState,
    onBack: () -> Unit,
    onSelectMethod: (GadgetDiscoveryMethod) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUpdateManual: (String) -> Unit,
    onUpdateQr: (String) -> Unit,
    onAddCandidate: (GadgetDiscoveryCandidate) -> Unit,
    onUpdateProvisioningApHost: (String) -> Unit,
    onUpdateProvisioningSsid: (String) -> Unit,
    onUpdateProvisioningPassword: (String) -> Unit,
    onConfigureWifi: () -> Unit,
    showWifiPassword: Boolean,
    onToggleWifiPassword: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }
        Text(
            text = "Поиск устройства",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GadgetDiscoveryMethod.entries.forEach { method ->
                    AssistChip(
                        onClick = { onSelectMethod(method) },
                        label = { Text(method.shortTitle) },
                        leadingIcon = if (method == uiState.discoveryMethod) {
                            {
                                Icon(
                                    imageVector = if (method == GadgetDiscoveryMethod.WIFI_AP) Icons.Default.Wifi else Icons.Default.Search,
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
            Text(
                text = uiState.discoveryMethod.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (uiState.discoveryMethod) {
                GadgetDiscoveryMethod.MANUAL -> OutlinedTextField(
                    value = uiState.discoveryManualInput,
                    onValueChange = onUpdateManual,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("IP, host или ws:// URL") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                )
                GadgetDiscoveryMethod.QR -> OutlinedTextField(
                    value = uiState.discoveryQrInput,
                    onValueChange = onUpdateQr,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("QR/код устройства") },
                    minLines = 3,
                )
                else -> Unit
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onStart,
                    enabled = !uiState.isDiscoveryBusy && !uiState.discoveryMethod.planned,
                ) {
                    if (uiState.isDiscoveryBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.discoveryMethod == GadgetDiscoveryMethod.QR) "Импорт" else "Искать")
                }
                OutlinedButton(onClick = onStop, enabled = uiState.isDiscoveryBusy) {
                    Icon(Icons.Default.LinkOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Стоп")
                }
            }
        }
    }

    if (uiState.discoveryMethod == GadgetDiscoveryMethod.WIFI_AP) {
        GadgetApProvisioningCard(
            uiState = uiState,
            onUpdateProvisioningApHost = onUpdateProvisioningApHost,
            onUpdateProvisioningSsid = onUpdateProvisioningSsid,
            onUpdateProvisioningPassword = onUpdateProvisioningPassword,
            onConfigureWifi = onConfigureWifi,
            showWifiPassword = showWifiPassword,
            onToggleWifiPassword = onToggleWifiPassword,
        )
    }

    if (uiState.discoveryCandidates.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Найдено",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                uiState.discoveryCandidates.forEach { candidate ->
                    DiscoveryCandidateRow(candidate = candidate, onAdd = { onAddCandidate(candidate) })
                }
            }
        }
    }

    if (uiState.discoveryLogs.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Журнал поиска", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                uiState.discoveryLogs.forEach { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GadgetApProvisioningCard(
    uiState: DevicesUiState,
    onUpdateProvisioningApHost: (String) -> Unit,
    onUpdateProvisioningSsid: (String) -> Unit,
    onUpdateProvisioningPassword: (String) -> Unit,
    onConfigureWifi: () -> Unit,
    showWifiPassword: Boolean,
    onToggleWifiPassword: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Настройка AP",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Если телефон уже подключен к AP устройства, отправь ему параметры домашней Wi-Fi сети.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.provisioningApHostInput,
                onValueChange = onUpdateProvisioningApHost,
                label = { Text("Адрес AP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.provisioningSsidInput,
                onValueChange = onUpdateProvisioningSsid,
                label = { Text("SSID домашней Wi-Fi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.provisioningPasswordInput,
                onValueChange = onUpdateProvisioningPassword,
                label = { Text("Пароль Wi-Fi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showWifiPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleWifiPassword) {
                        Icon(
                            imageVector = if (showWifiPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Показать или скрыть пароль Wi-Fi",
                        )
                    }
                },
            )
            Button(onClick = onConfigureWifi, enabled = !uiState.isProvisioningBusy) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Отправить Wi-Fi")
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
}

@Composable
private fun DiscoveryCandidateRow(
    candidate: GadgetDiscoveryCandidate,
    onAdd: () -> Unit,
) {
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
                Text(candidate.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = candidate.endpointText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOfNotNull(candidate.chip, candidate.firmware, candidate.rssi?.let { "$it dBm" })
                        .joinToString(" · ")
                        .ifBlank { candidate.method.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAdd,
                enabled = candidate.canAdd || !candidate.apSsid.isNullOrBlank(),
            ) {
                Text(if (candidate.canAdd) "Добавить" else "Настроить")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetDeviceDetailContent(
    uiState: DevicesUiState,
    selectedProfile: DeviceProfile?,
    selectedDescriptor: GadgetProfileDescriptor?,
    showToken: Boolean,
    onToggleToken: () -> Unit,
    showConfig: Boolean,
    onToggleConfig: () -> Unit,
    onBack: () -> Unit,
    onSelectTab: (GadgetDeviceDetailTab) -> Unit,
    onUpdateHost: (String) -> Unit,
    onUpdatePort: (String) -> Unit,
    onUpdatePath: (String) -> Unit,
    onUpdateToken: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshSensors: () -> Unit,
    onRefreshInfo: () -> Unit,
    onRefreshConfig: () -> Unit,
    onRefreshActuators: () -> Unit,
    onSetAirPump: (Boolean) -> Unit,
    onSetWaterPump: (Boolean) -> Unit,
    onSetFan: (Boolean) -> Unit,
    onUpdateFullLed: (Float) -> Unit,
    onUpdateWhiteLed: (Float) -> Unit,
    onApplyFullLed: () -> Unit,
    onApplyWhiteLed: () -> Unit,
    onRefreshSchedules: () -> Unit,
    onRefreshAutomation: () -> Unit,
    onScanI2c: () -> Unit,
    onUpdateSettingsDeviceName: (String) -> Unit,
    onUpdateSettingsTimezone: (String) -> Unit,
    onUpdateSettingsSensorInterval: (String) -> Unit,
    onUpdateSettingsDisplayBrightness: (Float) -> Unit,
    onUpdateSettingsAutoMode: (Boolean) -> Unit,
    onApplySettings: () -> Unit,
    onUpdateCalibrationSensor: (String) -> Unit,
    onUpdateCalibrationOffset: (String) -> Unit,
    onUpdateCalibrationReference: (String) -> Unit,
    onApplyCalibration: () -> Unit,
    onUpdateScheduleId: (String) -> Unit,
    onUpdateScheduleName: (String) -> Unit,
    onUpdateScheduleType: (String) -> Unit,
    onUpdateScheduleTime: (String) -> Unit,
    onUpdateScheduleAction: (String) -> Unit,
    onUpdateScheduleEnabled: (Boolean) -> Unit,
    onSaveSchedule: () -> Unit,
    onDeleteSchedule: () -> Unit,
    onEditSchedule: (GadgetScheduleItem) -> Unit,
    onUpdateAutomationId: (String) -> Unit,
    onUpdateAutomationName: (String) -> Unit,
    onUpdateAutomationSensor: (String) -> Unit,
    onUpdateAutomationOperator: (String) -> Unit,
    onUpdateAutomationThreshold: (String) -> Unit,
    onUpdateAutomationAction: (String) -> Unit,
    onUpdateAutomationEnabled: (Boolean) -> Unit,
    onSaveAutomation: () -> Unit,
    onDeleteAutomation: () -> Unit,
    onEditAutomation: (GadgetAutomationRule) -> Unit,
) {
    val selectedDevice = uiState.knownDevices.firstOrNull { it.id == uiState.selectedDeviceId }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selectedDevice?.name ?: "Устройство",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = selectedDevice?.endpointUrl() ?: "выберите устройство",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DeviceStatusChip(uiState.connectionState.status)
    }

    selectedDescriptor?.let { GadgetProfileSummary(it) }

    GadgetConnectionDetailCard(
        uiState = uiState,
        showToken = showToken,
        onToggleToken = onToggleToken,
        onUpdateHost = onUpdateHost,
        onUpdatePort = onUpdatePort,
        onUpdatePath = onUpdatePath,
        onUpdateToken = onUpdateToken,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GadgetDeviceDetailTab.entries.forEach { tab ->
            AssistChip(
                onClick = { onSelectTab(tab) },
                label = { Text(tab.title) },
                leadingIcon = if (tab == uiState.selectedDetailTab) {
                    {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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

    when (uiState.selectedDetailTab) {
        GadgetDeviceDetailTab.SENSORS -> GadgetSensorsDetailCard(
            uiState = uiState,
            selectedProfile = selectedProfile,
            onRefreshSensors = onRefreshSensors,
            onRefreshInfo = onRefreshInfo,
            onRefreshConfig = onRefreshConfig,
        )
        GadgetDeviceDetailTab.CONTROL -> GadgetControlDetailCard(
            uiState = uiState,
            onRefreshActuators = onRefreshActuators,
            onSetAirPump = onSetAirPump,
            onSetWaterPump = onSetWaterPump,
            onSetFan = onSetFan,
            onUpdateFullLed = onUpdateFullLed,
            onUpdateWhiteLed = onUpdateWhiteLed,
            onApplyFullLed = onApplyFullLed,
            onApplyWhiteLed = onApplyWhiteLed,
        )
        GadgetDeviceDetailTab.PARAMETERS -> GadgetParametersDetailCard(
            uiState = uiState,
            showConfig = showConfig,
            onToggleConfig = onToggleConfig,
            onRefreshConfig = onRefreshConfig,
            onUpdateSettingsDeviceName = onUpdateSettingsDeviceName,
            onUpdateSettingsTimezone = onUpdateSettingsTimezone,
            onUpdateSettingsSensorInterval = onUpdateSettingsSensorInterval,
            onUpdateSettingsDisplayBrightness = onUpdateSettingsDisplayBrightness,
            onUpdateSettingsAutoMode = onUpdateSettingsAutoMode,
            onApplySettings = onApplySettings,
            onUpdateCalibrationSensor = onUpdateCalibrationSensor,
            onUpdateCalibrationOffset = onUpdateCalibrationOffset,
            onUpdateCalibrationReference = onUpdateCalibrationReference,
            onApplyCalibration = onApplyCalibration,
        )
        GadgetDeviceDetailTab.SCHEDULES -> GadgetSchedulesDetailCard(
            uiState = uiState,
            onRefreshSchedules = onRefreshSchedules,
            onUpdateScheduleId = onUpdateScheduleId,
            onUpdateScheduleName = onUpdateScheduleName,
            onUpdateScheduleType = onUpdateScheduleType,
            onUpdateScheduleTime = onUpdateScheduleTime,
            onUpdateScheduleAction = onUpdateScheduleAction,
            onUpdateScheduleEnabled = onUpdateScheduleEnabled,
            onSaveSchedule = onSaveSchedule,
            onDeleteSchedule = onDeleteSchedule,
            onEditSchedule = onEditSchedule,
        )
        GadgetDeviceDetailTab.AUTOMATION -> GadgetAutomationDetailCard(
            uiState = uiState,
            onRefreshAutomation = onRefreshAutomation,
            onUpdateAutomationId = onUpdateAutomationId,
            onUpdateAutomationName = onUpdateAutomationName,
            onUpdateAutomationSensor = onUpdateAutomationSensor,
            onUpdateAutomationOperator = onUpdateAutomationOperator,
            onUpdateAutomationThreshold = onUpdateAutomationThreshold,
            onUpdateAutomationAction = onUpdateAutomationAction,
            onUpdateAutomationEnabled = onUpdateAutomationEnabled,
            onSaveAutomation = onSaveAutomation,
            onDeleteAutomation = onDeleteAutomation,
            onEditAutomation = onEditAutomation,
        )
        GadgetDeviceDetailTab.DIAGNOSTICS -> GadgetDiagnosticsDetailCard(uiState, onScanI2c)
        GadgetDeviceDetailTab.EVENTS -> GadgetEventsDetailCard(uiState)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetConnectionDetailCard(
    uiState: DevicesUiState,
    showToken: Boolean,
    onToggleToken: () -> Unit,
    onUpdateHost: (String) -> Unit,
    onUpdatePort: (String) -> Unit,
    onUpdatePath: (String) -> Unit,
    onUpdateToken: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Подключение", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = uiState.hostInput,
                onValueChange = onUpdateHost,
                label = { Text("IP или host") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.portInput,
                    onValueChange = onUpdatePort,
                    label = { Text("Порт") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.pathInput,
                    onValueChange = onUpdatePath,
                    label = { Text("Путь") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = uiState.tokenInput,
                onValueChange = onUpdateToken,
                label = { Text("Токен") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleToken) {
                        Icon(
                            imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Показать или скрыть токен",
                        )
                    }
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onConnect, enabled = !uiState.isBusy) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Подключить")
                }
                OutlinedButton(onClick = onDisconnect) {
                    Icon(Icons.Default.LinkOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Отключить")
                }
            }
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetSensorsDetailCard(
    uiState: DevicesUiState,
    selectedProfile: DeviceProfile?,
    onRefreshSensors: () -> Unit,
    onRefreshInfo: () -> Unit,
    onRefreshConfig: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Датчики", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onRefreshSensors, enabled = !uiState.isBusy && selectedProfile.supports(AquikDeviceProfile.COMMAND_GET_SENSORS)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Обновить")
                }
                OutlinedButton(
                    onClick = onRefreshInfo,
                    enabled = !uiState.isBusy && selectedProfile.supportsAny(
                        AquikDeviceProfile.COMMAND_GET_INFO,
                        AquikDeviceProfile.COMMAND_GET_INFO_LEGACY,
                    ),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Инфо")
                }
                OutlinedButton(onClick = onRefreshConfig, enabled = !uiState.isBusy) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Конфиг")
                }
            }
            uiState.telemetry?.let { SensorValues(values = it.values) } ?: Text(
                text = "Нет данных датчиков",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.infoText.isNotBlank()) {
                Text(
                    text = uiState.infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetControlDetailCard(
    uiState: DevicesUiState,
    onRefreshActuators: () -> Unit,
    onSetAirPump: (Boolean) -> Unit,
    onSetWaterPump: (Boolean) -> Unit,
    onSetFan: (Boolean) -> Unit,
    onUpdateFullLed: (Float) -> Unit,
    onUpdateWhiteLed: (Float) -> Unit,
    onApplyFullLed: () -> Unit,
    onApplyWhiteLed: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Управление", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onRefreshActuators, enabled = !uiState.isBusy) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Состояние")
            }
            ActuatorSwitch("Воздушный насос", uiState.airPumpEnabled, !uiState.isBusy, onSetAirPump)
            ActuatorSwitch("Водяной насос", uiState.waterPumpEnabled, !uiState.isBusy, onSetWaterPump)
            ActuatorSwitch("Вентилятор", uiState.fanEnabled, !uiState.isBusy, onSetFan)
            LedSlider("Полный спектр", uiState.fullLedValue, !uiState.isBusy, onUpdateFullLed, onApplyFullLed)
            LedSlider("Белый LED", uiState.whiteLedValue, !uiState.isBusy, onUpdateWhiteLed, onApplyWhiteLed)
        }
    }
}

@Composable
private fun GadgetParametersDetailCard(
    uiState: DevicesUiState,
    showConfig: Boolean,
    onToggleConfig: () -> Unit,
    onRefreshConfig: () -> Unit,
    onUpdateSettingsDeviceName: (String) -> Unit,
    onUpdateSettingsTimezone: (String) -> Unit,
    onUpdateSettingsSensorInterval: (String) -> Unit,
    onUpdateSettingsDisplayBrightness: (Float) -> Unit,
    onUpdateSettingsAutoMode: (Boolean) -> Unit,
    onApplySettings: () -> Unit,
    onUpdateCalibrationSensor: (String) -> Unit,
    onUpdateCalibrationOffset: (String) -> Unit,
    onUpdateCalibrationReference: (String) -> Unit,
    onApplyCalibration: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Параметры устройства", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onRefreshConfig, enabled = !uiState.isBusy) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Загрузить")
            }
            GadgetSettingsEditor(
                uiState = uiState,
                onUpdateDeviceName = onUpdateSettingsDeviceName,
                onUpdateTimezone = onUpdateSettingsTimezone,
                onUpdateSensorInterval = onUpdateSettingsSensorInterval,
                onUpdateDisplayBrightness = onUpdateSettingsDisplayBrightness,
                onUpdateAutoMode = onUpdateSettingsAutoMode,
                onApply = onApplySettings,
            )
            GadgetCalibrationEditor(
                uiState = uiState,
                onUpdateSensor = onUpdateCalibrationSensor,
                onUpdateOffset = onUpdateCalibrationOffset,
                onUpdateReference = onUpdateCalibrationReference,
                onApply = onApplyCalibration,
            )
            if (uiState.configSummary.items.isNotEmpty()) {
                SummaryBlock(title = "Конфигурация", items = uiState.configSummary.items)
            }
            if (uiState.configText.isNotBlank()) {
                OutlinedButton(onClick = onToggleConfig) {
                    Text(if (showConfig) "Скрыть JSON" else "Показать JSON")
                }
                if (showConfig) {
                    Text(
                        text = uiState.configText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetSchedulesDetailCard(
    uiState: DevicesUiState,
    onRefreshSchedules: () -> Unit,
    onUpdateScheduleId: (String) -> Unit,
    onUpdateScheduleName: (String) -> Unit,
    onUpdateScheduleType: (String) -> Unit,
    onUpdateScheduleTime: (String) -> Unit,
    onUpdateScheduleAction: (String) -> Unit,
    onUpdateScheduleEnabled: (Boolean) -> Unit,
    onSaveSchedule: () -> Unit,
    onDeleteSchedule: () -> Unit,
    onEditSchedule: (GadgetScheduleItem) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Расписания", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onRefreshSchedules, enabled = !uiState.isBusy) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Обновить")
            }
            GadgetScheduleEditor(
                uiState = uiState,
                onUpdateId = onUpdateScheduleId,
                onUpdateName = onUpdateScheduleName,
                onUpdateType = onUpdateScheduleType,
                onUpdateTime = onUpdateScheduleTime,
                onUpdateAction = onUpdateScheduleAction,
                onUpdateEnabled = onUpdateScheduleEnabled,
                onSave = onSaveSchedule,
                onDelete = onDeleteSchedule,
            )
            if (uiState.scheduleSummary.items.isNotEmpty()) {
                ScheduleBlock(uiState.scheduleSummary.items, onEditSchedule)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetAutomationDetailCard(
    uiState: DevicesUiState,
    onRefreshAutomation: () -> Unit,
    onUpdateAutomationId: (String) -> Unit,
    onUpdateAutomationName: (String) -> Unit,
    onUpdateAutomationSensor: (String) -> Unit,
    onUpdateAutomationOperator: (String) -> Unit,
    onUpdateAutomationThreshold: (String) -> Unit,
    onUpdateAutomationAction: (String) -> Unit,
    onUpdateAutomationEnabled: (Boolean) -> Unit,
    onSaveAutomation: () -> Unit,
    onDeleteAutomation: () -> Unit,
    onEditAutomation: (GadgetAutomationRule) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Автоматизация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onRefreshAutomation, enabled = !uiState.isBusy) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Обновить")
            }
            GadgetAutomationEditor(
                uiState = uiState,
                onUpdateId = onUpdateAutomationId,
                onUpdateName = onUpdateAutomationName,
                onUpdateSensor = onUpdateAutomationSensor,
                onUpdateOperator = onUpdateAutomationOperator,
                onUpdateThreshold = onUpdateAutomationThreshold,
                onUpdateAction = onUpdateAutomationAction,
                onUpdateEnabled = onUpdateAutomationEnabled,
                onSave = onSaveAutomation,
                onDelete = onDeleteAutomation,
            )
            if (uiState.automationSummary.items.isNotEmpty()) {
                AutomationBlock(uiState.automationSummary.items, onEditAutomation)
            }
        }
    }
}

@Composable
private fun GadgetDiagnosticsDetailCard(
    uiState: DevicesUiState,
    onScanI2c: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Диагностика", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onScanI2c, enabled = !uiState.isBusy) {
                Icon(Icons.Default.Sensors, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("I2C")
            }
            if (uiState.diagnosticSummary.items.isNotEmpty()) {
                SummaryBlock(title = "Результат", items = uiState.diagnosticSummary.items)
            } else {
                Text(
                    text = "Диагностика появится после запроса к устройству.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GadgetEventsDetailCard(uiState: DevicesUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("События", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (uiState.events.isEmpty()) {
                Text(
                    text = "Событий по устройству пока нет.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                uiState.events.take(20).forEach { DeviceEventRow(it) }
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
    commands: List<GadgetCloudCommand>,
    meshStatus: SollMeshStatus?,
    meshRouteStatus: GadgetRouteStatus,
    meshStatusMessage: String,
    meshOutbox: List<SollMeshOutboxItem>,
    isBusy: Boolean,
    isMeshBusy: Boolean,
    isServerCommandBusy: Boolean,
    onRefresh: () -> Unit,
    onRefreshSelected: () -> Unit,
    onSelectSnapshot: (GadgetCloudSnapshot) -> Unit,
    onExecuteManualCommand: (String) -> Unit,
    onRefreshMesh: () -> Unit,
    onClaimNextMesh: () -> Unit,
    onAckMesh: (String) -> Unit,
    onRetryMesh: (String) -> Unit,
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

            if (commands.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Команды сервера",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    commands.take(6).forEach { command ->
                        ServerCommandRow(
                            command = command,
                            isBusy = isServerCommandBusy,
                            onExecuteManual = onExecuteManualCommand,
                        )
                    }
                }
            }

            MeshWorkerSection(
                status = meshStatus,
                routeStatus = meshRouteStatus,
                statusMessage = meshStatusMessage,
                outbox = meshOutbox,
                isBusy = isMeshBusy,
                onRefresh = onRefreshMesh,
                onClaimNext = onClaimNextMesh,
                onAck = onAckMesh,
                onRetry = onRetryMesh,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeshWorkerSection(
    status: SollMeshStatus?,
    routeStatus: GadgetRouteStatus,
    statusMessage: String,
    outbox: List<SollMeshOutboxItem>,
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onClaimNext: () -> Unit,
    onAck: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Mesh worker / outbox",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (routeStatus == GadgetRouteStatus.ERROR) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                PassiveChip(text = routeStatus.label)
            }

            status?.let {
                SummaryBlock(
                    title = "Mesh counters",
                    items = listOf(
                        GadgetKeyValue("Queued", it.queuedOutboxCount.toString()),
                        GadgetKeyValue("Sent", it.sentOutboxCount.toString()),
                        GadgetKeyValue("Acked", it.ackedOutboxCount.toString()),
                        GadgetKeyValue("Failed", it.failedOutboxCount.toString()),
                        GadgetKeyValue("Payload", "${it.maxPayloadBytes} bytes"),
                    ),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isBusy,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mesh status")
                }
                Button(
                    onClick = onClaimNext,
                    enabled = !isBusy,
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Claim next")
                }
            }

            if (outbox.isEmpty()) {
                Text(
                    text = "Mesh outbox пуст или ещё не загружен.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    outbox.take(6).forEach { item ->
                        MeshOutboxRow(
                            item = item,
                            isBusy = isBusy,
                            onAck = onAck,
                            onRetry = onRetry,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeshOutboxRow(
    item: SollMeshOutboxItem,
    isBusy: Boolean,
    onAck: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        text = "${item.outboundId.take(8)} -> ${item.toPeer}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "${item.text} · retry ${item.retryCount}/${item.maxRetries}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    item.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                PassiveChip(text = item.status)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onAck(item.outboundId) },
                    enabled = !isBusy && item.status == "sent",
                ) {
                    Text("ACK")
                }
                OutlinedButton(
                    onClick = { onRetry(item.outboundId) },
                    enabled = !isBusy && item.status == "failed",
                ) {
                    Text("Retry")
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

@Composable
private fun ServerCommandRow(
    command: GadgetCloudCommand,
    isBusy: Boolean,
    onExecuteManual: (String) -> Unit,
) {
    var confirmManual by remember(command.id) { mutableStateOf(false) }
    if (confirmManual) {
        AlertDialog(
            onDismissRequest = { confirmManual = false },
            title = { Text("Подтвердить ручной запуск") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Команда: ${command.command}")
                    Text("Гаджет: ${command.gadgetId}")
                    Text("Параметры: ${command.params.ifEmpty { mapOf("empty" to true) }}")
                    Text(
                        text = "Запуск пойдет на локальный ESP/WebSocket. Background worker эту команду не выполняет.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isBusy,
                    onClick = {
                        confirmManual = false
                        onExecuteManual(command.id)
                    },
                ) {
                    Text("Выполнить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmManual = false }) {
                    Text("Отмена")
                }
            },
        )
    }
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
                    text = command.command.serverCommandButtonLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = command.commandSummaryLine(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PassiveChip(text = command.status.serverCommandStatusLabel())
                if (command.status == "manual_ready") {
                    OutlinedButton(
                        onClick = { confirmManual = true },
                        enabled = !isBusy,
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Security, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Вручную")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetServiceCard(
    uiState: DevicesUiState,
    onRefreshConfig: () -> Unit,
    onRefreshSchedules: () -> Unit,
    onRefreshAutomation: () -> Unit,
    onScanI2c: () -> Unit,
    onUpdateSettingsDeviceName: (String) -> Unit,
    onUpdateSettingsTimezone: (String) -> Unit,
    onUpdateSettingsSensorInterval: (String) -> Unit,
    onUpdateSettingsDisplayBrightness: (Float) -> Unit,
    onUpdateSettingsAutoMode: (Boolean) -> Unit,
    onApplySettings: () -> Unit,
    onUpdateCalibrationSensor: (String) -> Unit,
    onUpdateCalibrationOffset: (String) -> Unit,
    onUpdateCalibrationReference: (String) -> Unit,
    onApplyCalibration: () -> Unit,
    onUpdateScheduleId: (String) -> Unit,
    onUpdateScheduleName: (String) -> Unit,
    onUpdateScheduleType: (String) -> Unit,
    onUpdateScheduleTime: (String) -> Unit,
    onUpdateScheduleAction: (String) -> Unit,
    onUpdateScheduleEnabled: (Boolean) -> Unit,
    onSaveSchedule: () -> Unit,
    onDeleteSchedule: () -> Unit,
    onEditSchedule: (GadgetScheduleItem) -> Unit,
    onUpdateAutomationId: (String) -> Unit,
    onUpdateAutomationName: (String) -> Unit,
    onUpdateAutomationSensor: (String) -> Unit,
    onUpdateAutomationOperator: (String) -> Unit,
    onUpdateAutomationThreshold: (String) -> Unit,
    onUpdateAutomationAction: (String) -> Unit,
    onUpdateAutomationEnabled: (Boolean) -> Unit,
    onSaveAutomation: () -> Unit,
    onDeleteAutomation: () -> Unit,
    onEditAutomation: (GadgetAutomationRule) -> Unit,
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
                    enabled = !uiState.isBusy,
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Конфиг")
                }
                OutlinedButton(
                    onClick = onRefreshSchedules,
                    enabled = !uiState.isBusy,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Расписания")
                }
                OutlinedButton(
                    onClick = onRefreshAutomation,
                    enabled = !uiState.isBusy,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Автоматика")
                }
                OutlinedButton(
                    onClick = onScanI2c,
                    enabled = !uiState.isBusy,
                ) {
                    Icon(Icons.Default.Sensors, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I2C")
                }
            }

            GadgetSettingsEditor(
                uiState = uiState,
                onUpdateDeviceName = onUpdateSettingsDeviceName,
                onUpdateTimezone = onUpdateSettingsTimezone,
                onUpdateSensorInterval = onUpdateSettingsSensorInterval,
                onUpdateDisplayBrightness = onUpdateSettingsDisplayBrightness,
                onUpdateAutoMode = onUpdateSettingsAutoMode,
                onApply = onApplySettings,
            )
            GadgetCalibrationEditor(
                uiState = uiState,
                onUpdateSensor = onUpdateCalibrationSensor,
                onUpdateOffset = onUpdateCalibrationOffset,
                onUpdateReference = onUpdateCalibrationReference,
                onApply = onApplyCalibration,
            )
            GadgetScheduleEditor(
                uiState = uiState,
                onUpdateId = onUpdateScheduleId,
                onUpdateName = onUpdateScheduleName,
                onUpdateType = onUpdateScheduleType,
                onUpdateTime = onUpdateScheduleTime,
                onUpdateAction = onUpdateScheduleAction,
                onUpdateEnabled = onUpdateScheduleEnabled,
                onSave = onSaveSchedule,
                onDelete = onDeleteSchedule,
            )
            GadgetAutomationEditor(
                uiState = uiState,
                onUpdateId = onUpdateAutomationId,
                onUpdateName = onUpdateAutomationName,
                onUpdateSensor = onUpdateAutomationSensor,
                onUpdateOperator = onUpdateAutomationOperator,
                onUpdateThreshold = onUpdateAutomationThreshold,
                onUpdateAction = onUpdateAutomationAction,
                onUpdateEnabled = onUpdateAutomationEnabled,
                onSave = onSaveAutomation,
                onDelete = onDeleteAutomation,
            )

            if (uiState.configSummary.items.isNotEmpty()) {
                SummaryBlock(title = "Конфигурация", items = uiState.configSummary.items)
            }
            if (uiState.scheduleSummary.items.isNotEmpty()) {
                ScheduleBlock(uiState.scheduleSummary.items, onEditSchedule)
            }
            if (uiState.automationSummary.items.isNotEmpty()) {
                AutomationBlock(uiState.automationSummary.items, onEditAutomation)
            }
            if (uiState.diagnosticSummary.items.isNotEmpty()) {
                SummaryBlock(title = "Диагностика", items = uiState.diagnosticSummary.items)
            }
            if (
                uiState.configSummary.items.isEmpty() &&
                uiState.scheduleSummary.items.isEmpty() &&
                uiState.automationSummary.items.isEmpty() &&
                uiState.diagnosticSummary.items.isEmpty()
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
private fun GadgetSettingsEditor(
    uiState: DevicesUiState,
    onUpdateDeviceName: (String) -> Unit,
    onUpdateTimezone: (String) -> Unit,
    onUpdateSensorInterval: (String) -> Unit,
    onUpdateDisplayBrightness: (Float) -> Unit,
    onUpdateAutoMode: (Boolean) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = uiState.settingsDeviceNameInput,
            onValueChange = onUpdateDeviceName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Имя гаджета") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.settingsTimezoneInput,
                onValueChange = onUpdateTimezone,
                modifier = Modifier.weight(1f),
                label = { Text("Часовой пояс") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.settingsSensorIntervalInput,
                onValueChange = onUpdateSensorInterval,
                modifier = Modifier.weight(1f),
                label = { Text("Датчики, мс") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
        Text(
            text = "Яркость дисплея: ${uiState.settingsDisplayBrightness.toInt()}/255",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = uiState.settingsDisplayBrightness,
            onValueChange = onUpdateDisplayBrightness,
            valueRange = 0f..255f,
            steps = 254,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Авто-режим", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = uiState.settingsAutoMode, onCheckedChange = onUpdateAutoMode)
        }
        Button(
            onClick = onApply,
            enabled = !uiState.isBusy,
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Сохранить настройки")
        }
    }
}

@Composable
private fun GadgetCalibrationEditor(
    uiState: DevicesUiState,
    onUpdateSensor: (String) -> Unit,
    onUpdateOffset: (String) -> Unit,
    onUpdateReference: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Калибровка датчика",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.calibrationSensorInput,
                onValueChange = onUpdateSensor,
                modifier = Modifier.weight(1f),
                label = { Text("Датчик") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.calibrationOffsetInput,
                onValueChange = onUpdateOffset,
                modifier = Modifier.weight(1f),
                label = { Text("Смещение") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = uiState.calibrationReferenceInput,
            onValueChange = onUpdateReference,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Эталонное значение") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        OutlinedButton(
            onClick = onApply,
            enabled = !uiState.isBusy,
        ) {
            Icon(Icons.Default.Sensors, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Отправить калибровку")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetScheduleEditor(
    uiState: DevicesUiState,
    onUpdateId: (String) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateType: (String) -> Unit,
    onUpdateTime: (String) -> Unit,
    onUpdateAction: (String) -> Unit,
    onUpdateEnabled: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Редактор расписания",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.scheduleIdInput,
                onValueChange = onUpdateId,
                modifier = Modifier.weight(0.8f),
                label = { Text("ID") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.scheduleTimeInput,
                onValueChange = onUpdateTime,
                modifier = Modifier.weight(1f),
                label = { Text("Время") },
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = uiState.scheduleNameInput,
            onValueChange = onUpdateName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Название") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.scheduleTypeInput,
                onValueChange = onUpdateType,
                modifier = Modifier.weight(1f),
                label = { Text("Тип") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.scheduleActionInput,
                onValueChange = onUpdateAction,
                modifier = Modifier.weight(1f),
                label = { Text("Действие") },
                singleLine = true,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Активно", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = uiState.scheduleEnabled, onCheckedChange = onUpdateEnabled)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSave, enabled = !uiState.isBusy) {
                Text("Сохранить расписание")
            }
            OutlinedButton(onClick = onDelete, enabled = !uiState.isBusy && uiState.scheduleIdInput.isNotBlank()) {
                Text("Удалить")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GadgetAutomationEditor(
    uiState: DevicesUiState,
    onUpdateId: (String) -> Unit,
    onUpdateName: (String) -> Unit,
    onUpdateSensor: (String) -> Unit,
    onUpdateOperator: (String) -> Unit,
    onUpdateThreshold: (String) -> Unit,
    onUpdateAction: (String) -> Unit,
    onUpdateEnabled: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Редактор автоматизации",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.automationIdInput,
                onValueChange = onUpdateId,
                modifier = Modifier.weight(0.8f),
                label = { Text("ID") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.automationNameInput,
                onValueChange = onUpdateName,
                modifier = Modifier.weight(1.2f),
                label = { Text("Название") },
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = uiState.automationSensorInput,
                onValueChange = onUpdateSensor,
                modifier = Modifier.weight(1f),
                label = { Text("Датчик") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.automationOperatorInput,
                onValueChange = onUpdateOperator,
                modifier = Modifier.weight(0.6f),
                label = { Text("Условие") },
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.automationThresholdInput,
                onValueChange = onUpdateThreshold,
                modifier = Modifier.weight(0.9f),
                label = { Text("Порог") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
            )
        }
        OutlinedTextField(
            value = uiState.automationActionInput,
            onValueChange = onUpdateAction,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Действие") },
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Активна", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = uiState.automationEnabled, onCheckedChange = onUpdateEnabled)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onSave, enabled = !uiState.isBusy) {
                Text("Сохранить автоматизацию")
            }
            OutlinedButton(onClick = onDelete, enabled = !uiState.isBusy && uiState.automationIdInput.isNotBlank()) {
                Text("Удалить")
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
private fun ScheduleBlock(
    items: List<GadgetScheduleItem>,
    onEdit: (GadgetScheduleItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Расписания",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        items.forEach { item ->
            ScheduleRow(item, onEdit)
        }
    }
}

@Composable
private fun ScheduleRow(
    item: GadgetScheduleItem,
    onEdit: (GadgetScheduleItem) -> Unit,
) {
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
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PassiveChip(
                    text = if (item.enabled) "вкл" else "выкл",
                    containerColor = if (item.enabled) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                )
                OutlinedButton(onClick = { onEdit(item) }) {
                    Text("Править")
                }
            }
        }
    }
}

@Composable
private fun AutomationBlock(
    items: List<GadgetAutomationRule>,
    onEdit: (GadgetAutomationRule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Автоматизации",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        items.forEach { item ->
            AutomationRow(item, onEdit)
        }
    }
}

@Composable
private fun AutomationRow(
    item: GadgetAutomationRule,
    onEdit: (GadgetAutomationRule) -> Unit,
) {
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
                    text = "${item.sensorKey} ${item.operator} ${item.threshold} -> ${item.action}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PassiveChip(
                    text = if (item.enabled) "вкл" else "выкл",
                    containerColor = if (item.enabled) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    },
                )
                OutlinedButton(onClick = { onEdit(item) }) {
                    Text("Править")
                }
            }
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

private fun String.serverCommandButtonLabel(): String =
    when (this) {
        AquikDeviceProfile.COMMAND_GET_SENSORS -> "Датчики"
        AquikDeviceProfile.COMMAND_GET_ACTUATORS -> "Актуаторы"
        AquikDeviceProfile.COMMAND_GET_INFO,
        AquikDeviceProfile.COMMAND_GET_INFO_LEGACY -> "Инфо"
        else -> this
    }

private fun String.serverCommandStatusLabel(): String =
    when (this) {
        "pending" -> "в очереди"
        "done" -> "готово"
        "disabled" -> "выкл"
        "approval_required" -> "подтв."
        "manual_ready" -> "ручной"
        "unsupported" -> "не поддерж."
        "failed" -> "ошибка"
        else -> this
    }

private fun GadgetCloudCommand.commandSummaryLine(): String {
    val time = completedAt ?: createdAt
    val details = when {
        reason.isNotBlank() -> reason
        approvalId.isNotBlank() -> "Подтверждение: $approvalId"
        result.isNotEmpty() -> "Ответ: ${result.keys.joinToString()}"
        params.isNotEmpty() -> "Параметры: ${params.keys.joinToString()}"
        else -> "Без данных ответа"
    }
    return listOf(time, details).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun String.statusText(): String =
    runCatching { DeviceConnectionStatus.valueOf(this).label() }.getOrDefault(this)

private fun Context.hasWifiDiscoveryPermissions(): Boolean =
    requiredWifiDiscoveryPermissions().all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

private fun requiredWifiDiscoveryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

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
