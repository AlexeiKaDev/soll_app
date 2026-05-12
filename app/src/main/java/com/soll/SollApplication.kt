package com.soll

import android.app.Application
import com.soll.data.notification.SollNotificationChannels
import com.soll.data.repository.GadgetServerSyncScheduler
import com.soll.data.repository.SettingsRepository
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
        GadgetServerSyncScheduler.schedule(this, settingsRepository)

        Timber.d("SollApplication initialized")
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "soll_bot_service"
        const val NOTIFICATION_ID = SollNotificationChannels.BOT_SERVICE_NOTIFICATION_ID
        const val TTS_NOTIFICATION_CHANNEL_ID = "soll_tts_service"
        const val TTS_NOTIFICATION_ID = SollNotificationChannels.TTS_NOTIFICATION_ID
        const val MUSIC_NOTIFICATION_CHANNEL_ID = "soll_music_playback"
        const val MUSIC_NOTIFICATION_ID = SollNotificationChannels.MUSIC_NOTIFICATION_ID
        const val EVENTS_NOTIFICATION_CHANNEL_ID = "soll_events"
        const val ALERTS_NOTIFICATION_CHANNEL_ID = "soll_alerts"
        const val TOOL_JOBS_NOTIFICATION_CHANNEL_ID = "soll_tool_jobs"
    }
}
