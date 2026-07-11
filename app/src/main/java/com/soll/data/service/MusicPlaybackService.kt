package com.soll.data.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.soll.R
import com.soll.SollApplication
import com.soll.data.local.entity.MusicSourceEntity
import com.soll.data.local.entity.MusicTrackEntity
import com.soll.data.notification.ForegroundServiceStartMode
import com.soll.data.notification.ForegroundServiceStartPolicy
import com.soll.data.repository.MusicRepository
import com.soll.data.repository.SettingsRepository
import com.soll.domain.music.MusicControllerAccessPolicy
import com.soll.domain.music.MusicPlayerState
import com.soll.domain.music.MusicRepeatMode
import com.soll.domain.music.MusicSourceType
import com.soll.domain.tts.TextToSpeechManager
import com.soll.presentation.MainActivity
import com.soll.presentation.navigation.AppLaunchTargets
import com.soll.presentation.widgets.MusicWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var musicRepository: MusicRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var ttsManager: TextToSpeechManager

    private lateinit var player: ExoPlayer
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)
    private var queue: List<MusicTrackEntity> = emptyList()
    private var positionTicker: Job? = null
    private var lastMarkedTrackId: Long? = null
    private var lastWidgetSnapshot: MusicWidgetSnapshot? = null

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (!isControllerAllowed(controller, session)) {
                return MediaSession.ConnectionResult.reject()
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon()
                        .add(COMMAND_TOGGLE_SHUFFLE)
                        .add(COMMAND_CYCLE_REPEAT)
                        .add(COMMAND_STOP_PLAYBACK)
                        .build()
                )
                .setMediaButtonPreferences(currentMediaButtonPreferences())
                .build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browsableItem(LIBRARY_ROOT_ID, "Музыка Soll"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            itemListFuture(params) {
                val items = when {
                    parentId == LIBRARY_ROOT_ID -> listOf(
                        browsableItem(LIBRARY_ALL_TRACKS_ID, "Все треки"),
                        browsableItem(LIBRARY_SOURCES_ID, "Папки и источники"),
                    )
                    parentId == LIBRARY_ALL_TRACKS_ID -> musicRepository.getAllTracks().map { it.toMediaItem() }
                    parentId == LIBRARY_SOURCES_ID -> musicRepository.observeSourcesSnapshot().map { it.toSourceMediaItem() }
                    parentId.startsWith(LIBRARY_SOURCE_PREFIX) -> {
                        val sourceUri = parentId.removePrefix(LIBRARY_SOURCE_PREFIX)
                        musicRepository.getTracksForSource(sourceUri).map { it.toMediaItem() }
                    }
                    else -> emptyList()
                }
                items.page(page, pageSize)
            }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            itemFuture {
                when {
                    mediaId == LIBRARY_ROOT_ID -> browsableItem(LIBRARY_ROOT_ID, "Музыка Soll")
                    mediaId == LIBRARY_ALL_TRACKS_ID -> browsableItem(LIBRARY_ALL_TRACKS_ID, "Все треки")
                    mediaId == LIBRARY_SOURCES_ID -> browsableItem(LIBRARY_SOURCES_ID, "Папки и источники")
                    mediaId.startsWith(LIBRARY_SOURCE_PREFIX) -> {
                        val sourceUri = mediaId.removePrefix(LIBRARY_SOURCE_PREFIX)
                        musicRepository.observeSourcesSnapshot()
                            .firstOrNull { it.uri == sourceUri }
                            ?.toSourceMediaItem()
                    }
                    else -> mediaId.toLongOrNull()?.let { musicRepository.getTrack(it)?.toMediaItem() }
                }
            }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            itemListFuture(params) {
                val normalized = query.trim().lowercase()
                if (normalized.isBlank()) {
                    emptyList()
                } else {
                    musicRepository.getAllTracks()
                        .filter { track ->
                            listOf(track.title, track.artist, track.album, track.displayName)
                                .filterNotNull()
                                .any { it.lowercase().contains(normalized) }
                        }
                        .map { it.toMediaItem() }
                        .page(page, pageSize)
                }
            }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            playbackResumptionFuture()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_TOGGLE_SHUFFLE.customAction -> setShuffle(!player.shuffleModeEnabled)
                COMMAND_CYCLE_REPEAT.customAction -> setRepeat(player.repeatMode.toMusicRepeatMode().next())
                COMMAND_STOP_PLAYBACK.customAction -> stopPlayback()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    override fun onCreate() {
        super.onCreate()
        configureMediaNotification()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(audioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            shuffleModeEnabled = settingsRepository.musicDefaultShuffle
            repeatMode = settingsRepository.musicDefaultRepeatMode.toPlayerRepeatMode()
            addListener(playerListener)
        }
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, libraryCallback)
            .setSessionActivity(createSessionActivity())
            .setMediaButtonPreferences(currentMediaButtonPreferences())
            .build()
        _state.value = _state.value.copy(errorMessage = null)
        restoreSavedState()
        Timber.d("MusicPlaybackService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != null) {
            acceptsDirectCommands = true
        }
        when (intent?.action) {
            ACTION_PLAY_TRACK -> {
                val trackId = intent.getLongExtra(EXTRA_TRACK_ID, 0L)
                if (trackId > 0L) playTrack(trackId)
            }
            ACTION_PLAY_QUEUE -> {
                val ids = intent.getStringExtra(EXTRA_QUEUE_IDS)
                    ?.split(',')
                    ?.mapNotNull { it.toLongOrNull() }
                    .orEmpty()
                val startTrackId = intent.getLongExtra(EXTRA_TRACK_ID, 0L).takeIf { it > 0L }
                playQueue(ids, startTrackId)
            }
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> next()
            ACTION_PREV -> previous()
            ACTION_STOP -> stopPlayback()
            ACTION_SEEK -> seekTo(intent.getLongExtra(EXTRA_POSITION_MS, 0L))
            ACTION_SET_SHUFFLE -> setShuffle(intent.getBooleanExtra(EXTRA_ENABLED, false))
            ACTION_SET_REPEAT -> setRepeat(
                runCatching {
                    MusicRepeatMode.valueOf(intent.getStringExtra(EXTRA_REPEAT_MODE) ?: MusicRepeatMode.OFF.name)
                }.getOrDefault(MusicRepeatMode.OFF)
            )
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        if (isControllerAllowed(controllerInfo, mediaLibrarySession)) {
            mediaLibrarySession
        } else {
            null
        }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePlaybackSnapshotFinal()
        positionTicker?.cancel()
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        if (::player.isInitialized) {
            player.removeListener(playerListener)
            player.release()
        }
        serviceJob.cancel()
        _state.value = MusicPlayerState(isServiceActive = false)
        acceptsDirectCommands = false
        publishWidgetIfChanged(_state.value)
        Timber.d("MusicPlaybackService destroyed")
        super.onDestroy()
    }

    private fun restoreSavedState() {
        if (!settingsRepository.musicResumeLastTrack) return
        serviceScope.launch(Dispatchers.IO) {
            val saved = musicRepository.getPlaybackState() ?: return@launch
            val tracks = savedTracks(saved.queueTrackIdsCsv).ifEmpty { musicRepository.getAllTracks() }
            if (tracks.isEmpty()) return@launch
            val startIndex = tracks.indexOfFirst { it.id == saved.currentTrackId }.takeIf { it >= 0 } ?: 0
            withContext(Dispatchers.Main.immediate) {
                queue = tracks
                player.shuffleModeEnabled = saved.shuffleEnabled
                player.repeatMode = saved.repeatMode.toRepeatMode()
                player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, saved.positionMs)
                player.prepare()
                publishState("Готово к продолжению")
            }
        }
    }

    private suspend fun savedTracks(queueTrackIdsCsv: String): List<MusicTrackEntity> {
        val ids = queueTrackIdsCsv
            .takeIf { it.isNotBlank() }
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        return if (ids.isEmpty()) emptyList() else musicRepository.getTracksByIds(ids)
    }

    private fun playbackResumptionFuture(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        serviceScope.launch(Dispatchers.IO) {
            runCatching {
                val saved = musicRepository.getPlaybackState()
                val tracks = saved?.queueTrackIdsCsv
                    ?.let { savedTracks(it) }
                    ?.ifEmpty { null }
                    ?: musicRepository.getAllTracks()
                if (tracks.isEmpty()) {
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                } else {
                    val startIndex = saved?.currentTrackId
                        ?.let { trackId -> tracks.indexOfFirst { it.id == trackId } }
                        ?.takeIf { it >= 0 }
                        ?: 0
                    withContext(Dispatchers.Main.immediate) {
                        queue = tracks
                    }
                    MediaSession.MediaItemsWithStartPosition(
                        tracks.map { it.toMediaItem() },
                        startIndex,
                        saved?.positionMs ?: 0L,
                    )
                }
            }.onSuccess { items ->
                future.set(items)
            }.onFailure { error ->
                Timber.w(error, "Music playback resumption failed")
                future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
            }
        }
        return future
    }

    private fun playTrack(trackId: Long) {
        loadQueueAndPlay(trackId = trackId, resumeSavedPosition = false)
    }

    private fun playQueue(trackIds: List<Long>, startTrackId: Long?) {
        if (trackIds.isEmpty()) {
            loadQueueAndPlay(trackId = startTrackId, resumeSavedPosition = false)
            return
        }
        publishPreparing("Готовлю очередь")
        serviceScope.launch(Dispatchers.IO) {
            val tracks = musicRepository.getTracksByIds(trackIds)
            if (tracks.isEmpty()) {
                publishError("В очереди нет доступных треков")
                return@launch
            }
            val startIndex = startTrackId
                ?.let { id -> tracks.indexOfFirst { it.id == id } }
                ?.takeIf { it >= 0 }
                ?: 0
            withContext(Dispatchers.Main.immediate) {
                if (settingsRepository.musicStopTtsOnStart) {
                    stopInternalTts()
                }
                queue = tracks
                player.shuffleModeEnabled = settingsRepository.musicDefaultShuffle
                player.repeatMode = settingsRepository.musicDefaultRepeatMode.toPlayerRepeatMode()
                player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, 0L)
                player.prepare()
                player.play()
                publishState("Воспроизведение подборки")
            }
        }
    }

    private fun play() {
        if (player.mediaItemCount > 0) {
            stopInternalTts()
            player.play()
            publishState()
            return
        }
        loadQueueAndPlay(trackId = null, resumeSavedPosition = settingsRepository.musicResumeLastTrack)
    }

    private fun loadQueueAndPlay(trackId: Long?, resumeSavedPosition: Boolean) {
        publishPreparing("Готовлю музыку")
        serviceScope.launch(Dispatchers.IO) {
            val saved = if (resumeSavedPosition) musicRepository.getPlaybackState() else null
            val tracks = saved?.queueTrackIdsCsv
                ?.let { savedTracks(it) }
                ?.ifEmpty { null }
                ?: musicRepository.getAllTracks()

            if (tracks.isEmpty()) {
                publishError("Медиатека пуста")
                return@launch
            }

            val startIndex = when {
                trackId != null -> tracks.indexOfFirst { it.id == trackId }
                saved?.currentTrackId != null -> tracks.indexOfFirst { it.id == saved.currentTrackId }
                else -> 0
            }.takeIf { it >= 0 } ?: 0
            val startPosition = if (trackId == null && saved != null) saved.positionMs else 0L

            withContext(Dispatchers.Main.immediate) {
                if (settingsRepository.musicStopTtsOnStart) {
                    stopInternalTts()
                }
                queue = tracks
                player.shuffleModeEnabled = saved?.shuffleEnabled ?: settingsRepository.musicDefaultShuffle
                player.repeatMode = saved?.repeatMode?.toRepeatMode()
                    ?: settingsRepository.musicDefaultRepeatMode.toPlayerRepeatMode()
                player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex, startPosition)
                player.prepare()
                player.play()
                publishState("Воспроизведение")
            }
        }
    }

    private fun pause() {
        player.pause()
        savePlaybackSnapshot()
        publishState("Пауза")
    }

    private fun toggle() {
        if (player.isPlaying) pause() else play()
    }

    private fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
        }
        publishState()
    }

    private fun previous() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else if (player.mediaItemCount > 0) {
            player.seekTo(0L)
        }
        publishState()
    }

    private fun stopPlayback() {
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        savePlaybackSnapshot()
        acceptsDirectCommands = false
        publishState("Остановлено")
        stopSelf()
    }

    private fun seekTo(positionMs: Long) {
        if (player.mediaItemCount == 0) return
        player.seekTo(positionMs.coerceAtLeast(0L))
        savePlaybackSnapshot()
        publishState()
    }

    private fun setShuffle(enabled: Boolean) {
        settingsRepository.musicDefaultShuffle = enabled
        player.shuffleModeEnabled = enabled
        updateMediaButtonPreferences()
        savePlaybackSnapshot()
        publishState()
    }

    private fun setRepeat(mode: MusicRepeatMode) {
        settingsRepository.musicDefaultRepeatMode = mode
        player.repeatMode = mode.toPlayerRepeatMode()
        updateMediaButtonPreferences()
        savePlaybackSnapshot()
        publishState()
    }

    private fun savePlaybackSnapshot() {
        if (!::player.isInitialized) return
        val snapshot = capturePlaybackSnapshot()
        serviceScope.launch(Dispatchers.IO) {
            savePlaybackSnapshot(snapshot)
        }
    }

    private fun savePlaybackSnapshotFinal() {
        if (!::player.isInitialized) return
        val snapshot = capturePlaybackSnapshot()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                savePlaybackSnapshot(snapshot)
            }.onFailure { error ->
                Timber.w(error, "Failed to save final music playback snapshot")
            }
        }
    }

    private fun capturePlaybackSnapshot(): PlaybackSnapshot =
        PlaybackSnapshot(
            currentTrackId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            positionMs = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode.toMusicRepeatMode(),
            queueTrackIds = queue.map { it.id },
        )

    private suspend fun savePlaybackSnapshot(snapshot: PlaybackSnapshot) {
        musicRepository.savePlaybackState(
            currentTrackId = snapshot.currentTrackId,
            positionMs = snapshot.positionMs,
            shuffleEnabled = snapshot.shuffleEnabled,
            repeatMode = snapshot.repeatMode,
            queueTrackIds = snapshot.queueTrackIds,
        )
    }

    private fun publishPreparing(message: String) {
        _state.value = _state.value.copy(
            isServiceActive = true,
            isPreparing = true,
            statusText = message,
            errorMessage = null,
        )
        publishWidgetIfChanged(_state.value)
    }

    private fun publishState(statusText: String? = null) {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        _state.value = MusicPlayerState(
            isServiceActive = acceptsDirectCommands || player.isPlaying,
            isPreparing = player.playbackState == Player.STATE_BUFFERING,
            currentTrackId = item?.mediaId?.toLongOrNull(),
            currentTrackUri = item?.localConfiguration?.uri?.toString(),
            title = metadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: "Музыка",
            artist = metadata?.artist?.toString()?.takeIf { it.isNotBlank() },
            album = metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L,
            durationMs = duration,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode.toMusicRepeatMode(),
            queueSize = player.mediaItemCount,
            statusText = statusText,
            errorMessage = null,
        )
        publishWidgetIfChanged(_state.value)
    }

    private fun publishError(message: String) {
        serviceScope.launch(Dispatchers.Main.immediate) {
            _state.value = _state.value.copy(
                isPreparing = false,
                isPlaying = false,
                statusText = null,
                errorMessage = message,
            )
            publishWidgetIfChanged(_state.value)
        }
    }

    private fun publishWidgetIfChanged(state: MusicPlayerState) {
        val nextSnapshot = MusicWidgetSnapshot(
            isServiceActive = state.isServiceActive,
            isPreparing = state.isPreparing,
            currentTrackId = state.currentTrackId,
            currentTrackUri = state.currentTrackUri,
            title = state.title,
            artist = state.artist,
            album = state.album,
            isPlaying = state.isPlaying,
            statusText = state.statusText,
            errorMessage = state.errorMessage,
        )
        if (lastWidgetSnapshot == nextSnapshot) return
        lastWidgetSnapshot = nextSnapshot
        MusicWidgetProvider.updateAll(this)
    }

    private fun isControllerAllowed(
        controllerInfo: MediaSession.ControllerInfo,
        session: MediaSession? = mediaLibrarySession,
    ): Boolean = MusicControllerAccessPolicy.canConnect(
        appPackageName = packageName,
        controllerPackageName = controllerInfo.packageName,
        isMediaNotificationController = session?.isMediaNotificationController(controllerInfo) == true,
        isTrusted = controllerInfo.isTrusted,
        headsetControlsEnabled = settingsRepository.musicHeadsetControls,
    )

    private fun stopInternalTts() {
        ttsManager.stop()
        TtsService.stop(this)
    }

    private fun startTickerIfNeeded() {
        if (positionTicker?.isActive == true) return
        positionTicker = serviceScope.launch {
            while (player.isPlaying) {
                publishState()
                delay(1000L)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun configureMediaNotification() {
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(SollApplication.MUSIC_NOTIFICATION_CHANNEL_ID)
            .setNotificationId(SollApplication.MUSIC_NOTIFICATION_ID)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_soll_notification)
            }
        setMediaNotificationProvider(provider)
    }

    private fun updateMediaButtonPreferences() {
        mediaLibrarySession?.setMediaButtonPreferences(currentMediaButtonPreferences())
    }

    @OptIn(UnstableApi::class)
    private fun currentMediaButtonPreferences(): List<CommandButton> =
        listOf(
            CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .setDisplayName("Предыдущий")
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(if (player.isPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY)
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setDisplayName(if (player.isPlaying) "Пауза" else "Играть")
                .setSlots(CommandButton.SLOT_CENTRAL)
                .build(),
            CommandButton.Builder(CommandButton.ICON_NEXT)
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .setDisplayName("Следующий")
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(
                if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
            )
                .setSessionCommand(COMMAND_TOGGLE_SHUFFLE)
                .setDisplayName(if (player.shuffleModeEnabled) "Перемешивание включено" else "Перемешивание выключено")
                .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(player.repeatMode.toMusicRepeatMode().notificationIcon())
                .setSessionCommand(COMMAND_CYCLE_REPEAT)
                .setDisplayName(player.repeatMode.toMusicRepeatMode().notificationLabel())
                .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
                .build(),
            CommandButton.Builder(CommandButton.ICON_STOP)
                .setSessionCommand(COMMAND_STOP_PLAYBACK)
                .setDisplayName("Стоп")
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )

    private fun createSessionActivity(): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra(AppLaunchTargets.EXTRA_OPEN_SECTION, AppLaunchTargets.SECTION_MUSIC)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            flags,
        )
    }

    private fun itemFuture(
        loader: suspend () -> MediaItem?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        serviceScope.launch(Dispatchers.IO) {
            runCatching { loader() }
                .onSuccess { item ->
                    future.set(
                        item?.let { LibraryResult.ofItem(it, null) }
                            ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                    )
                }
                .onFailure { error ->
                    Timber.w(error, "Failed to load music library item")
                    future.set(LibraryResult.ofError(SessionError.ERROR_IO))
                }
        }
        return future
    }

    private fun itemListFuture(
        params: LibraryParams?,
        loader: suspend () -> List<MediaItem>,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        serviceScope.launch(Dispatchers.IO) {
            runCatching { loader() }
                .onSuccess { future.set(LibraryResult.ofItemList(it, params)) }
                .onFailure { error ->
                    Timber.w(error, "Failed to load music library children")
                    future.set(LibraryResult.ofItemList(emptyList(), params))
                }
        }
        return future
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startTickerIfNeeded()
            } else {
                positionTicker?.cancel()
                savePlaybackSnapshot()
            }
            updateMediaButtonPreferences()
            publishState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val trackId = mediaItem?.mediaId?.toLongOrNull()
            if (trackId != null && trackId != lastMarkedTrackId) {
                lastMarkedTrackId = trackId
                serviceScope.launch(Dispatchers.IO) {
                    musicRepository.markTrackPlayed(trackId)
                }
            }
            savePlaybackSnapshot()
            publishState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            publishState()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            settingsRepository.musicDefaultShuffle = shuffleModeEnabled
            updateMediaButtonPreferences()
            savePlaybackSnapshot()
            publishState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            settingsRepository.musicDefaultRepeatMode = repeatMode.toMusicRepeatMode()
            updateMediaButtonPreferences()
            savePlaybackSnapshot()
            publishState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Music playback failed")
            _state.value = _state.value.copy(
                isPreparing = false,
                isPlaying = false,
                errorMessage = error.message ?: "Ошибка воспроизведения",
            )
            publishWidgetIfChanged(_state.value)
            savePlaybackSnapshot()
        }
    }

    private fun MusicSourceEntity.toSourceMediaItem(): MediaItem {
        val title = if (sourceType == MusicSourceType.FOLDER.name) displayName else "Выбранные треки"
        return browsableItem("$LIBRARY_SOURCE_PREFIX$uri", "$title ($trackCount)")
    }

    private fun browsableItem(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun MusicTrackEntity.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun List<MediaItem>.page(page: Int, pageSize: Int): List<MediaItem> {
        if (pageSize <= 0) return this
        val from = page.coerceAtLeast(0) * pageSize
        if (from >= size) return emptyList()
        val to = (from + pageSize).coerceAtMost(size)
        return subList(from, to)
    }

    private fun String.toRepeatMode(): Int =
        runCatching { MusicRepeatMode.valueOf(this) }
            .getOrDefault(MusicRepeatMode.OFF)
            .toPlayerRepeatMode()

    private fun MusicRepeatMode.toPlayerRepeatMode(): Int = when (this) {
        MusicRepeatMode.OFF -> Player.REPEAT_MODE_OFF
        MusicRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        MusicRepeatMode.ALL -> Player.REPEAT_MODE_ALL
    }

    private fun Int.toMusicRepeatMode(): MusicRepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> MusicRepeatMode.ONE
        Player.REPEAT_MODE_ALL -> MusicRepeatMode.ALL
        else -> MusicRepeatMode.OFF
    }

    private fun MusicRepeatMode.next(): MusicRepeatMode = when (this) {
        MusicRepeatMode.OFF -> MusicRepeatMode.ALL
        MusicRepeatMode.ALL -> MusicRepeatMode.ONE
        MusicRepeatMode.ONE -> MusicRepeatMode.OFF
    }

    @OptIn(UnstableApi::class)
    private fun MusicRepeatMode.notificationIcon(): Int = when (this) {
        MusicRepeatMode.OFF -> CommandButton.ICON_REPEAT_OFF
        MusicRepeatMode.ALL -> CommandButton.ICON_REPEAT_ALL
        MusicRepeatMode.ONE -> CommandButton.ICON_REPEAT_ONE
    }

    private fun MusicRepeatMode.notificationLabel(): String = when (this) {
        MusicRepeatMode.OFF -> "Повтор выключен"
        MusicRepeatMode.ALL -> "Повтор очереди"
        MusicRepeatMode.ONE -> "Повтор трека"
    }

    private data class PlaybackSnapshot(
        val currentTrackId: Long?,
        val positionMs: Long,
        val shuffleEnabled: Boolean,
        val repeatMode: MusicRepeatMode,
        val queueTrackIds: List<Long>,
    )

    private data class MusicWidgetSnapshot(
        val isServiceActive: Boolean,
        val isPreparing: Boolean,
        val currentTrackId: Long?,
        val currentTrackUri: String?,
        val title: String,
        val artist: String?,
        val album: String?,
        val isPlaying: Boolean,
        val statusText: String?,
        val errorMessage: String?,
    )

    companion object {
        private val COMMAND_TOGGLE_SHUFFLE = SessionCommand("com.soll.music.TOGGLE_SHUFFLE", Bundle.EMPTY)
        private val COMMAND_CYCLE_REPEAT = SessionCommand("com.soll.music.CYCLE_REPEAT", Bundle.EMPTY)
        private val COMMAND_STOP_PLAYBACK = SessionCommand("com.soll.music.STOP_PLAYBACK", Bundle.EMPTY)
        private const val ACTION_PLAY_TRACK = "com.soll.music.PLAY_TRACK"
        private const val ACTION_PLAY_QUEUE = "com.soll.music.PLAY_QUEUE"
        private const val ACTION_PLAY = "com.soll.music.PLAY"
        private const val ACTION_PAUSE = "com.soll.music.PAUSE"
        private const val ACTION_TOGGLE = "com.soll.music.TOGGLE"
        private const val ACTION_NEXT = "com.soll.music.NEXT"
        private const val ACTION_PREV = "com.soll.music.PREV"
        private const val ACTION_STOP = "com.soll.music.STOP"
        private const val ACTION_SEEK = "com.soll.music.SEEK"
        private const val ACTION_SET_SHUFFLE = "com.soll.music.SET_SHUFFLE"
        private const val ACTION_SET_REPEAT = "com.soll.music.SET_REPEAT"
        private const val EXTRA_TRACK_ID = "track_id"
        private const val EXTRA_QUEUE_IDS = "queue_ids"
        private const val EXTRA_POSITION_MS = "position_ms"
        private const val EXTRA_ENABLED = "enabled"
        private const val EXTRA_REPEAT_MODE = "repeat_mode"
        private const val LIBRARY_ROOT_ID = "soll_music_root"
        private const val LIBRARY_ALL_TRACKS_ID = "soll_music_all_tracks"
        private const val LIBRARY_SOURCES_ID = "soll_music_sources"
        private const val LIBRARY_SOURCE_PREFIX = "soll_music_source:"
        private val _state = kotlinx.coroutines.flow.MutableStateFlow(MusicPlayerState())
        val state: kotlinx.coroutines.flow.StateFlow<MusicPlayerState> = _state.asStateFlow()
        private var acceptsDirectCommands = false

        fun currentState(): MusicPlayerState = _state.value

        fun playTrack(context: Context, trackId: Long) {
            startPlaybackCommand(
                context,
                Intent(context, MusicPlaybackService::class.java).apply {
                    action = ACTION_PLAY_TRACK
                    putExtra(EXTRA_TRACK_ID, trackId)
                },
            )
        }

        fun playQueue(context: Context, trackIds: List<Long>, startTrackId: Long? = trackIds.firstOrNull()) {
            startPlaybackCommand(
                context,
                Intent(context, MusicPlaybackService::class.java).apply {
                    action = ACTION_PLAY_QUEUE
                    putExtra(EXTRA_QUEUE_IDS, trackIds.distinct().joinToString(","))
                    startTrackId?.let { putExtra(EXTRA_TRACK_ID, it) }
                },
            )
        }

        fun play(context: Context) {
            startPlaybackCommand(
                context,
                Intent(context, MusicPlaybackService::class.java).setAction(ACTION_PLAY),
            )
        }

        fun pause(context: Context) = startIfActive(context, ACTION_PAUSE)

        fun toggle(context: Context) = startIfActive(context, ACTION_TOGGLE)

        fun next(context: Context) = startIfActive(context, ACTION_NEXT)

        fun previous(context: Context) = startIfActive(context, ACTION_PREV)

        fun stop(context: Context) = startIfActive(context, ACTION_STOP)

        fun seekTo(context: Context, positionMs: Long) {
            if (!ForegroundServiceStartPolicy.canSendDirectControlCommand(acceptsDirectCommands)) return
            context.startService(
                Intent(context, MusicPlaybackService::class.java).apply {
                    action = ACTION_SEEK
                    putExtra(EXTRA_POSITION_MS, positionMs)
                }
            )
        }

        fun setShuffle(context: Context, enabled: Boolean) {
            if (!ForegroundServiceStartPolicy.canSendDirectControlCommand(acceptsDirectCommands)) return
            context.startService(
                Intent(context, MusicPlaybackService::class.java).apply {
                    action = ACTION_SET_SHUFFLE
                    putExtra(EXTRA_ENABLED, enabled)
                }
            )
        }

        fun setRepeat(context: Context, repeatMode: MusicRepeatMode) {
            if (!ForegroundServiceStartPolicy.canSendDirectControlCommand(acceptsDirectCommands)) return
            context.startService(
                Intent(context, MusicPlaybackService::class.java).apply {
                    action = ACTION_SET_REPEAT
                    putExtra(EXTRA_REPEAT_MODE, repeatMode.name)
                }
            )
        }

        private fun startIfActive(context: Context, action: String) {
            if (!ForegroundServiceStartPolicy.canSendDirectControlCommand(acceptsDirectCommands)) return
            context.startService(Intent(context, MusicPlaybackService::class.java).setAction(action))
        }

        private fun startPlaybackCommand(context: Context, intent: Intent) {
            runCatching {
                when (
                    ForegroundServiceStartPolicy.forPlaybackCommand(
                        acceptsDirectCommands = acceptsDirectCommands,
                        hasOngoingPlayback = _state.value.isPlaying,
                    )
                ) {
                    ForegroundServiceStartMode.START_SERVICE -> context.startService(intent)
                    ForegroundServiceStartMode.START_FOREGROUND_SERVICE ->
                        ContextCompat.startForegroundService(context, intent)
                }
            }.onFailure { error ->
                Timber.w(error, "Music startService failed, retrying as foreground service")
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
