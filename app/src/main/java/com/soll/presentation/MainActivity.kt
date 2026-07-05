package com.soll.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.soll.data.repository.GadgetServerSyncScheduler
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollServerSyncScheduler
import com.soll.data.service.AndroidPushTokenRegistrar
import com.soll.domain.soll.SollPairingPayload
import com.soll.domain.soll.SollPairingPayloadParser
import com.soll.presentation.navigation.AppLaunchCommand
import com.soll.presentation.navigation.AppLaunchTargets
import com.soll.presentation.navigation.AppNavigation
import com.soll.ui.theme.SollTheme
import com.soll.ui.theme.SollThemeVariant
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private var launchCommand by mutableStateOf<AppLaunchCommand?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchCommand = intent?.toLaunchCommand()

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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchCommand = intent.toLaunchCommand()
    }

    private fun android.content.Intent.toLaunchCommand(): AppLaunchCommand? {
        dataString
            ?.let(SollPairingPayloadParser::parse)
            ?.let { payload ->
                applySollPairingPayload(payload, reason = "deep_link_pairing")
                return AppLaunchCommand(section = AppLaunchTargets.SECTION_SETTINGS)
            }

        return AppLaunchTargets.fromExtras(
            section = getStringExtra(AppLaunchTargets.EXTRA_OPEN_SECTION),
            logsTab = getStringExtra(AppLaunchTargets.EXTRA_OPEN_LOGS_TAB),
        )
    }

    private fun applySollPairingPayload(payload: SollPairingPayload, reason: String) {
        settingsRepository.applySollPairingPayload(payload)
        GadgetServerSyncScheduler.schedule(applicationContext, settingsRepository)
        SollServerSyncScheduler.schedule(applicationContext, settingsRepository)
        AndroidPushTokenRegistrar.registerCurrentToken(
            applicationContext,
            reason = reason,
            force = true,
        )
    }
}
