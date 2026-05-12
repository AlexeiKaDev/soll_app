package com.soll.presentation.screens.tools.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.soll.data.local.entity.MusicSourceEntity
import com.soll.data.local.entity.MusicTrackEntity
import com.soll.data.repository.MusicRepository
import com.soll.data.repository.SettingsRepository
import com.soll.data.service.MusicPlaybackService
import com.soll.data.service.TtsService
import com.soll.domain.assistant.AssistantEvent
import com.soll.domain.assistant.AssistantEventLogger
import com.soll.domain.assistant.CapabilityRegistry
import com.soll.domain.music.MusicPlayerState
import com.soll.domain.music.MusicLibraryInsights
import com.soll.domain.music.MusicLibraryView
import com.soll.domain.music.MusicPlaylistCard
import com.soll.domain.music.MusicRepeatMode
import com.soll.domain.music.MusicSettings
import com.soll.domain.music.MusicSourceType
import com.soll.domain.tool.ToolHandler
import com.soll.domain.tool.ToolJob
import com.soll.domain.tool.ToolJobProgressSink
import com.soll.domain.tool.ToolJobResult
import com.soll.domain.tool.ToolJobRunner
import com.soll.domain.tool.ToolJobStatus
import com.soll.domain.tts.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

data class MusicUiState(
    val sources: List<MusicSourceEntity> = emptyList(),
    val tracks: List<MusicTrackEntity> = emptyList(),
    val playlists: List<MusicPlaylistCard> = emptyList(),
    val insights: MusicLibraryInsights = MusicLibraryInsights(),
    val playerState: MusicPlayerState = MusicPlayerState(),
    val settings: MusicSettings = MusicSettings(),
    val libraryView: MusicLibraryView = MusicLibraryView.OVERVIEW,
    val searchQuery: String = "",
    val collectionTitle: String? = null,
    val collectionSubtitle: String? = null,
    val collectionTrackIds: List<Long> = emptyList(),
    val selectedTrackIds: Set<Long> = emptySet(),
    val playlistNameInput: String = "",
    val isImporting: Boolean = false,
    val showSettings: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
) {
    val visibleTracks: List<MusicTrackEntity>
        get() {
            val base = if (collectionTrackIds.isEmpty()) {
                tracks
            } else {
                val byId = tracks.associateBy { it.id }
                collectionTrackIds.mapNotNull { byId[it] }
            }
            val query = searchQuery.trim().lowercase()
            if (query.isBlank()) return base
            return base.filter { track ->
                listOf(track.title, track.artist, track.album, track.albumArtist, track.genre, track.year?.toString(), track.displayName)
                    .filterNotNull()
                    .any { it.lowercase().contains(query) }
            }
        }

    val selectedTracks: List<MusicTrackEntity>
        get() = tracks.filter { it.id in selectedTrackIds }
}

@HiltViewModel
class MusicViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    private val capabilityRegistry: CapabilityRegistry,
    private val toolJobRunner: ToolJobRunner,
    private val assistantEventLogger: AssistantEventLogger,
    private val ttsManager: TextToSpeechManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    private var autoRescanRequested = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val controllerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishControllerState(player)
        }
    }

    init {
        _uiState.update {
            val settings = settingsRepository.getMusicSettings()
            it.copy(
                settings = settings,
                playerState = it.playerState.copy(
                    shuffleEnabled = settings.defaultShuffle,
                    repeatMode = settings.defaultRepeatMode,
                ),
            )
        }
        connectMediaController()
        viewModelScope.launch {
            musicRepository.observeTracks().collectLatest { tracks ->
                _uiState.update { state ->
                    val ids = tracks.map { it.id }.toSet()
                    state.copy(
                        tracks = tracks,
                        insights = musicRepository.buildLibraryInsights(tracks),
                        selectedTrackIds = state.selectedTrackIds.intersect(ids),
                        collectionTrackIds = state.collectionTrackIds.filter { it in ids },
                    )
                }
            }
        }
        viewModelScope.launch {
            musicRepository.observePlaylists().collectLatest { playlists ->
                _uiState.update { state ->
                    state.copy(
                        playlists = playlists.map {
                            MusicPlaylistCard(
                                id = it.id,
                                name = it.name,
                                description = it.description,
                                mood = it.mood,
                                trackCount = it.trackCount,
                                coverSeed = it.coverSeed,
                                updatedAt = it.updatedAt,
                            )
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            musicRepository.observeSources().collectLatest { sources ->
                _uiState.update { it.copy(sources = sources) }
                if (
                    !autoRescanRequested &&
                    _uiState.value.settings.autoRescanOnOpen &&
                    sources.any { it.sourceType == MusicSourceType.FOLDER.name }
                ) {
                    autoRescanRequested = true
                    rescanFolders()
                }
            }
        }
        viewModelScope.launch {
            MusicPlaybackService.state.collectLatest { state ->
                _uiState.update { it.copy(playerState = state) }
            }
        }
    }

    override fun onCleared() {
        mediaController?.removeListener(controllerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onCleared()
    }

    fun updateSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun selectLibraryView(view: MusicLibraryView) {
        _uiState.update {
            it.copy(
                libraryView = view,
                collectionTitle = if (view == MusicLibraryView.TRACKS) it.collectionTitle else null,
                collectionSubtitle = if (view == MusicLibraryView.TRACKS) it.collectionSubtitle else null,
                collectionTrackIds = if (view == MusicLibraryView.TRACKS) it.collectionTrackIds else emptyList(),
            )
        }
    }

    fun clearCollectionFilter() {
        _uiState.update {
            it.copy(
                collectionTitle = null,
                collectionSubtitle = null,
                collectionTrackIds = emptyList(),
                libraryView = MusicLibraryView.TRACKS,
            )
        }
    }

    fun openCollection(title: String, subtitle: String, trackIds: List<Long>) {
        _uiState.update {
            it.copy(
                libraryView = MusicLibraryView.TRACKS,
                collectionTitle = title,
                collectionSubtitle = subtitle,
                collectionTrackIds = trackIds,
                searchQuery = "",
            )
        }
    }

    fun openPlaylist(playlist: MusicPlaylistCard) {
        viewModelScope.launch {
            val tracks = musicRepository.getTracksForPlaylist(playlist.id)
            openCollection(
                title = playlist.name,
                subtitle = "${playlist.trackCount} треков${playlist.mood?.let { " · $it" } ?: ""}",
                trackIds = tracks.map { it.id },
            )
        }
    }

    fun updatePlaylistName(value: String) {
        _uiState.update { it.copy(playlistNameInput = value, message = null, isError = false) }
    }

    fun toggleTrackSelection(trackId: Long) {
        _uiState.update {
            val next = if (trackId in it.selectedTrackIds) {
                it.selectedTrackIds - trackId
            } else {
                it.selectedTrackIds + trackId
            }
            it.copy(selectedTrackIds = next)
        }
    }

    fun selectVisibleTracks() {
        _uiState.update { it.copy(selectedTrackIds = it.selectedTrackIds + it.visibleTracks.map { track -> track.id }) }
    }

    fun clearTrackSelection() {
        _uiState.update { it.copy(selectedTrackIds = emptySet()) }
    }

    fun createPlaylistFromSelection() {
        val state = _uiState.value
        val name = state.playlistNameInput.trim()
        val ids = state.selectedTrackIds
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "Введите название плейлиста", isError = true) }
            return
        }
        if (ids.isEmpty() && state.libraryView != MusicLibraryView.PLAYLISTS) {
            _uiState.update { it.copy(message = "Нет треков для плейлиста", isError = true) }
            return
        }
        viewModelScope.launch {
            runCatching {
                musicRepository.createPlaylist(
                    name = name,
                    description = if (ids.isNotEmpty()) {
                        state.collectionTitle?.let { "Создано из подборки: $it" }.orEmpty()
                    } else {
                        ""
                    },
                    mood = state.collectionTitle?.takeIf { ids.isNotEmpty() },
                    trackIds = ids.toList(),
                )
            }.onSuccess { playlist ->
                _uiState.update {
                    it.copy(
                        playlistNameInput = "",
                        selectedTrackIds = emptySet(),
                        message = "Плейлист создан: ${playlist.name}",
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(message = error.message ?: "Не удалось создать плейлист", isError = true)
                }
            }
        }
    }

    fun addTrackToPlaylist(track: MusicTrackEntity, playlist: MusicPlaylistCard) {
        viewModelScope.launch {
            runCatching {
                musicRepository.addTracksToPlaylist(playlist.id, listOf(track.id))
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message = "Добавлено в «${playlist.name}»: ${track.title}",
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(message = error.message ?: "Не удалось добавить трек в плейлист", isError = true)
                }
            }
        }
    }

    fun addSelectionToPlaylist(playlist: MusicPlaylistCard) {
        val ids = _uiState.value.selectedTrackIds.toList()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(message = "Выберите треки для добавления", isError = true) }
            return
        }
        viewModelScope.launch {
            runCatching {
                musicRepository.addTracksToPlaylist(playlist.id, ids)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        selectedTrackIds = emptySet(),
                        message = "Добавлено в плейлист: ${playlist.name}",
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(message = error.message ?: "Не удалось обновить плейлист", isError = true)
                }
            }
        }
    }

    fun deletePlaylist(playlist: MusicPlaylistCard) {
        viewModelScope.launch {
            runCatching { musicRepository.deletePlaylist(playlist.id) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            message = "Плейлист удален: ${playlist.name}",
                            isError = false,
                            collectionTitle = if (it.collectionTitle == playlist.name) null else it.collectionTitle,
                            collectionTrackIds = if (it.collectionTitle == playlist.name) emptyList() else it.collectionTrackIds,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "Не удалось удалить плейлист", isError = true) }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }

    fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun updateMusicSettings(settings: MusicSettings) {
        settingsRepository.saveMusicSettings(settings)
        _uiState.update {
            val playerState = if (it.playerState.isServiceActive) {
                it.playerState
            } else {
                it.playerState.copy(
                    shuffleEnabled = settings.defaultShuffle,
                    repeatMode = settings.defaultRepeatMode,
                )
            }
            it.copy(settings = settings, playerState = playerState)
        }
    }

    fun importFolder(uri: Uri) {
        if (!ensureMusicCapability("Импорт музыки заблокирован.")) return
        runImportJob(
            inputJson = JSONObject()
                .put("kind", "folder")
                .put("uri", uri.toString())
                .toString(),
            action = { progress -> musicRepository.importFolder(uri, progress) },
        )
    }

    fun importTracks(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!ensureMusicCapability("Импорт музыки заблокирован.")) return
        runImportJob(
            inputJson = JSONObject()
                .put("kind", "tracks")
                .put("count", uris.size)
                .toString(),
            action = { progress -> musicRepository.importTracks(uris, progress) },
        )
    }

    fun rescanFolders() {
        if (!ensureMusicCapability("Пересканирование музыки заблокировано.")) return
        val folderSources = _uiState.value.sources.filter { it.sourceType == MusicSourceType.FOLDER.name }
        if (folderSources.isEmpty()) {
            _uiState.update {
                it.copy(
                    message = "Нет добавленных папок для пересканирования",
                    isError = true,
                )
            }
            return
        }
        viewModelScope.launch {
            folderSources.forEach { source ->
                rescanSource(source)
            }
        }
    }

    fun rescanSource(source: MusicSourceEntity) {
        if (!ensureMusicCapability("Пересканирование музыки заблокировано.")) return
        if (source.sourceType != MusicSourceType.FOLDER.name) {
            _uiState.update {
                it.copy(
                    message = "Отдельные треки нельзя пересканировать как папку. Добавь их повторно.",
                    isError = true,
                )
            }
            return
        }
        runImportJob(
            inputJson = JSONObject()
                .put("kind", "rescan_folder")
                .put("uri", source.uri)
                .toString(),
            action = { progress -> musicRepository.importFolder(Uri.parse(source.uri), progress) },
        )
    }

    fun removeSource(source: MusicSourceEntity) {
        if (!ensureMusicCapability("Удаление источника музыки заблокировано.")) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    musicRepository.removeSource(source.uri)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message = "Источник удален: ${source.displayName}",
                        isError = false,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        message = error.message ?: "Не удалось удалить источник",
                        isError = true,
                    )
                }
            }
        }
    }

    fun playTrack(track: MusicTrackEntity) {
        if (!ensureMusicCapability("Воспроизведение музыки заблокировано.")) return
        stopTtsPlayback()
        val queueIds = _uiState.value.visibleTracks.map { it.id }.ifEmpty { _uiState.value.tracks.map { it.id } }
        MusicPlaybackService.playQueue(appContext, queueIds, track.id)
        viewModelScope.launch {
            assistantEventLogger.logEvent(
                AssistantEvent(
                    type = "music_playback",
                    source = "music",
                    summary = "Запущен трек: ${track.title}",
                    payloadJson = JSONObject()
                        .put("track_id", track.id)
                        .put("title", track.title)
                        .put("artist", track.artist)
                        .toString(),
                )
            )
        }
    }

    fun playTrackIds(trackIds: List<Long>) {
        if (!ensureMusicCapability("Воспроизведение музыки заблокировано.")) return
        val ids = trackIds.distinct()
        if (ids.isEmpty()) {
            _uiState.update { it.copy(message = "В подборке нет треков", isError = true) }
            return
        }
        stopTtsPlayback()
        MusicPlaybackService.playQueue(appContext, ids, ids.firstOrNull())
        viewModelScope.launch {
            assistantEventLogger.logEvent(
                AssistantEvent(
                    type = "music_collection_playback",
                    source = "music",
                    summary = "Запущена подборка: ${ids.size} треков",
                    payloadJson = JSONObject().put("count", ids.size).toString(),
                )
            )
        }
    }

    fun togglePlayback() {
        if (!ensureMusicCapability("Воспроизведение музыки заблокировано.")) return
        if (_uiState.value.tracks.isEmpty()) {
            _uiState.update {
                it.copy(
                    message = "Медиатека пуста. Сначала добавь папку или треки.",
                    isError = true,
                )
            }
            return
        }
        val controller = activeController()
        if (controller?.isPlaying == true || _uiState.value.playerState.isPlaying) {
            controller?.pause() ?: MusicPlaybackService.pause(appContext)
        } else {
            stopTtsPlayback()
            if (controller != null && controller.mediaItemCount > 0) {
                controller.prepare()
                controller.play()
            } else {
                MusicPlaybackService.play(appContext)
            }
        }
    }

    fun nextTrack() {
        if (_uiState.value.playerState.queueSize == 0) return
        val controller = activeController()
        when {
            controller == null -> MusicPlaybackService.next(appContext)
            controller.hasNextMediaItem() -> controller.seekToNextMediaItem()
            controller.repeatMode == Player.REPEAT_MODE_ALL && controller.mediaItemCount > 0 -> controller.seekTo(0, 0L)
        }
    }

    fun previousTrack() {
        if (_uiState.value.playerState.queueSize == 0) return
        val controller = activeController()
        when {
            controller == null -> MusicPlaybackService.previous(appContext)
            controller.hasPreviousMediaItem() -> controller.seekToPreviousMediaItem()
            controller.mediaItemCount > 0 -> controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        activeController()?.seekTo(positionMs.coerceAtLeast(0L))
            ?: MusicPlaybackService.seekTo(appContext, positionMs)
    }

    fun toggleShuffle() {
        val enabled = !_uiState.value.playerState.shuffleEnabled
        val nextSettings = _uiState.value.settings.copy(defaultShuffle = enabled)
        updateMusicSettings(nextSettings)
        activeController()?.setShuffleModeEnabled(enabled)
            ?: MusicPlaybackService.setShuffle(appContext, enabled)
    }

    fun cycleRepeatMode() {
        val next = when (_uiState.value.playerState.repeatMode) {
            MusicRepeatMode.OFF -> MusicRepeatMode.ALL
            MusicRepeatMode.ALL -> MusicRepeatMode.ONE
            MusicRepeatMode.ONE -> MusicRepeatMode.OFF
        }
        updateMusicSettings(_uiState.value.settings.copy(defaultRepeatMode = next))
        activeController()?.setRepeatMode(next.toPlayerRepeatMode())
            ?: MusicPlaybackService.setRepeat(appContext, next)
    }

    fun stopPlayback() {
        MusicPlaybackService.stop(appContext)
    }

    private fun runImportJob(
        inputJson: String,
        action: suspend (ToolJobProgressSink) -> com.soll.domain.music.MusicImportSummary,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, message = null, isError = false) }
            var userMessage = ""
            val job = toolJobRunner.run(
                toolId = MUSIC_SCAN_TOOL_ID,
                inputJson = inputJson,
                handler = object : ToolHandler {
                    override val toolId: String = MUSIC_SCAN_TOOL_ID

                    override suspend fun execute(job: ToolJob, progress: ToolJobProgressSink): ToolJobResult {
                        val summary = action(progress)
                        userMessage = summary.toUserMessage()
                        return ToolJobResult(
                            outputJson = JSONObject()
                                .put("source", summary.sourceLabel)
                                .put("scanned", summary.scannedCount)
                                .put("imported", summary.importedCount)
                                .put("updated", summary.updatedCount)
                                .put("skipped", summary.skippedCount)
                                .toString(),
                            logText = userMessage,
                        )
                    }
                },
            )
            val ok = job.status == ToolJobStatus.SUCCESS
            _uiState.update {
                it.copy(
                    isImporting = false,
                    message = if (ok) userMessage.ifBlank { "Музыка импортирована" } else job.logText.ifBlank { "Импорт не выполнен" },
                    isError = !ok,
                )
            }
            assistantEventLogger.logEvent(
                AssistantEvent(
                    type = if (ok) "music_imported" else "music_import_failed",
                    source = "music",
                    summary = if (ok) userMessage.ifBlank { "Музыка импортирована" } else "Импорт музыки не выполнен",
                    payloadJson = job.outputJson ?: job.inputJson,
                )
            )
        }
    }

    private fun ensureMusicCapability(prefix: String): Boolean {
        val decision = capabilityRegistry.checkCommand(MUSIC_CAPABILITY_ID)
        if (decision.allowed) return true
        val message = "$prefix ${decision.message.ifBlank { "Включите возможность «Музыка» в настройках." }}"
        _uiState.update { it.copy(message = message, isError = true) }
        viewModelScope.launch {
            assistantEventLogger.logEvent(
                AssistantEvent(
                    type = "music_capability_blocked",
                    source = "music",
                    summary = message,
                )
            )
        }
        return false
    }

    private fun stopTtsPlayback() {
        ttsManager.stop()
        TtsService.stop(appContext)
    }

    private fun connectMediaController() {
        val token = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { controller ->
                        mediaController = controller
                        controller.addListener(controllerListener)
                        publishControllerState(controller)
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                message = error.message ?: "Не удалось подключить управление музыкой",
                                isError = true,
                            )
                        }
                    }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun activeController(): MediaController? =
        mediaController?.takeIf { it.isConnected }

    private fun publishControllerState(player: Player) {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        _uiState.update {
            it.copy(
                playerState = MusicPlayerState(
                    isServiceActive = player.mediaItemCount > 0 ||
                        player.playbackState != Player.STATE_IDLE ||
                        player.isPlaying,
                    isPreparing = player.playbackState == Player.STATE_BUFFERING,
                    currentTrackId = item?.mediaId?.toLongOrNull(),
                    title = metadata?.title?.toString()?.takeIf { title -> title.isNotBlank() } ?: "Музыка",
                    artist = metadata?.artist?.toString()?.takeIf { artist -> artist.isNotBlank() },
                    album = metadata?.albumTitle?.toString()?.takeIf { album -> album.isNotBlank() },
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition.takeIf { position -> position != C.TIME_UNSET } ?: 0L,
                    durationMs = duration,
                    shuffleEnabled = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode.toMusicRepeatMode(),
                    queueSize = player.mediaItemCount,
                    statusText = null,
                    errorMessage = player.playerError?.message,
                ),
            )
        }
    }

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

    private companion object {
        const val MUSIC_CAPABILITY_ID = "music"
        const val MUSIC_SCAN_TOOL_ID = "music_scan"
    }
}
