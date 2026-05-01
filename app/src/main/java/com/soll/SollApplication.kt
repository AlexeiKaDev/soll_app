package com.soll

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class SollApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Create notification channel for Foreground Service
        createNotificationChannel()

        Timber.d("SollApplication initialized")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val botChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(botChannel)

            val ttsChannel = NotificationChannel(
                TTS_NOTIFICATION_CHANNEL_ID,
                "Book Reader TTS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Text-to-speech playback controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(ttsChannel)

            Timber.d("Notification channels created")
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "soll_bot_service"
        const val NOTIFICATION_ID = 1001
        const val TTS_NOTIFICATION_CHANNEL_ID = "soll_tts_service"
        const val TTS_NOTIFICATION_ID = 1002
    }
}
