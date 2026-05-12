package com.soll.presentation.screens.tools.music

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.soll.data.local.entity.MusicSourceEntity
import com.soll.data.local.entity.MusicTrackEntity
import com.soll.domain.music.MusicAlbumCard
import com.soll.domain.music.MusicArtistCard
import com.soll.domain.music.MusicGenreCard
import com.soll.domain.music.MusicLibraryInsights
import com.soll.domain.music.MusicLibraryView
import com.soll.domain.music.MusicPlayerState
import com.soll.domain.music.MusicPlaylistCard
import com.soll.domain.music.MusicRepeatMode
import com.soll.domain.music.MusicRecommendationShelf
import com.soll.domain.music.MusicSettings
import com.soll.domain.music.MusicSourceType
import com.soll.ui.components.PassiveChip

private val MUSIC_MIME_TYPES = arrayOf(
    "audio/mpeg",
    "audio/mp4",
    "audio/aac",
    "audio/flac",
    "audio/ogg",
    "audio/opus",
    "audio/wav",
    "audio/x-wav",
    "application/ogg",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MusicScreen(
    onBack: () -> Unit,
    viewModel: MusicViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        uri?.let(viewModel::importFolder)
    }
    val trackPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.importTracks(uris)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Музыка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки музыки")
                    }
                    IconButton(onClick = viewModel::rescanFolders) {
                        Icon(Icons.Default.Refresh, contentDescription = "Пересканировать папки")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            uiState.message?.let { message ->
                AssistChip(
                    onClick = viewModel::clearMessage,
                    label = { Text(message, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                )
            }

            MusicPlayerPanel(
                state = uiState.playerState,
                onTogglePlayback = viewModel::togglePlayback,
                onPrevious = viewModel::previousTrack,
                onNext = viewModel::nextTrack,
                onStop = viewModel::stopPlayback,
                onSeek = viewModel::seekTo,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                canStartPlayback = uiState.tracks.isNotEmpty(),
            )

            MusicActionsRow(
                tracksCount = uiState.tracks.size,
                sourcesCount = uiState.sources.size,
                onAddFolder = { folderPicker.launch(null) },
                onAddTracks = { trackPicker.launch(MUSIC_MIME_TYPES) },
            )

            MusicLibraryTabs(
                selected = uiState.libraryView,
                onSelect = viewModel::selectLibraryView,
            )

            if (uiState.showSettings) {
                MusicSettingsPanel(
                    settings = uiState.settings,
                    onSettingsChange = viewModel::updateMusicSettings,
                )
            }

            if (uiState.sources.isNotEmpty()) {
                MusicSourcesPanel(
                    sources = uiState.sources,
                    onRescan = viewModel::rescanSource,
                    onRemove = viewModel::removeSource,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 6.dp),
            ) {
                MusicLibraryContent(
                    uiState = uiState,
                    isImporting = uiState.isImporting,
                    onAddFolder = { folderPicker.launch(null) },
                    onAddTracks = { trackPicker.launch(MUSIC_MIME_TYPES) },
                    onSearchChange = viewModel::updateSearchQuery,
                    onClearCollection = viewModel::clearCollectionFilter,
                    onOpenCollection = viewModel::openCollection,
                    onOpenPlaylist = viewModel::openPlaylist,
                onPlayTrack = viewModel::playTrack,
                onPlayIds = viewModel::playTrackIds,
                onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                onToggleTrackSelection = viewModel::toggleTrackSelection,
                onSelectVisible = viewModel::selectVisibleTracks,
                onClearSelection = viewModel::clearTrackSelection,
                    onPlaylistNameChange = viewModel::updatePlaylistName,
                    onCreatePlaylist = viewModel::createPlaylistFromSelection,
                    onAddSelectionToPlaylist = viewModel::addSelectionToPlaylist,
                    onDeletePlaylist = viewModel::deletePlaylist,
                )
            }
        }
    }
}

@Composable
private fun MusicLibraryTabs(
    selected: MusicLibraryView,
    onSelect: (MusicLibraryView) -> Unit,
) {
    val tabs = listOf(
        MusicLibraryView.OVERVIEW to "Обзор",
        MusicLibraryView.TRACKS to "Треки",
        MusicLibraryView.ARTISTS to "Артисты",
        MusicLibraryView.ALBUMS to "Альбомы",
        MusicLibraryView.GENRES to "Жанры",
        MusicLibraryView.PLAYLISTS to "Плейлисты",
    )
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        edgePadding = 0.dp,
        divider = {},
    ) {
        tabs.forEach { (view, label) ->
            Tab(
                selected = selected == view,
                onClick = { onSelect(view) },
                text = { Text(label, maxLines = 1) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MusicLibraryContent(
    uiState: MusicUiState,
    isImporting: Boolean,
    onAddFolder: () -> Unit,
    onAddTracks: () -> Unit,
    onSearchChange: (String) -> Unit,
    onClearCollection: () -> Unit,
    onOpenCollection: (String, String, List<Long>) -> Unit,
    onOpenPlaylist: (MusicPlaylistCard) -> Unit,
    onPlayTrack: (MusicTrackEntity) -> Unit,
    onPlayIds: (List<Long>) -> Unit,
    onAddTrackToPlaylist: (MusicTrackEntity, MusicPlaylistCard) -> Unit,
    onToggleTrackSelection: (Long) -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onPlaylistNameChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onAddSelectionToPlaylist: (MusicPlaylistCard) -> Unit,
    onDeletePlaylist: (MusicPlaylistCard) -> Unit,
) {
    if (uiState.tracks.isEmpty()) {
        EmptyMusicLibrary(
            isImporting = isImporting,
            onAddFolder = onAddFolder,
            onAddTracks = onAddTracks,
        )
        return
    }

    when (uiState.libraryView) {
        MusicLibraryView.OVERVIEW -> MusicOverview(
            insights = uiState.insights,
            playlists = uiState.playlists,
            onOpenCollection = onOpenCollection,
            onOpenPlaylist = onOpenPlaylist,
            onPlayIds = onPlayIds,
        )
        MusicLibraryView.TRACKS -> MusicTracksView(
            uiState = uiState,
            onSearchChange = onSearchChange,
            onClearCollection = onClearCollection,
            onPlayTrack = onPlayTrack,
            onPlayIds = onPlayIds,
            onAddTrackToPlaylist = onAddTrackToPlaylist,
            onToggleTrackSelection = onToggleTrackSelection,
            onSelectVisible = onSelectVisible,
            onClearSelection = onClearSelection,
            onAddSelectionToPlaylist = onAddSelectionToPlaylist,
        )
        MusicLibraryView.ARTISTS -> CardGridList {
            items(uiState.insights.artists, key = { it.name }) { artist ->
                ArtistCard(
                    artist = artist,
                    onOpen = { onOpenCollection(artist.name, "${artist.trackCount} треков · ${artist.albumCount} альбомов", artist.trackIds) },
                    onPlay = { onPlayIds(artist.trackIds) },
                )
            }
        }
        MusicLibraryView.ALBUMS -> CardGridList {
            items(uiState.insights.albums, key = { "${it.artist}|${it.title}" }) { album ->
                AlbumCard(
                    album = album,
                    onOpen = { onOpenCollection(album.title, listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "), album.trackIds) },
                    onPlay = { onPlayIds(album.trackIds) },
                )
            }
        }
        MusicLibraryView.GENRES -> CardGridList {
            items(uiState.insights.genres, key = { it.name }) { genre ->
                GenreCard(
                    genre = genre,
                    onOpen = { onOpenCollection(genre.name, "${genre.trackCount} треков · ${genre.artistCount} артистов", genre.trackIds) },
                    onPlay = { onPlayIds(genre.trackIds) },
                )
            }
        }
        MusicLibraryView.PLAYLISTS -> PlaylistsView(
            uiState = uiState,
            onPlaylistNameChange = onPlaylistNameChange,
            onCreatePlaylist = onCreatePlaylist,
            onOpenPlaylist = onOpenPlaylist,
            onDeletePlaylist = onDeletePlaylist,
        )
    }
}

@Composable
private fun CardGridList(
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun MusicOverview(
    insights: MusicLibraryInsights,
    playlists: List<MusicPlaylistCard>,
    onOpenCollection: (String, String, List<Long>) -> Unit,
    onOpenPlaylist: (MusicPlaylistCard) -> Unit,
    onPlayIds: (List<Long>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LibraryStatsCard(insights = insights)
        }
        item {
            RecommendationShelves(
                shelves = insights.recommendations,
                onOpen = { shelf -> onOpenCollection(shelf.title, shelf.subtitle, shelf.trackIds) },
                onPlay = { shelf -> onPlayIds(shelf.trackIds) },
            )
        }
        if (playlists.isNotEmpty()) {
            item {
                Text(
                    text = "Плейлисты",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        CompactPlaylistCard(
                            playlist = playlist,
                            onOpen = { onOpenPlaylist(playlist) },
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "Исполнители",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(insights.artists.take(12), key = { it.name }) { artist ->
                    CompactCollectionCard(
                        title = artist.name,
                        subtitle = "${artist.trackCount} треков",
                        icon = Icons.Default.Person,
                        onOpen = { onOpenCollection(artist.name, "${artist.albumCount} альбомов", artist.trackIds) },
                    )
                }
            }
        }
        item {
            Text(
                text = "Альбомы",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(insights.albums.take(12), key = { "${it.artist}|${it.title}" }) { album ->
                    CompactCollectionCard(
                        title = album.title,
                        subtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                        icon = Icons.Default.Album,
                        onOpen = { onOpenCollection(album.title, album.artist.orEmpty(), album.trackIds) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryStatsCard(insights: MusicLibraryInsights) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        ),
    ) {
        FlowRow(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip("Треки", insights.trackCount.toString())
            StatChip("Артисты", insights.artistCount.toString())
            StatChip("Альбомы", insights.albumCount.toString())
            StatChip("Жанры", insights.genreCount.toString())
            StatChip("Время", formatDuration(insights.totalDurationMs))
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    PassiveChip(text = "$label: $value")
}

@Composable
private fun RecommendationShelves(
    shelves: List<MusicRecommendationShelf>,
    onOpen: (MusicRecommendationShelf) -> Unit,
    onPlay: (MusicRecommendationShelf) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Предложка Soll",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shelves, key = { it.id }) { shelf ->
                RecommendationCard(
                    shelf = shelf,
                    onOpen = { onOpen(shelf) },
                    onPlay = { onPlay(shelf) },
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    shelf: MusicRecommendationShelf,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onOpen),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = shelf.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = shelf.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Играть")
            }
        }
    }
}

@Composable
private fun MusicTracksView(
    uiState: MusicUiState,
    onSearchChange: (String) -> Unit,
    onClearCollection: () -> Unit,
    onPlayTrack: (MusicTrackEntity) -> Unit,
    onPlayIds: (List<Long>) -> Unit,
    onAddTrackToPlaylist: (MusicTrackEntity, MusicPlaylistCard) -> Unit,
    onToggleTrackSelection: (Long) -> Unit,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onAddSelectionToPlaylist: (MusicPlaylistCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        uiState.collectionTitle?.let { title ->
            CollectionFilterCard(
                title = title,
                subtitle = uiState.collectionSubtitle.orEmpty(),
                count = uiState.collectionTrackIds.size,
                onPlay = { onPlayIds(uiState.collectionTrackIds) },
                onClear = onClearCollection,
            )
        }
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            label = { Text("Поиск: трек, артист, альбом, жанр") },
        )
        TrackSelectionBar(
            selectedCount = uiState.selectedTrackIds.size,
            visibleCount = uiState.visibleTracks.size,
            onSelectVisible = onSelectVisible,
            onClearSelection = onClearSelection,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(uiState.visibleTracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = uiState.playerState.currentTrackId == track.id,
                    isPlaying = uiState.playerState.currentTrackId == track.id && uiState.playerState.isPlaying,
                    selected = track.id in uiState.selectedTrackIds,
                    playlists = uiState.playlists,
                    onToggleSelected = { onToggleTrackSelection(track.id) },
                    onAddToPlaylist = { playlist -> onAddTrackToPlaylist(track, playlist) },
                    onPlay = { onPlayTrack(track) },
                )
            }
        }
        if (uiState.selectedTrackIds.isNotEmpty()) {
            SelectedTracksPlaylistDock(
                selectedCount = uiState.selectedTrackIds.size,
                playlists = uiState.playlists,
                onAddSelectionToPlaylist = onAddSelectionToPlaylist,
            )
        }
    }
}

@Composable
private fun CollectionFilterCard(
    title: String,
    subtitle: String,
    count: Int,
    onPlay: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = listOf(subtitle, "$count треков").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Играть")
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Сбросить")
            }
        }
    }
}

@Composable
private fun TrackSelectionBar(
    selectedCount: Int,
    visibleCount: Int,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Выбрано: $selectedCount",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSelectVisible, enabled = visibleCount > 0) { Text("Все") }
            TextButton(onClick = onClearSelection, enabled = selectedCount > 0) { Text("Снять") }
        }
    }
}

@Composable
private fun SelectedTracksPlaylistDock(
    selectedCount: Int,
    playlists: List<MusicPlaylistCard>,
    onAddSelectionToPlaylist: (MusicPlaylistCard) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f),
        ),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Добавить выбранные: $selectedCount",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (playlists.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(playlists.take(12), key = { it.id }) { playlist ->
                        FilterChip(
                            selected = false,
                            onClick = { onAddSelectionToPlaylist(playlist) },
                            label = { Text("+ ${playlist.name}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            } else {
                Text(
                    text = "Сначала создай плейлист во вкладке «Плейлисты».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArtistCard(
    artist: MusicArtistCard,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    CollectionRowCard(
        icon = Icons.Default.Person,
        title = artist.name,
        subtitle = "${artist.trackCount} треков · ${artist.albumCount} альбомов · запусков ${artist.playCount}",
        onOpen = onOpen,
        onPlay = onPlay,
    )
}

@Composable
private fun AlbumCard(
    album: MusicAlbumCard,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    CollectionRowCard(
        icon = Icons.Default.Album,
        title = album.title,
        subtitle = listOfNotNull(album.artist, album.year?.toString(), "${album.trackCount} треков", formatDuration(album.durationMs)).joinToString(" · "),
        onOpen = onOpen,
        onPlay = onPlay,
    )
}

@Composable
private fun GenreCard(
    genre: MusicGenreCard,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    CollectionRowCard(
        icon = Icons.Default.Category,
        title = genre.name,
        subtitle = "${genre.trackCount} треков · ${genre.artistCount} артистов",
        onOpen = onOpen,
        onPlay = onPlay,
    )
}

@Composable
private fun CollectionRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPlay) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Играть")
            }
        }
    }
}

@Composable
private fun CompactCollectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .width(168.dp)
            .clickable(onClick = onOpen),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactPlaylistCard(
    playlist: MusicPlaylistCard,
    onOpen: () -> Unit,
) {
    CompactCollectionCard(
        title = playlist.name,
        subtitle = "${playlist.trackCount} треков",
        icon = Icons.AutoMirrored.Filled.QueueMusic,
        onOpen = onOpen,
    )
}

@Composable
private fun PlaylistsView(
    uiState: MusicUiState,
    onPlaylistNameChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (MusicPlaylistCard) -> Unit,
    onDeletePlaylist: (MusicPlaylistCard) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PlaylistCreatePanel(
                selectedCount = uiState.selectedTrackIds.size,
                playlistName = uiState.playlistNameInput,
                onPlaylistNameChange = onPlaylistNameChange,
                onCreatePlaylist = onCreatePlaylist,
            )
        }
        if (uiState.playlists.isEmpty()) {
            item {
                Text(
                    text = "Плейлистов пока нет. Выбери треки и создай первый список.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            items(uiState.playlists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpenPlaylist(playlist) },
                    onDelete = { onDeletePlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistCreatePanel(
    selectedCount: Int,
    playlistName: String,
    onPlaylistNameChange: (String) -> Unit,
    onCreatePlaylist: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Новый плейлист",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (selectedCount > 0) {
                    "Создать с выбранными треками: $selectedCount"
                } else {
                    "Создать пустой плейлист, а треки добавить из вкладки «Треки»."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = onPlaylistNameChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Название") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                )
                Button(onClick = onCreatePlaylist, enabled = playlistName.isNotBlank()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Создать")
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: MusicPlaylistCard,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull("${playlist.trackCount} треков", playlist.mood).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Удалить")
            }
        }
    }
}

@Composable
private fun MusicPlayerPanel(
    state: MusicPlayerState,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    canStartPlayback: Boolean,
) {
    var pendingSeek by remember(state.currentTrackId) { mutableFloatStateOf(state.positionMs.toFloat()) }
    val duration = state.durationMs ?: 0L
    val hasQueue = state.queueSize > 0
    LaunchedEffect(state.currentTrackId, state.positionMs) {
        pendingSeek = state.positionMs.toFloat()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.statusText?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = listOfNotNull(state.artist, state.album).joinToString(" · ").ifBlank { "Локальная медиатека" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.isPreparing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            if (duration > 0L) {
                Slider(
                    value = pendingSeek.coerceIn(0f, duration.toFloat()),
                    onValueChange = { pendingSeek = it },
                    onValueChangeFinished = { onSeek(pendingSeek.toLong()) },
                    valueRange = 0f..duration.toFloat(),
                    enabled = hasQueue,
                )
            } else {
                LinearProgressIndicator(
                    progress = { 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(state.positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (duration > 0L) formatDuration(duration) else "—:—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Перемешать",
                        tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onPrevious, enabled = hasQueue) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий трек")
                }
                IconButton(onClick = onTogglePlayback, enabled = canStartPlayback || hasQueue) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "Пауза" else "Играть",
                        modifier = Modifier.size(34.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onNext, enabled = hasQueue) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Следующий трек")
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (state.repeatMode == MusicRepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Повтор",
                        tint = if (state.repeatMode != MusicRepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onStop, enabled = hasQueue || state.isServiceActive) {
                    Icon(Icons.Default.Stop, contentDescription = "Стоп")
                }
            }

            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun MusicActionsRow(
    tracksCount: Int,
    sourcesCount: Int,
    onAddFolder: () -> Unit,
    onAddTracks: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onAddFolder,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Папка")
        }
        OutlinedButton(
            onClick = onAddTracks,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Треки")
        }
        AssistChip(
            onClick = onAddTracks,
            label = { Text("$tracksCount") },
            leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
        AssistChip(
            onClick = onAddFolder,
            label = { Text("$sourcesCount") },
            leadingIcon = { Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun MusicSettingsPanel(
    settings: MusicSettings,
    onSettingsChange: (MusicSettings) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Настройки музыки",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            SettingSwitchRow(
                title = "Продолжать последний трек",
                checked = settings.resumeLastTrack,
                onCheckedChange = { onSettingsChange(settings.copy(resumeLastTrack = it)) },
            )
            SettingSwitchRow(
                title = "Ставить музыку на паузу перед чтением",
                checked = settings.pauseMusicForTts,
                onCheckedChange = { onSettingsChange(settings.copy(pauseMusicForTts = it)) },
            )
            SettingSwitchRow(
                title = "Останавливать чтение при старте музыки",
                checked = settings.stopTtsOnMusicStart,
                onCheckedChange = { onSettingsChange(settings.copy(stopTtsOnMusicStart = it)) },
            )
            SettingSwitchRow(
                title = "Кнопки гарнитуры и Bluetooth",
                checked = settings.headsetControlsEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(headsetControlsEnabled = it)) },
            )
            SettingSwitchRow(
                title = "Автопересканирование при открытии",
                checked = settings.autoRescanOnOpen,
                onCheckedChange = { onSettingsChange(settings.copy(autoRescanOnOpen = it)) },
            )
            SettingSwitchRow(
                title = "Строгая проверка аудиоформатов",
                checked = settings.strictAudioFilter,
                onCheckedChange = { onSettingsChange(settings.copy(strictAudioFilter = it)) },
            )
            SettingSwitchRow(
                title = "Показывать подсказки фонового режима",
                checked = settings.showBackgroundHints,
                onCheckedChange = { onSettingsChange(settings.copy(showBackgroundHints = it)) },
            )
            if (settings.showBackgroundHints) {
                HorizontalDivider()
                Text(
                    text = "Фон работает через системную Media3-сессию и медиа-уведомление. Для надежной работы оставь уведомления Soll разрешенными.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun MusicSourcesPanel(
    sources: List<MusicSourceEntity>,
    onRescan: (MusicSourceEntity) -> Unit,
    onRemove: (MusicSourceEntity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Источники",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            sources.forEachIndexed { index, source ->
                if (index > 0) HorizontalDivider()
                SourceRow(
                    source = source,
                    onRescan = { onRescan(source) },
                    onRemove = { onRemove(source) },
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: MusicSourceEntity,
    onRescan: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (source.sourceType == MusicSourceType.FOLDER.name) Icons.Default.FolderOpen else Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${source.trackCount} треков",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        source.lastError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (source.sourceType == MusicSourceType.FOLDER.name) {
                TextButton(onClick = onRescan) {
                    Text("Пересканировать")
                }
            }
            TextButton(onClick = onRemove) {
                Text("Удалить")
            }
        }
    }
}

@Composable
private fun EmptyMusicLibrary(
    isImporting: Boolean,
    onAddFolder: () -> Unit,
    onAddTracks: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            if (isImporting) {
                CircularProgressIndicator()
            } else {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "Музыка пока не добавлена",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Выбери папку с музыкой или добавь отдельные композиции",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddFolder) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Папка")
                }
                OutlinedButton(onClick = onAddTracks) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Треки")
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: MusicTrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    selected: Boolean,
    playlists: List<MusicPlaylistCard>,
    onToggleSelected: () -> Unit,
    onAddToPlaylist: (MusicPlaylistCard) -> Unit,
    onPlay: () -> Unit,
) {
    var playlistMenuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelected() },
            )
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.MusicNote,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(track.artist, track.album).joinToString(" · ").ifBlank { track.displayName },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.musicMetaLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = track.durationMs?.let(::formatDuration) ?: "—:—",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (playlists.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    IconButton(onClick = { playlistMenuExpanded = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Добавить в плейлист",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    DropdownMenu(
                        expanded = playlistMenuExpanded,
                        onDismissRequest = { playlistMenuExpanded = false },
                    ) {
                        playlists.forEach { playlist ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = playlist.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    playlistMenuExpanded = false
                                    onAddToPlaylist(playlist)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun MusicTrackEntity.musicMetaLine(): String =
    listOfNotNull(
        genre?.takeIf { it.isNotBlank() },
        year?.toString(),
        trackNumber?.let { "№$it" },
        bitrate?.takeIf { it > 0 }?.let { "${it / 1000} кбит/с" },
    ).joinToString(" · ").ifBlank { "Метаданные из файла" }
