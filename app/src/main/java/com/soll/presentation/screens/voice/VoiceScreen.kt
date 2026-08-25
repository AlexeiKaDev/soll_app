package com.soll.presentation.screens.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.soll.domain.voice.SttRecognitionMode
import com.soll.domain.voice.VoiceCommandSessionStatus
import com.soll.ui.components.PassiveChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    viewModel: VoiceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var microphoneGranted by remember {
        mutableStateOf(context.hasRecordAudioPermission())
    }
    var bluetoothPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val microphoneNowGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            context.hasRecordAudioPermission()
        microphoneGranted = microphoneNowGranted
        if (microphoneNowGranted) {
            viewModel.onMicrophonePermissionGranted()
        } else {
            val permanentlyDenied = permissionRequested &&
                activity?.let {
                    !ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.RECORD_AUDIO,
                    )
                } == true
            viewModel.onMicrophonePermissionDenied(permanentlyDenied)
        }
    }

    val voiceInputReady = microphoneGranted && (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.hasBluetoothConnectPermission() ||
            bluetoothPermissionRequested
        )

    DisposableEffect(lifecycleOwner, context) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onScreenStarted()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenStarted()
                Lifecycle.Event.ON_RESUME -> {
                    val granted = context.hasRecordAudioPermission()
                    if (granted && !microphoneGranted) {
                        viewModel.onMicrophonePermissionGranted()
                    }
                    microphoneGranted = granted
                }

                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenStopped()
        }
    }

    val requestVoicePermissions = {
        permissionRequested = true
        bluetoothPermissionRequested = true
        permissionLauncher.launch(context.missingVoiceInputPermissions().toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Голос Soll") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.onScreenStopped()
                            onBack()
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VoiceStatus(uiState)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Микрофон активен только пока экран открыт и вы удерживаете кнопку. Максимум 30 секунд.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    PushToTalkButton(
                        listening = uiState.isListening,
                        enabled = uiState.isAvailable && !uiState.isProcessing,
                        voiceInputReady = voiceInputReady,
                        onPermissionRequired = requestVoicePermissions,
                        onPress = viewModel::startListening,
                        onRelease = viewModel::finishListening,
                        onCancel = viewModel::cancelListening,
                    )

                    Text(
                        text = if (uiState.isListening) {
                            "Говорите. Отпустите для отправки."
                        } else {
                            "Нажмите и удерживайте, чтобы говорить"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.isListening) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )

                    if (uiState.isListening) {
                        OutlinedButton(
                            onClick = viewModel::cancelListening,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Отменить без отправки")
                        }
                    }

                    if (uiState.isProcessing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text("Получаю безопасный ответ Soll")
                        }
                    }

                    uiState.permissionMessage?.let { message ->
                        PermissionMessage(
                            message = message,
                            permanentlyDenied = uiState.permissionPermanentlyDenied,
                            onRequest = requestVoicePermissions,
                            onOpenSettings = { context.openAppPermissionSettings() },
                        )
                    }

                    VoiceTextBlock(
                        title = "Распознано",
                        text = uiState.partialText.ifBlank { uiState.recognizedText },
                    )

                    VoiceTextBlock(
                        title = "Ответ",
                        text = uiState.responseText,
                    )

                    VoicePlaybackControls(uiState = uiState, viewModel = viewModel)

                    uiState.activationHint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    uiState.errorMessage?.let { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PushToTalkButton(
    listening: Boolean,
    enabled: Boolean,
    voiceInputReady: Boolean,
    onPermissionRequired: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(112.dp)
            .semantics {
                role = Role.Button
                contentDescription = "Удерживайте для разговора с Soll"
                if (!enabled) disabled()
            }
            .pointerInput(enabled, voiceInputReady) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        if (!voiceInputReady) {
                            onPermissionRequired()
                            return@detectTapGestures
                        }
                        onPress()
                        if (tryAwaitRelease()) onRelease() else onCancel()
                    }
                )
            },
        shape = CircleShape,
        color = if (listening) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        },
        contentColor = if (listening) {
            MaterialTheme.colorScheme.onError
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        shadowElevation = 4.dp,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
            Text(if (listening) "Слушаю" else "Удерживать")
        }
    }
}

@Composable
private fun PermissionMessage(
    message: String,
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = if (permanentlyDenied) onOpenSettings else onRequest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (permanentlyDenied) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (permanentlyDenied) "Открыть настройки" else "Разрешить микрофон")
            }
        }
    }
}

@Composable
private fun VoicePlaybackControls(uiState: VoiceUiState, viewModel: VoiceViewModel) {
    if (uiState.responseText.isBlank()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.isSpeaking) {
            OutlinedButton(
                onClick = viewModel::stopSpeaking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Остановить озвучивание")
            }
        } else if (!uiState.isMuted) {
            OutlinedButton(
                onClick = viewModel::speakResponse,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Озвучить снова")
            }
        }
        OutlinedButton(
            onClick = viewModel::toggleMute,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (uiState.isMuted) {
                    Icons.AutoMirrored.Filled.VolumeUp
                } else {
                    Icons.AutoMirrored.Filled.VolumeOff
                },
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (uiState.isMuted) "Включить автоозвучивание" else "Выключить звук")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceStatus(uiState: VoiceUiState) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PassiveChip(
            text = when {
                !uiState.isAvailable -> "STT недоступен"
                uiState.isListening -> "PTT: слушаю"
                uiState.isProcessing -> "Ответ Soll"
                uiState.session != null -> uiState.session.status.label()
                else -> "Готов"
            },
        )
        PassiveChip(
            text = when {
                uiState.activeSttMode == SttRecognitionMode.ON_DEVICE -> "STT на устройстве"
                uiState.preferOffline -> "Офлайн запрошен"
                else -> "Системное STT"
            },
        )
        PassiveChip(text = "Только на экране")
        if (uiState.isMuted) PassiveChip(text = "Без звука")
        if (uiState.wakePhraseRequired) PassiveChip(text = "Фраза «Солл»")
    }
}

private fun VoiceCommandSessionStatus.label(): String = when (this) {
    VoiceCommandSessionStatus.LISTENING -> "Слушаю"
    VoiceCommandSessionStatus.PROCESSING -> "Запрос"
    VoiceCommandSessionStatus.COMPLETED -> "Готово"
    VoiceCommandSessionStatus.FAILED -> "Ошибка"
    VoiceCommandSessionStatus.CANCELLED -> "Отменено"
}

@Composable
private fun VoiceTextBlock(title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = text.ifBlank { "..." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.hasBluetoothConnectPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.missingVoiceInputPermissions(): List<String> = buildList {
    if (!hasRecordAudioPermission()) add(Manifest.permission.RECORD_AUDIO)
    if (!hasBluetoothConnectPermission()) add(Manifest.permission.BLUETOOTH_CONNECT)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppPermissionSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
