package com.soll.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.soll.data.repository.GadgetServerSyncScheduler
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollServerSyncScheduler
import com.soll.data.repository.SollSyncQueueRepository
import com.soll.data.service.AndroidPushTokenRegistrar
import com.soll.domain.soll.SollPairingPayload
import com.soll.domain.soll.SollPairingPayloadParser
import com.soll.presentation.navigation.AppLaunchCommand
import com.soll.presentation.navigation.AppLaunchTargets
import com.soll.presentation.navigation.AppNavigation
import com.soll.presentation.navigation.SharedLinkParser
import com.soll.ui.theme.SollTheme
import com.soll.ui.theme.SollThemeVariant
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.UUID
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository
    @Inject
    lateinit var syncQueueRepository: SollSyncQueueRepository

    private var launchCommand by mutableStateOf<AppLaunchCommand?>(null)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            AndroidPushTokenRegistrar.registerCurrentToken(
                applicationContext,
                reason = "notification_permission_granted",
                force = true,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recordNotificationOpened(intent)
        launchCommand = intent?.toLaunchCommand()
        requestNotificationPermissionIfNeeded()

        setContent {
            val themeVariantKey by settingsRepository.appThemeVariantFlow.collectAsState(
                initial = settingsRepository.appThemeVariant,
            )
            SollTheme(variant = SollThemeVariant.fromStorage(themeVariantKey)) {
                AppNavigation(
                    modifier = Modifier.fillMaxSize(),
                    launchCommand = launchCommand,
                    onLaunchCommandConsumed = { launchCommand = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recordNotificationOpened(intent)
        launchCommand = intent.toLaunchCommand()
    }

    private fun recordNotificationOpened(intent: Intent?) {
        val eventId = intent
            ?.getStringExtra(AppLaunchTargets.EXTRA_NOTIFICATION_EVENT_ID)
            ?.trim()
            ?.take(200)
            ?.takeIf { it.isNotBlank() }
            ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                syncQueueRepository.enqueueNotificationReceipt(
                    eventId = eventId,
                    state = "opened",
                    occurredAt = Instant.now().toString(),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Opening the requested destination must not be blocked by receipt persistence failure.
            }
        }
    }

    private fun Intent.toLaunchCommand(): AppLaunchCommand? {
        if (action == Intent.ACTION_SEND && type.equals("text/plain", ignoreCase = true)) {
            val clientId = getStringExtra(EXTRA_SHARE_CLIENT_ID)
                ?.takeIf(String::isNotBlank)
                ?: UUID.randomUUID().toString().also { putExtra(EXTRA_SHARE_CLIENT_ID, it) }
            val title = getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()
                ?: getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
            return AppLaunchCommand(
                section = AppLaunchTargets.SECTION_SHARE_IMPORT,
                sharedLink = SharedLinkParser.parse(
                    sharedText = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                    explicitTitle = title,
                    clientId = clientId,
                ),
            )
        }

        dataString
            ?.let(SollPairingPayloadParser::parse)
            ?.let { payload ->
                applySollPairingPayload(payload, reason = "deep_link_pairing")
                return AppLaunchCommand(section = AppLaunchTargets.SECTION_SETTINGS)
            }

        if (data?.scheme.equals("soll", ignoreCase = true) &&
            data?.host.equals(AppLaunchTargets.SECTION_TODAY, ignoreCase = true)
        ) {
            return AppLaunchCommand(section = AppLaunchTargets.SECTION_TODAY)
        }

        return AppLaunchTargets.fromExtras(
            section = getStringExtra(AppLaunchTargets.EXTRA_OPEN_SECTION),
            logsTab = getStringExtra(AppLaunchTargets.EXTRA_OPEN_LOGS_TAB),
        )
    }

    private fun applySollPairingPayload(payload: SollPairingPayload, reason: String) {
        settingsRepository.applySollPairingPayload(payload)
        GadgetServerSyncScheduler.schedule(applicationContext, settingsRepository)
        GadgetServerSyncScheduler.runNow(applicationContext, settingsRepository)
        SollServerSyncScheduler.schedule(
            applicationContext,
            settingsRepository,
            initialDelayMs = 0L,
            replaceExisting = true,
        )
        AndroidPushTokenRegistrar.registerCurrentToken(
            applicationContext,
            reason = reason,
            force = true,
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val EXTRA_SHARE_CLIENT_ID = "com.soll.extra.SHARE_CLIENT_ID"
    }
}
