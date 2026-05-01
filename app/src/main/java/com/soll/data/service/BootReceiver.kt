package com.soll.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soll.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Timber.d("Boot completed, checking auto-start settings")

            if (settingsRepository.autoStartEnabled && settingsRepository.hasValidToken()) {
                Timber.d("Auto-starting bot service")
                BotService.start(context)
            } else {
                Timber.d("Auto-start disabled or no valid token")
            }
        }
    }
}
