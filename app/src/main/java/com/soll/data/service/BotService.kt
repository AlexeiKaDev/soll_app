package com.soll.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

/**
 * Archived Telegram bot service placeholder.
 *
 * The Android app now uses Soll chat/server sync. This class stays only so
 * legacy command/status code compiles while the old bot module is phased out.
 */
class BotService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastError = "Android Telegram bot is archived; use Soll chat sync"
        Timber.d("Archived BotService start ignored")
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private val stopped = MutableStateFlow(false)

        val isRunning: StateFlow<Boolean> = stopped
        var messagesProcessed: Long = 0
            private set
        var startTime: Long = 0
            private set
        var lastError: String? = "Android Telegram bot is archived; use Soll chat sync"
            private set

        fun start(context: Context) {
            lastError = "Android Telegram bot is archived; use Soll chat sync"
            Timber.d("Archived BotService start requested for %s", context.packageName)
        }

        fun stop(context: Context) {
            lastError = "Android Telegram bot is archived; use Soll chat sync"
            Timber.d("Archived BotService stop requested for %s", context.packageName)
        }
    }
}
