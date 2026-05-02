package com.soll.data.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.soll.R
import com.soll.SollApplication
import com.soll.data.api.model.Message
import com.soll.data.api.model.Update
import com.soll.data.repository.SettingsRepository
import com.soll.data.repository.TelegramRepository
import com.soll.domain.command.CommandProcessor
import com.soll.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BotService : Service() {

    @Inject
    lateinit var telegramRepository: TelegramRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var commandProcessor: CommandProcessor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val _isRunning = MutableStateFlow(false)

    companion object {
        private const val WAKELOCK_TAG = "Soll::BotServiceWakeLock"
        private const val ACTION_STOP = "com.soll.ACTION_STOP"

        val isRunning: StateFlow<Boolean> get() = _instance?._isRunning?.asStateFlow() ?: MutableStateFlow(false)
        private var _instance: BotService? = null

        var messagesProcessed: Long = 0
            private set

        var startTime: Long = 0
            private set

        var lastError: String? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, BotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BotService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        Timber.d("BotService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("BotService onStartCommand, action: ${intent?.action}")

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Start as foreground service immediately
        startForeground(SollApplication.NOTIFICATION_ID, createNotification())

        // Acquire wake lock
        acquireWakeLock()

        // Start polling
        startPolling()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Timber.d("BotService onDestroy")
        stopPolling()
        releaseWakeLock()
        _isRunning.value = false
        settingsRepository.isServiceRunning = false
        _instance = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.d("BotService onTaskRemoved")
        // Restart service if it was running
        if (settingsRepository.autoStartEnabled) {
            val restartIntent = Intent(this, BotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) {
            Timber.d("Polling already active")
            return
        }

        if (!settingsRepository.hasValidToken()) {
            Timber.e("No valid bot token")
            lastError = "No valid bot token configured"
            stopSelf()
            return
        }

        _isRunning.value = true
        settingsRepository.isServiceRunning = true
        startTime = System.currentTimeMillis()
        messagesProcessed = 0
        lastError = null

        pollingJob = serviceScope.launch {
            Timber.d("Starting polling loop")

            // Verify bot token first
            val botInfoResult = telegramRepository.getMe()
            if (botInfoResult.isFailure) {
                lastError = "Invalid bot token: ${botInfoResult.exceptionOrNull()?.message}"
                Timber.e("Failed to verify bot: $lastError")
                withContext(Dispatchers.Main) {
                    updateNotification("Error: $lastError")
                }
                delay(30000) // Wait before retry
            }

            var offset = settingsRepository.lastOffset
            val timeout = settingsRepository.pollingTimeout

            // Long polling conflicts with an active webhook (Telegram → HTTP 409).
            telegramRepository.deleteWebhook(dropPendingUpdates = false).fold(
                onSuccess = { Timber.d("deleteWebhook: ok (long polling mode)") },
                onFailure = { Timber.w(it, "deleteWebhook failed; getUpdates may return 409 if webhook is set") },
            )

            while (isActive) {
                try {
                    val result = telegramRepository.getUpdates(
                        offset = if (offset > 0) offset + 1 else null,
                        timeout = timeout
                    )

                    result.fold(
                        onSuccess = { updates ->
                            lastError = null
                            if (updates.isNotEmpty()) {
                                Timber.d("Received ${updates.size} updates")
                                processUpdates(updates)
                                offset = updates.maxOf { it.updateId }
                                settingsRepository.lastOffset = offset
                            }
                        },
                        onFailure = { error ->
                            lastError = error.message
                            Timber.e(error, "Failed to get updates")
                            withContext(Dispatchers.Main) {
                                updateNotification("Error: ${error.message}")
                            }
                            if (error is HttpException && error.code() == 409) {
                                Timber.w("409 Conflict: clearing webhook / retrying (also check no second bot instance)")
                                telegramRepository.deleteWebhook(dropPendingUpdates = false)
                                delay(1500)
                            } else {
                                delay(5000) // Wait before retry on error
                            }
                        }
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message
                    Timber.e(e, "Polling error")
                    delay(5000)
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        serviceScope.cancel()
    }

    private suspend fun processUpdates(updates: List<Update>) {
        for (update in updates) {
            try {
                update.message?.let { message ->
                    processMessage(message)
                    messagesProcessed++
                }

                update.callbackQuery?.let { callback ->
                    // Handle callback queries if needed
                    Timber.d("Callback query: ${callback.data}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing update ${update.updateId}")
            }
        }

        // Update notification with message count
        withContext(Dispatchers.Main) {
            updateNotification("Messages: $messagesProcessed")
        }
    }

    private suspend fun processMessage(message: Message) {
        val text = message.text ?: return

        // Check if it's a command
        if (text.startsWith("/")) {
            val parts = text.split(" ", limit = 2)
            val command = parts[0].removePrefix("/").split("@")[0] // Remove bot username if present
            val args = if (parts.size > 1) parts[1] else null

            Timber.d("Processing command: /$command with args: $args")

            commandProcessor.processCommand(
                command = command,
                args = args,
                message = message
            )
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BotService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SollApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, SollApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.notify(SollApplication.NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update notification")
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutes, will be refreshed
            }
            Timber.d("WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.d("WakeLock released")
            }
        }
        wakeLock = null
    }
}
