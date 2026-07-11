package com.soll.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.soll.R
import com.soll.SollApplication
import com.soll.data.repository.ActivityTrackingRepository
import com.soll.data.repository.SettingsRepository
import com.soll.domain.activity.ActivitySample
import com.soll.domain.activity.ActivityTrackingPolicy
import com.soll.domain.activity.ActivityTrackingPolicyInput
import com.soll.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@AndroidEntryPoint
class ActivityTrackingService : Service(), SensorEventListener {

    @Inject lateinit var repository: ActivityTrackingRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var sampleJob: Job? = null
    private var stepSensor: Sensor? = null
    private var lastBootStepCounter: Int? = null
    private var pendingStepDelta: Int = 0
    private var lastSampleAt: Long = 0L
    private var sessionId: String = UUID.randomUUID().toString()

    override fun onCreate() {
        super.onCreate()
        _instance = this
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        lastSampleAt = repository.latestSample()?.capturedAt ?: 0L
        repository.refresh()
        Timber.d("ActivityTrackingService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settingsRepository.activityTrackerEnabled = false
            repository.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            SollApplication.ACTIVITY_TRACKING_NOTIFICATION_ID,
            createNotification("Запуск трекера активности..."),
        )

        if (!hasActivityRecognitionPermission() && !hasLocationPermission()) {
            lastError = "Нет разрешений на шагомер или геолокацию"
            settingsRepository.activityTrackerEnabled = false
            repository.setEnabled(false)
            updateNotification("Нет разрешений: включите шаги/геолокацию")
            stopSelf()
            return START_NOT_STICKY
        }

        settingsRepository.activityTrackerEnabled = true
        repository.setEnabled(true)
        startTracking()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Timber.d("ActivityTrackingService destroyed")
        sampleJob?.cancel()
        stepSensor?.let { sensorManager.unregisterListener(this, it) }
        serviceScope.cancel()
        _globalIsRunning.value = false
        _instance = null
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val current = event.values.firstOrNull()?.toInt() ?: return
        val previous = lastBootStepCounter
        if (previous != null && current >= previous) {
            pendingStepDelta += current - previous
        }
        lastBootStepCounter = current
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startTracking() {
        if (sampleJob?.isActive == true) return
        _globalIsRunning.value = true
        lastError = null

        stepSensor?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } ?: Timber.w("TYPE_STEP_COUNTER sensor is not available; activity tracker will use location-only samples")

        sampleJob = serviceScope.launch {
            delay(INITIAL_SAMPLE_DELAY_MS)
            while (isActive) {
                try {
                    recordIfNeeded()
                    delay(SAMPLE_LOOP_MS)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    lastError = error.message
                    Timber.e(error, "Activity tracking sample failed")
                    updateNotification("Ошибка трекера: ${error.message ?: "unknown"}")
                    delay(ERROR_RETRY_MS)
                }
            }
        }
    }

    private suspend fun recordIfNeeded() {
        val now = System.currentTimeMillis()
        val battery = readBattery()
        val decision = ActivityTrackingPolicy.decide(
            ActivityTrackingPolicyInput(
                elapsedSinceLastSampleMs = if (lastSampleAt > 0L) now - lastSampleAt else Long.MAX_VALUE,
                pendingStepDelta = pendingStepDelta,
                batteryLevel = battery.level,
                isCharging = battery.isCharging,
                hasLocationPermission = hasLocationPermission(),
                hasStepSensor = stepSensor != null,
            )
        )
        if (!decision.shouldRecord) {
            updateNotification(formatNotificationText(repository.summary.todaySteps, "ожидание движения"))
            return
        }

        val stepDelta = pendingStepDelta.coerceAtLeast(0)
        pendingStepDelta = 0
        val location = requestLocation()
        val sample = ActivitySample(
            sessionId = sessionId,
            capturedAt = now,
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracy?.takeIf { location.hasAccuracy() },
            stepDelta = stepDelta,
            batteryLevel = battery.level,
            isCharging = battery.isCharging,
            reason = decision.reason,
        )
        repository.recordSample(sample)
        lastSampleAt = now
        val summary = repository.summary
        updateNotification(formatNotificationText(summary.todaySteps, decision.reason))
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(): Location? {
        if (!hasLocationPermission()) return null
        val tokenSource = CancellationTokenSource()
        val current = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    tokenSource.token,
                )
                    .addOnSuccessListener { value ->
                        if (continuation.isActive) continuation.resume(value)
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                continuation.invokeOnCancellation { tokenSource.cancel() }
            }
        }
        tokenSource.cancel()
        if (current != null) return current

        return suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { value ->
                    if (continuation.isActive) continuation.resume(value)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun readBattery(): BatterySnapshot {
        val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else null
        val batteryStatus = status?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = batteryStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
            batteryStatus == BatteryManager.BATTERY_STATUS_FULL
        return BatterySnapshot(percent, charging)
    }

    private fun createNotification(text: String): Notification =
        NotificationCompat.Builder(this, SollApplication.ACTIVITY_TRACKING_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Soll Активность")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_soll_notification)
            .setContentIntent(contentPendingIntent())
            .addAction(R.drawable.ic_stop, "Стоп", stopPendingIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(
            SollApplication.ACTIVITY_TRACKING_NOTIFICATION_ID,
            createNotification(text),
        )
    }

    private fun contentPendingIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun stopPendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            1,
            Intent(this, ActivityTrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun formatNotificationText(steps: Int, reason: String): String =
        "Сегодня шагов: $steps • $reason"

    private data class BatterySnapshot(
        val level: Int?,
        val isCharging: Boolean,
    )

    companion object {
        private const val ACTION_STOP = "com.soll.ACTION_STOP_ACTIVITY_TRACKING"
        private const val INITIAL_SAMPLE_DELAY_MS = 2_000L
        private const val SAMPLE_LOOP_MS = 60_000L
        private const val ERROR_RETRY_MS = 90_000L
        private const val LOCATION_TIMEOUT_MS = 8_000L

        private val _globalIsRunning = MutableStateFlow(false)

        val isRunning: StateFlow<Boolean> = _globalIsRunning.asStateFlow()

        private var _instance: ActivityTrackingService? = null
        var lastError: String? = null
            private set

        fun start(context: Context): Boolean =
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ActivityTrackingService::class.java),
                )
                true
            }.getOrElse { error ->
                lastError = error.message
                Timber.e(error, "Failed to start ActivityTrackingService")
                false
            }

        fun stop(context: Context): Boolean =
            runCatching {
                context.startService(Intent(context, ActivityTrackingService::class.java).apply { action = ACTION_STOP })
                true
            }.getOrElse { error ->
                lastError = error.message
                Timber.e(error, "Failed to stop ActivityTrackingService")
                false
            }
    }
}
