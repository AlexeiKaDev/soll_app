package com.soll

import android.app.Application
import com.soll.data.notification.AppForegroundState
import com.soll.data.notification.SollNotificationChannels
import com.soll.data.repository.GadgetServerSyncScheduler
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.SollServerSyncScheduler
import com.soll.data.service.AndroidPushTokenRegistrar
import com.soll.data.service.SollServerSyncAlarmScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class SollApplication : Application() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        SollNotificationChannels.ensureAll(this)
        AppForegroundState.register(this)
        AppForegroundState.addBackgroundListener {
            SollServerSyncScheduler.schedule(
                this,
                settingsRepository,
                initialDelayMs = BACKGROUND_SYNC_DELAY_MS,
                replaceExisting = true,
            )
        }
        SollServerSyncAlarmScheduler.cancel(this)
        GadgetServerSyncScheduler.schedule(this, settingsRepository)
        GadgetServerSyncScheduler.runNow(this, settingsRepository)
        SollServerSyncScheduler.schedule(this, settingsRepository)
        AndroidPushTokenRegistrar.registerCurrentToken(
            this,
            reason = "startup",
            force = true,
        )

        Timber.d("SollApplication initialized")
    }

    companion object {
        private const val BACKGROUND_SYNC_DELAY_MS = 2_000L
        const val NOTIFICATION_CHANNEL_ID = "soll_chat"
        const val NOTIFICATION_ID = SollNotificationChannels.CHAT_NOTIFICATION_ID
        const val TTS_NOTIFICATION_CHANNEL_ID = "soll_tts_service"
        const val TTS_NOTIFICATION_ID = SollNotificationChannels.TTS_NOTIFICATION_ID
        const val MUSIC_NOTIFICATION_CHANNEL_ID = "soll_music_playback"
        const val MUSIC_NOTIFICATION_ID = SollNotificationChannels.MUSIC_NOTIFICATION_ID
        const val ACTIVITY_TRACKING_NOTIFICATION_CHANNEL_ID = "soll_activity_tracking"
        const val ACTIVITY_TRACKING_NOTIFICATION_ID = SollNotificationChannels.ACTIVITY_TRACKING_NOTIFICATION_ID
        const val EVENTS_NOTIFICATION_CHANNEL_ID = "soll_events"
        const val ALERTS_NOTIFICATION_CHANNEL_ID = "soll_alerts"
        const val TOOL_JOBS_NOTIFICATION_CHANNEL_ID = "soll_tool_jobs"
    }
}
