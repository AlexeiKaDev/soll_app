package com.soll.data.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.soll.BuildConfig
import com.soll.R
import com.soll.data.notification.AppForegroundState
import com.soll.data.notification.SollNotificationChannels
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollServerSyncScheduler
import com.soll.domain.notification.SollNotificationChannel
import com.soll.presentation.MainActivity
import com.soll.presentation.navigation.AppLaunchTargets
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SollServerSyncForegroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        SollNotificationChannels.ensureAll(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startSyncForeground()

        if (intent?.action == ACTION_STOP) {
            stopForegroundNotification()
            stopSelf()
            return START_NOT_STICKY
        }

        val settings = settingsRepository()
        if (settings.sollServerUrl.isBlank()) {
            stopForegroundNotification()
            stopSelf()
            return START_NOT_STICKY
        }

        scheduleImmediateSync(settings)
        ensurePollingLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceJob.cancel()
        pollJob = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensurePollingLoop() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(FOREGROUND_POLL_INTERVAL_MS)
                val settings = settingsRepository()
                if (settings.sollServerUrl.isBlank() || AppForegroundState.isUserFacing()) {
                    stopSelf()
                    return@launch
                }
                scheduleImmediateSync(settings)
            }
        }
    }

    private fun scheduleImmediateSync(settings: SettingsRepository) {
        logSyncService("enqueue foreground sync")
        SollServerSyncScheduler.schedule(
            context = applicationContext,
            settingsRepository = settings,
            initialDelayMs = 0L,
            replaceExisting = true,
        )
        SollServerSyncAlarmScheduler.scheduleNext(applicationContext)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, SollNotificationChannel.SERVER_SYNC.channelId)
            .setSmallIcon(R.drawable.ic_ai_robot_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_icon_tint))
            .setContentTitle("Фоновая синхронизация Soll")
            .setContentText("Проверяю чат и задачи, пока приложение свернуто")
            .setContentIntent(openChatIntent())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(R.drawable.ic_stop, "Стоп", stopIntent())
            .build()

    private fun startSyncForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                SollNotificationChannels.SERVER_SYNC_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(
                SollNotificationChannels.SERVER_SYNC_NOTIFICATION_ID,
                notification,
            )
        }
    }

    private fun stopForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun openChatIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AppLaunchTargets.EXTRA_OPEN_SECTION, AppLaunchTargets.SECTION_CHAT)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun stopIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            1,
            Intent(this, SollServerSyncForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun settingsRepository(): SettingsRepository =
        EntryPointAccessors.fromApplication(
            applicationContext,
            SollServerSyncForegroundEntryPoint::class.java,
        ).settingsRepository()

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SollServerSyncForegroundEntryPoint {
        fun settingsRepository(): SettingsRepository
    }

    companion object {
        private const val ACTION_STOP = "com.soll.action.STOP_SERVER_SYNC_FOREGROUND"
        private const val FOREGROUND_POLL_INTERVAL_MS = 60_000L

        fun startIfConfigured(context: Context, settingsRepository: SettingsRepository) {
            if (settingsRepository.sollServerUrl.isBlank()) return
            runCatching {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, SollServerSyncForegroundService::class.java),
                )
            }.onFailure { error ->
                logSyncService("foreground start blocked: ${error.message ?: error::class.java.simpleName}")
                SollServerSyncScheduler.schedule(
                    context = context.applicationContext,
                    settingsRepository = settingsRepository,
                    initialDelayMs = 0L,
                    replaceExisting = true,
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.applicationContext.startService(
                    Intent(context.applicationContext, SollServerSyncForegroundService::class.java)
                        .setAction(ACTION_STOP),
                )
            }.onFailure { error ->
                logSyncService("foreground stop command failed: ${error.message ?: error::class.java.simpleName}")
                context.applicationContext.stopService(
                    Intent(context.applicationContext, SollServerSyncForegroundService::class.java),
                )
            }
        }
    }
}

private fun logSyncService(message: String) {
    if (BuildConfig.DEBUG) {
        Log.i("SollServerSyncForeground", message)
    }
}
