package com.soll.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollServerSyncScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootReceiverEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Timber.d("Boot completed, checking auto-start settings")

            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                BootReceiverEntryPoint::class.java
            )
            val settingsRepository = entryPoint.settingsRepository()

            Timber.d("Telegram bot auto-start is archived; server chat sync is scheduled by WorkManager")
            SollServerSyncAlarmScheduler.cancel(context.applicationContext)
            SollServerSyncScheduler.schedule(context.applicationContext, settingsRepository, initialDelayMs = 0L)

            if (settingsRepository.activityTrackerEnabled) {
                Timber.d("Auto-starting activity tracker service")
                if (!ActivityTrackingService.start(context)) {
                    Timber.w("Activity tracker auto-start was blocked by Android")
                }
            }
        }
    }
}
