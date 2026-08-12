package com.soll.data.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.soll.BuildConfig
import com.soll.data.notification.AppForegroundState
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollServerSyncScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SollServerSyncAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SERVER_SYNC_ALARM) return

        val appContext = context.applicationContext
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            SollServerSyncAlarmEntryPoint::class.java,
        )
        val settings = entryPoint.settingsRepository()
        if (settings.sollServerUrl.isBlank()) {
            SollServerSyncAlarmScheduler.cancel(appContext)
            return
        }

        SollServerSyncAlarmScheduler.cancel(appContext)
        if (AppForegroundState.isUserFacing()) {
            logAlarm("legacy sync alarm disabled while app is user-facing")
            return
        }

        logAlarm("migrate legacy sync alarm to WorkManager")
        SollServerSyncScheduler.schedule(
            context = appContext,
            settingsRepository = settings,
            initialDelayMs = 0L,
            replaceExisting = true,
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SollServerSyncAlarmEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    companion object {
        const val ACTION_SERVER_SYNC_ALARM = "com.soll.action.SERVER_SYNC_ALARM"
    }
}

object SollServerSyncAlarmScheduler {
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
        logAlarm("cancelled sync alarm")
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SollServerSyncAlarmReceiver::class.java)
                .setAction(SollServerSyncAlarmReceiver.ACTION_SERVER_SYNC_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

private fun logAlarm(message: String) {
    if (BuildConfig.DEBUG) {
        Log.i("SollServerSyncAlarm", message)
    }
}
