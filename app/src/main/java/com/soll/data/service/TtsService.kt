package com.soll.data.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.soll.R
import com.soll.SollApplication
import com.soll.domain.tts.TextToSpeechManager
import com.soll.domain.tts.TtsServiceAction
import com.soll.presentation.MainActivity
import com.soll.presentation.navigation.AppLaunchTargets
import com.soll.presentation.widgets.ReaderWidgetProvider
import com.soll.presentation.widgets.WidgetArtworkLoader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ReaderPlaybackState(
    val isRunning: Boolean = false,
    val title: String = "Читалка Soll",
    val subtitle: String = "",
    val coverPath: String? = null,
    val isPlaying: Boolean = false,
)

@AndroidEntryPoint
class TtsService : Service() {

    @Inject
    lateinit var ttsManager: TextToSpeechManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSessionCompat? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    private var currentTitle = "Читалка Soll"
    private var currentSubtitle = ""
    private var currentCoverPath: String? = null
    private var isPlaying = true

    companion object {
        private const val WAKELOCK_TAG = "Soll::TtsServiceWakeLock"
        private const val PLAYBACK_WAKELOCK_MS = 20 * 60 * 1000L
        private const val ACTION_PLAY = "com.soll.tts.PLAY"
        private const val ACTION_PAUSE = "com.soll.tts.PAUSE"
        private const val ACTION_STOP = "com.soll.tts.STOP"
        private const val ACTION_NEXT = "com.soll.tts.NEXT"
        private const val ACTION_PREV = "com.soll.tts.PREV"
        private const val ACTION_UPDATE_NOTIFICATION = "com.soll.tts.UPDATE_NOTIFICATION"
        private const val ACTION_UPDATE_PLAYBACK = "com.soll.tts.UPDATE_PLAYBACK"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val EXTRA_COVER_PATH = "cover_path"
        private const val EXTRA_IS_PLAYING = "is_playing"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        private val _playbackState = MutableStateFlow(ReaderPlaybackState())
        val playbackState: StateFlow<ReaderPlaybackState> = _playbackState.asStateFlow()

        fun currentState(): ReaderPlaybackState = _playbackState.value

        fun start(
            context: Context,
            title: String = "Читалка Soll",
            subtitle: String = "",
            coverPath: String? = null,
        ) {
            val intent = Intent(context, TtsService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_COVER_PATH, coverPath.orEmpty())
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TtsService::class.java))
        }

        fun play(context: Context) = sendCommandIfRunning(context, ACTION_PLAY)

        fun pause(context: Context) = sendCommandIfRunning(context, ACTION_PAUSE)

        fun toggle(context: Context) {
            if (_playbackState.value.isPlaying) {
                pause(context)
            } else {
                play(context)
            }
        }

        fun next(context: Context) = sendCommandIfRunning(context, ACTION_NEXT)

        fun previous(context: Context) = sendCommandIfRunning(context, ACTION_PREV)

        fun stopPlayback(context: Context) = sendCommandIfRunning(context, ACTION_STOP)

        fun updateNotification(context: Context, title: String, subtitle: String, coverPath: String? = null) {
            if (!_isRunning.value) return
            val intent = Intent(context, TtsService::class.java).apply {
                action = ACTION_UPDATE_NOTIFICATION
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_COVER_PATH, coverPath.orEmpty())
            }
            context.startService(intent)
        }

        fun updatePlaybackState(context: Context, isPlaying: Boolean) {
            if (!_isRunning.value) return
            val intent = Intent(context, TtsService::class.java).apply {
                action = ACTION_UPDATE_PLAYBACK
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            context.startService(intent)
        }

        private fun sendCommandIfRunning(context: Context, action: String) {
            if (!_isRunning.value) return
            context.startService(Intent(context, TtsService::class.java).setAction(action))
        }
    }

    override fun onCreate() {
        super.onCreate()
        setupMediaSession()
        _isRunning.value = true
        publishWidgetState()
        Timber.d("TtsService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                isPlaying = true
                ttsManager.emitServiceAction(TtsServiceAction.PLAY)
                updateWakeLockForPlayback()
                updateNotificationDisplay()
            }
            ACTION_PAUSE -> {
                isPlaying = false
                ttsManager.emitServiceAction(TtsServiceAction.PAUSE)
                updateWakeLockForPlayback()
                updateNotificationDisplay()
            }
            ACTION_STOP -> {
                isPlaying = false
                publishWidgetState()
                ttsManager.emitServiceAction(TtsServiceAction.STOP)
                updateWakeLockForPlayback()
                stopSelf()
            }
            ACTION_NEXT -> {
                ttsManager.emitServiceAction(TtsServiceAction.NEXT_CHAPTER)
            }
            ACTION_PREV -> {
                ttsManager.emitServiceAction(TtsServiceAction.PREV_CHAPTER)
            }
            ACTION_UPDATE_NOTIFICATION -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentSubtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: currentSubtitle
                currentCoverPath = intent.getStringExtra(EXTRA_COVER_PATH)?.takeIf { it.isNotBlank() }
                updateNotificationDisplay()
            }
            ACTION_UPDATE_PLAYBACK -> {
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
                updateWakeLockForPlayback()
                updateNotificationDisplay()
            }
            else -> {
                // Initial start
                currentTitle = intent?.getStringExtra(EXTRA_TITLE) ?: "Читалка Soll"
                currentSubtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: ""
                currentCoverPath = intent?.getStringExtra(EXTRA_COVER_PATH)?.takeIf { it.isNotBlank() }
                isPlaying = true
                updateWakeLockForPlayback()
                startForeground(SollApplication.TTS_NOTIFICATION_ID, buildNotification())
                publishWidgetState()
            }
        }

        observeTtsState()

        return START_NOT_STICKY
    }

    private fun observeTtsState() {
        if (observeJob?.isActive == true) return
        observeJob = serviceScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                if (isPlaying != speaking) {
                    isPlaying = speaking
                    updateWakeLockForPlayback()
                    updateNotificationDisplay()
                    updateMediaSessionPlaybackState()
                }
            }
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SollTtsSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    ttsManager.emitServiceAction(TtsServiceAction.PLAY)
                }

                override fun onPause() {
                    ttsManager.emitServiceAction(TtsServiceAction.PAUSE)
                }

                override fun onStop() {
                    ttsManager.emitServiceAction(TtsServiceAction.STOP)
                    stopSelf()
                }

                override fun onSkipToNext() {
                    ttsManager.emitServiceAction(TtsServiceAction.NEXT_CHAPTER)
                }

                override fun onSkipToPrevious() {
                    ttsManager.emitServiceAction(TtsServiceAction.PREV_CHAPTER)
                }
            })
            isActive = true
            updateMediaSessionPlaybackState()
        }
    }

    private fun updateMediaSessionPlaybackState() {
        val state = if (isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                .build()
        )

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentSubtitle.ifEmpty { currentTitle })
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentTitle)
        WidgetArtworkLoader.decodeFileArtwork(currentCoverPath)?.let { cover ->
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, cover)
            metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
        }
        mediaSession?.setMetadata(metadata.build())
    }

    private fun buildNotification(): android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra(AppLaunchTargets.EXTRA_OPEN_SECTION, AppLaunchTargets.SECTION_BOOK_READER)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TtsService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this, 2,
            Intent(this, TtsService::class.java).setAction(if (isPlaying) ACTION_PAUSE else ACTION_PLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, TtsService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, TtsService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseLabel = if (isPlaying) "Пауза" else "Играть"

        return NotificationCompat.Builder(this, SollApplication.TTS_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentSubtitle)
            .setSmallIcon(R.drawable.ic_ai_robot_notification)
            .setLargeIcon(WidgetArtworkLoader.decodeFileArtwork(currentCoverPath))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_prev, "Назад", prevIntent)
            .addAction(playPauseIcon, playPauseLabel, playPauseIntent)
            .addAction(R.drawable.ic_next, "Далее", nextIntent)
            .addAction(R.drawable.ic_stop, "Стоп", stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun updateNotificationDisplay() {
        updateMediaSessionPlaybackState()
        publishWidgetState()
        val notification = buildNotification()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(SollApplication.TTS_NOTIFICATION_ID, notification)
    }

    private fun publishWidgetState() {
        _playbackState.value = ReaderPlaybackState(
            isRunning = _isRunning.value,
            title = currentTitle,
            subtitle = currentSubtitle,
            coverPath = currentCoverPath,
            isPlaying = isPlaying,
        )
        ReaderWidgetProvider.updateAll(this)
    }

    private fun updateWakeLockForPlayback() {
        if (isPlaying) {
            acquirePlaybackWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun acquirePlaybackWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKELOCK_TAG
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(PLAYBACK_WAKELOCK_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        _isRunning.value = false
        _playbackState.value = ReaderPlaybackState()
        ReaderWidgetProvider.updateAll(this)
        observeJob?.cancel()
        observeJob = null
        serviceScope.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        releaseWakeLock()
        Timber.d("TtsService destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
