package com.soll.presentation.screens.tools.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.data.local.entity.ScanItemEntity
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.soll.domain.soll.SollTask
import com.soll.domain.scanner.ScannerDuplicatePolicy
import com.soll.domain.scanner.ScannerSettings
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import timber.log.Timber

private const val PAIRING_CAMERA_PROMPT = "Наведи камеру на QR pairing в Desktop"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onPairingCompleted: () -> Unit = onBack,
    autoStartCamera: Boolean = false,
    pairingMode: Boolean = false,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            viewModel.setCameraEnabled(
                enabled = true,
                requireScannerCapability = !pairingMode,
                cameraStatus = if (pairingMode) PAIRING_CAMERA_PROMPT else null,
            )
        } else {
            viewModel.showCameraPermissionDenied()
        }
    }
    var autoStartRequested by remember { mutableStateOf(false) }

    LaunchedEffect(pairingMode, uiState.pairingCompleted) {
        if (pairingMode && uiState.pairingCompleted) {
            delay(900)
            onPairingCompleted()
        }
    }

    LaunchedEffect(autoStartCamera, pairingMode, hasCameraPermission, uiState.cameraEnabled) {
        if (!autoStartCamera || autoStartRequested || uiState.cameraEnabled) return@LaunchedEffect
        autoStartRequested = true
        if (hasCameraPermission) {
            viewModel.setCameraEnabled(
                enabled = true,
                requireScannerCapability = !pairingMode,
                cameraStatus = if (pairingMode) PAIRING_CAMERA_PROMPT else null,
            )
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (pairingMode) "QR pairing" else "Сканер") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (!pairingMode) {
                        IconButton(onClick = viewModel::toggleSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки сканера")
                        }
                        IconButton(onClick = viewModel::selectAll, enabled = uiState.items.isNotEmpty()) {
                            Icon(Icons.Default.Checklist, contentDescription = "Выбрать все")
                        }
                        IconButton(onClick = viewModel::clearSelection, enabled = uiState.selectedIds.isNotEmpty()) {
                            Icon(Icons.Default.Clear, contentDescription = "Снять выбор")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isExporting || uiState.isActionRunning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.message?.let { message ->
                AssistChip(
                    onClick = viewModel::clearMessage,
                    label = { Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
            }

            if (pairingMode) {
                PairingCameraPanel(
                    cameraEnabled = uiState.cameraEnabled,
                    cameraStatus = uiState.cameraStatus,
                    hasCameraPermission = hasCameraPermission,
                    onEnableCamera = {
                        if (hasCameraPermission) {
                            viewModel.setCameraEnabled(
                                enabled = true,
                                requireScannerCapability = false,
                                cameraStatus = PAIRING_CAMERA_PROMPT,
                            )
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onBarcodeDetected = { rawValue, format ->
                        viewModel.handleCameraBarcode(rawValue, format, pairingOnly = true)
                    },
                )
            } else {
                if (uiState.showSettings) {
                    ScannerSettingsPanel(
                        settings = uiState.settings,
                        onSettingsChange = viewModel::updateScannerSettings,
                    )
                }

                CameraScanCard(
                    cameraEnabled = uiState.cameraEnabled,
                    cameraStatus = uiState.cameraStatus,
                    hasCameraPermission = hasCameraPermission,
                    onEnableCamera = {
                        if (viewModel.ensureScannerCapability()) {
                            if (hasCameraPermission) {
                                viewModel.setCameraEnabled(true)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    onDisableCamera = { viewModel.setCameraEnabled(false) },
                    onBarcodeDetected = { rawValue, format -> viewModel.handleCameraBarcode(rawValue, format) },
                )

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Штрихкод",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        OutlinedTextField(
                            value = uiState.input,
                            onValueChange = viewModel::updateInput,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("EAN, QR или код") },
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = viewModel::addManualScan,
                                enabled = uiState.input.isNotBlank(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Добавить")
                            }
                            OutlinedButton(
                                onClick = viewModel::exportSelected,
                                enabled = uiState.selectedIds.isNotEmpty() && !uiState.isExporting,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("В raw")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(onClick = viewModel::selectAll, label = { Text("Сканы: ${uiState.items.size}") })
                    AssistChip(onClick = viewModel::clearSelection, label = { Text("Выбрано: ${uiState.selectedIds.size}") })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::toggleTaskPicker,
                        enabled = uiState.selectedIds.isNotEmpty() && !uiState.isActionRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("К задаче")
                    }
                    OutlinedButton(
                        onClick = viewModel::pairSelectedDevice,
                        enabled = uiState.selectedIds.isNotEmpty() && !uiState.isActionRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Гаджет")
                    }
                }

                if (uiState.showTaskPicker) {
                    TaskPickerPanel(
                        tasks = uiState.taskCandidates,
                        isBusy = uiState.isActionRunning,
                        onAttach = viewModel::attachSelectedToTask,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.items, key = { it.id }) { item ->
                        ScanItemRow(
                            item = item,
                            selected = item.id in uiState.selectedIds,
                            onToggle = { viewModel.toggleSelected(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingCameraPanel(
    cameraEnabled: Boolean,
    cameraStatus: String?,
    hasCameraPermission: Boolean,
    onEnableCamera: () -> Unit,
    onBarcodeDetected: (rawValue: String, format: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сканировать QR",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = cameraStatus ?: PAIRING_CAMERA_PROMPT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (cameraEnabled && hasCameraPermission) {
                CameraBarcodePreview(
                    onBarcodeDetected = onBarcodeDetected,
                    qrOnly = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (hasCameraPermission) {
                Button(onClick = onEnableCamera, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть камеру")
                }
            } else {
                Text(
                    text = "Для сканирования нужно разрешение камеры.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onEnableCamera, modifier = Modifier.fillMaxWidth()) {
                    Text("Разрешить камеру")
                }
            }
        }
    }
}

@Composable
private fun TaskPickerPanel(
    tasks: List<SollTask>,
    isBusy: Boolean,
    onAttach: (SollTask) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Прикрепить к задаче",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (tasks.isEmpty()) {
                Text(
                    text = "Открытых задач в кеше нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tasks.forEach { task ->
                    OutlinedButton(
                        onClick = { onAttach(task) },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                        ) {
                            Text(
                                text = task.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = task.projectName?.takeIf { it.isNotBlank() } ?: task.status,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerSettingsPanel(
    settings: ScannerSettings,
    onSettingsChange: (ScannerSettings) -> Unit,
) {
    val ignoreDuplicates = settings.duplicatePolicy == ScannerDuplicatePolicy.IGNORE_EXISTING
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Игнорировать дубли",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (ignoreDuplicates) {
                            "Повторный код остается в истории без роста счетчика."
                        } else {
                            "Повторный код увеличивает счетчик xN и обновляет время."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ignoreDuplicates,
                    onCheckedChange = { enabled ->
                        onSettingsChange(
                            settings.copy(
                                duplicatePolicy = if (enabled) {
                                    ScannerDuplicatePolicy.IGNORE_EXISTING
                                } else {
                                    ScannerDuplicatePolicy.COUNT_REPEATS
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CameraScanCard(
    cameraEnabled: Boolean,
    cameraStatus: String?,
    hasCameraPermission: Boolean,
    onEnableCamera: () -> Unit,
    onDisableCamera: () -> Unit,
    onBarcodeDetected: (rawValue: String, format: String) -> Unit,
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
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Камера",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = cameraStatus ?: "Камера выключена",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (cameraEnabled) {
                    OutlinedButton(onClick = onDisableCamera) {
                        Text("Выкл.")
                    }
                } else {
                    Button(onClick = onEnableCamera) {
                        Text("Камера")
                    }
                }
            }

            if (cameraEnabled) {
                if (hasCameraPermission) {
                    CameraBarcodePreview(onBarcodeDetected = onBarcodeDetected)
                } else {
                    Text(
                        text = "Для сканирования нужно разрешение камеры.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraBarcodePreview(
    onBarcodeDetected: (rawValue: String, format: String) -> Unit,
    qrOnly: Boolean = false,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(260.dp),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val analyzerExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val barcodeScanner = BarcodeScanning.getClient(
            if (qrOnly) qrBarcodeScannerOptions() else barcodeScannerOptions(),
        )
        var cameraProvider: ProcessCameraProvider? = null
        var disposed = false

        cameraProviderFuture.addListener(
            {
                runCatching {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider
                    if (disposed) {
                        provider.unbindAll()
                        return@runCatching
                    }

                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(
                                analyzerExecutor,
                                MlKitBarcodeAnalyzer(
                                    scanner = barcodeScanner,
                                    onBarcodeDetected = { rawValue, format ->
                                        currentOnBarcodeDetected(rawValue, format)
                                    },
                                ),
                            )
                        }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure {
                    Timber.w(it, "Не удалось запустить камеру сканера")
                }
            },
            mainExecutor,
        )

        onDispose {
            disposed = true
            runCatching { cameraProvider?.unbindAll() }
            analyzerExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

private class MlKitBarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val onBarcodeDetected: (rawValue: String, format: String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val processing = AtomicBoolean(false)

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.firstOrNull { it.decodedRawValue().isNotBlank() }
                if (barcode != null) {
                    val value = barcode.decodedRawValue()
                    if (value.isBlank()) return@addOnSuccessListener
                    onBarcodeDetected(
                        value,
                        barcode.format.toScannerFormat(),
                    )
                }
            }
            .addOnFailureListener {
                Timber.w(it, "Не удалось распознать штрихкод")
            }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }
}

private fun Barcode.decodedRawValue(): String {
    rawValue?.takeIf { it.isNotBlank() }?.let { return it }
    val bytes = rawBytes
    return if (bytes != null && bytes.isNotEmpty()) {
        bytes.toString(Charsets.UTF_8)
    } else {
        ""
    }
}

@Composable
private fun ScanItemRow(
    item: ScanItemEntity,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.normalizedValue,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.format} · ${formatTime(item.lastScannedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.count > 1) {
                AssistChip(onClick = onToggle, label = { Text("x${item.count}") })
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

private fun barcodeScannerOptions(): BarcodeScannerOptions =
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
        )
        .enableAllPotentialBarcodes()
        .build()

private fun qrBarcodeScannerOptions(): BarcodeScannerOptions =
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAllPotentialBarcodes()
        .build()

private fun Int.toScannerFormat(): String =
    when (this) {
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_PDF417 -> "PDF_417"
        else -> "UNKNOWN"
    }
