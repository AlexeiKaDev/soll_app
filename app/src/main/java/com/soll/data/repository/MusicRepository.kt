package com.soll.data.repository

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.soll.data.local.dao.MusicDao
import com.soll.data.local.entity.MusicPlaybackStateEntity
import com.soll.data.local.entity.MusicPlaylistEntity
import com.soll.data.local.entity.MusicPlaylistTrackEntity
import com.soll.data.local.entity.MusicSourceEntity
import com.soll.data.local.entity.MusicSourceTrackEntity
import com.soll.data.local.entity.MusicTrackEntity
import com.soll.domain.music.MusicAlbumCard
import com.soll.domain.music.MusicArtistCard
import com.soll.domain.music.MusicFileSupport
import com.soll.domain.music.MusicGenreCard
import com.soll.domain.music.MusicImportSummary
import com.soll.domain.music.MusicLibraryInsights
import com.soll.domain.music.MusicPlaylistCard
import com.soll.domain.music.MusicRecommendationShelf
import com.soll.domain.music.MusicRepeatMode
import com.soll.domain.music.MusicSourceType
import com.soll.domain.tool.ToolJobProgressSink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val settingsRepository: SettingsRepository,
) {
    fun observeSources(): Flow<List<MusicSourceEntity>> = musicDao.observeSources()

    fun observeTracks(): Flow<List<MusicTrackEntity>> = musicDao.observeTracks()

    fun observePlaylists(): Flow<List<MusicPlaylistEntity>> = musicDao.observePlaylists()

    suspend fun observeSourcesSnapshot(): List<MusicSourceEntity> = withContext(Dispatchers.IO) {
        musicDao.getSources()
    }

    suspend fun getAllTracks(): List<MusicTrackEntity> = withContext(Dispatchers.IO) {
        musicDao.getAllTracks()
    }

    suspend fun getTrack(id: Long): MusicTrackEntity? = withContext(Dispatchers.IO) {
        musicDao.getTrack(id)
    }

    suspend fun getTracksForSource(sourceUri: String): List<MusicTrackEntity> = withContext(Dispatchers.IO) {
        musicDao.getTracksForSource(sourceUri)
    }

    suspend fun getTracksByIds(ids: List<Long>): List<MusicTrackEntity> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = musicDao.getTracksByIds(ids).associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    suspend fun getPlaylists(): List<MusicPlaylistCard> = withContext(Dispatchers.IO) {
        musicDao.getPlaylists().map { it.toCard() }
    }

    suspend fun getTracksForPlaylist(playlistId: String): List<MusicTrackEntity> = withContext(Dispatchers.IO) {
        musicDao.getTracksForPlaylist(playlistId)
    }

    suspend fun buildLibraryInsights(): MusicLibraryInsights = withContext(Dispatchers.IO) {
        buildLibraryInsights(musicDao.getAllTracks())
    }

    fun buildLibraryInsights(tracks: List<MusicTrackEntity>): MusicLibraryInsights {
        val sortedTracks = tracks.sortedWith(
            compareByDescending<MusicTrackEntity> { it.playCount }
                .thenByDescending { it.lastPlayedAt ?: 0L }
                .thenBy { it.title.lowercase() },
        )
        val artists = tracks
            .groupBy { it.artistKey() }
            .filterKeys { it.isNotBlank() }
            .map { (artist, artistTracks) ->
                MusicArtistCard(
                    name = artist,
                    trackCount = artistTracks.size,
                    albumCount = artistTracks.mapNotNull { it.album?.takeIf(String::isNotBlank) }.distinctBy { it.lowercase() }.size,
                    playCount = artistTracks.sumOf { it.playCount },
                    lastPlayedAt = artistTracks.mapNotNull { it.lastPlayedAt }.maxOrNull(),
                    trackIds = artistTracks.sortedForPlayback().map { it.id },
                )
            }
            .sortedWith(compareByDescending<MusicArtistCard> { it.playCount }.thenByDescending { it.trackCount }.thenBy { it.name.lowercase() })

        val albums = tracks
            .filter { !it.album.isNullOrBlank() }
            .groupBy { "${it.albumArtistKey()}|${it.album.orEmpty().lowercase()}" }
            .map { (_, albumTracks) ->
                val first = albumTracks.first()
                MusicAlbumCard(
                    title = first.album.orEmpty(),
                    artist = first.albumArtist ?: first.artist,
                    year = albumTracks.mapNotNull { it.year }.minOrNull(),
                    genre = albumTracks.mapNotNull { it.genre }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                    trackCount = albumTracks.size,
                    durationMs = albumTracks.sumOf { it.durationMs ?: 0L },
                    playCount = albumTracks.sumOf { it.playCount },
                    trackIds = albumTracks.sortedForPlayback().map { it.id },
                )
            }
            .sortedWith(compareByDescending<MusicAlbumCard> { it.playCount }.thenBy { it.artist.orEmpty().lowercase() }.thenBy { it.title.lowercase() })

        val genres = tracks
            .flatMap { track -> track.genreParts().map { genre -> genre to track } }
            .groupBy({ it.first }, { it.second })
            .map { (genre, genreTracks) ->
                MusicGenreCard(
                    name = genre,
                    trackCount = genreTracks.size,
                    artistCount = genreTracks.map { it.artistKey().lowercase() }.filter { it.isNotBlank() }.distinct().size,
                    playCount = genreTracks.sumOf { it.playCount },
                    trackIds = genreTracks.sortedForPlayback().map { it.id },
                )
            }
            .sortedWith(compareByDescending<MusicGenreCard> { it.playCount }.thenByDescending { it.trackCount }.thenBy { it.name.lowercase() })

        return MusicLibraryInsights(
            trackCount = tracks.size,
            artistCount = artists.size,
            albumCount = albums.size,
            genreCount = genres.size,
            totalDurationMs = tracks.sumOf { it.durationMs ?: 0L },
            artists = artists,
            albums = albums,
            genres = genres,
            recommendations = buildRecommendationShelves(
                tracks = tracks,
                sortedTracks = sortedTracks,
                artists = artists,
                genres = genres,
            ),
        )
    }

    suspend fun importFolder(
        treeUri: Uri,
        progress: ToolJobProgressSink? = null,
    ): MusicImportSummary = withContext(Dispatchers.IO) {
        val sourceUri = treeUri.toString()
        runCatching {
            persistReadPermission(treeUri)
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Не удалось открыть выбранную папку")
            val sourceLabel = root.name?.takeIf { it.isNotBlank() } ?: "папка с музыкой"
            musicDao.upsertSource(
                MusicSourceEntity(
                    uri = sourceUri,
                    displayName = sourceLabel,
                    sourceType = MusicSourceType.FOLDER.name,
                )
            )

            progress?.appendLog("Сканирую папку: $sourceLabel")
            val documents = collectAudioDocuments(root, progress)
            val result = upsertTracksForSource(
                sourceUri = sourceUri,
                sourceLabel = sourceLabel,
                documents = documents,
                progress = progress,
                deleteMissing = true,
            )
            musicDao.updateSourceScanState(
                sourceUri = sourceUri,
                trackCount = musicDao.countTracksForSource(sourceUri),
                lastError = null,
                lastScannedAt = System.currentTimeMillis(),
            )
            result
        }.getOrElse { error ->
            musicDao.updateSourceScanState(
                sourceUri = sourceUri,
                trackCount = musicDao.countTracksForSource(sourceUri),
                lastError = error.message ?: "Ошибка сканирования папки",
                lastScannedAt = System.currentTimeMillis(),
            )
            throw error
        }
    }

    suspend fun importTracks(
        uris: List<Uri>,
        progress: ToolJobProgressSink? = null,
    ): MusicImportSummary = withContext(Dispatchers.IO) {
        val distinctUris = uris.distinctBy { it.toString() }
        distinctUris.forEach(::persistReadPermission)
        val sourceUri = "manual:${System.currentTimeMillis()}"
        val sourceLabel = "выбранные треки"
        musicDao.upsertSource(
            MusicSourceEntity(
                uri = sourceUri,
                displayName = sourceLabel,
                sourceType = MusicSourceType.TRACKS.name,
            )
        )
        runCatching {
            val documents = distinctUris.mapNotNull { uri ->
                val meta = queryDocumentMeta(uri)
                val name = meta.displayName ?: uri.lastPathSegment ?: uri.toString()
                val mime = meta.mimeType ?: context.contentResolver.getType(uri)
                if (!MusicFileSupport.isSupported(name, mime, settingsRepository.musicStrictAudioFilter)) return@mapNotNull null
                AudioDocument(uri, name, mime, meta.sizeBytes)
            }
            val result = upsertTracksForSource(
                sourceUri = sourceUri,
                sourceLabel = sourceLabel,
                documents = documents,
                progress = progress,
                deleteMissing = false,
            )
            musicDao.updateSourceScanState(
                sourceUri = sourceUri,
                trackCount = musicDao.countTracksForSource(sourceUri),
                lastError = null,
                lastScannedAt = System.currentTimeMillis(),
            )
            result.copy(scannedCount = distinctUris.size, skippedCount = distinctUris.size - documents.size + result.skippedCount)
        }.getOrElse { error ->
            musicDao.updateSourceScanState(
                sourceUri = sourceUri,
                trackCount = musicDao.countTracksForSource(sourceUri),
                lastError = error.message ?: "Ошибка импорта треков",
                lastScannedAt = System.currentTimeMillis(),
            )
            throw error
        }
    }

    suspend fun removeSource(sourceUri: String) = withContext(Dispatchers.IO) {
        musicDao.deleteSourceTrackLinks(sourceUri)
        musicDao.deleteOrphanTracks()
        musicDao.deleteMissingPlaylistTrackLinks()
        musicDao.deleteSource(sourceUri)
    }

    suspend fun createPlaylist(
        name: String,
        description: String = "",
        mood: String? = null,
        trackIds: List<Long> = emptyList(),
    ): MusicPlaylistCard = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Название плейлиста не задано" }
        val now = System.currentTimeMillis()
        val playlist = MusicPlaylistEntity(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            description = description.trim(),
            mood = mood?.trim()?.takeIf { it.isNotBlank() },
            coverSeed = cleanName.hashCode(),
            createdAt = now,
            updatedAt = now,
        )
        musicDao.upsertPlaylist(playlist)
        addTracksToPlaylist(playlist.id, trackIds)
        musicDao.getPlaylist(playlist.id)?.toCard() ?: playlist.toCard()
    }

    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<Long>) = withContext(Dispatchers.IO) {
        val playlist = musicDao.getPlaylist(playlistId) ?: error("Плейлист не найден")
        var position = musicDao.nextPlaylistPosition(playlistId)
        trackIds.distinct().forEach { trackId ->
            if (musicDao.getTrack(trackId) != null && musicDao.hasPlaylistTrack(playlistId, trackId) == 0) {
                musicDao.upsertPlaylistTrack(
                    MusicPlaylistTrackEntity(
                        playlistId = playlistId,
                        trackId = trackId,
                        position = position,
                    )
                )
                position += 1
            }
        }
        musicDao.updatePlaylistCount(playlistId, musicDao.countPlaylistTracks(playlistId))
        Timber.d("Playlist %s updated: %s", playlist.name, playlistId)
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: Long) = withContext(Dispatchers.IO) {
        musicDao.removePlaylistTrack(playlistId, trackId)
        musicDao.updatePlaylistCount(playlistId, musicDao.countPlaylistTracks(playlistId))
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        musicDao.deletePlaylistTracks(playlistId)
        musicDao.deletePlaylist(playlistId)
    }

    suspend fun markTrackPlayed(trackId: Long) = withContext(Dispatchers.IO) {
        musicDao.markTrackPlayed(trackId)
    }

    suspend fun savePlaybackState(
        currentTrackId: Long?,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: MusicRepeatMode,
        queueTrackIds: List<Long>,
    ) = withContext(Dispatchers.IO) {
        musicDao.upsertPlaybackState(
            MusicPlaybackStateEntity(
                currentTrackId = currentTrackId,
                positionMs = positionMs.coerceAtLeast(0L),
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode.name,
                queueTrackIdsCsv = queueTrackIds.joinToString(","),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun getPlaybackState(): MusicPlaybackStateEntity? = withContext(Dispatchers.IO) {
        musicDao.getPlaybackState()
    }

    private suspend fun upsertTracksForSource(
        sourceUri: String,
        sourceLabel: String,
        documents: List<AudioDocument>,
        progress: ToolJobProgressSink?,
        deleteMissing: Boolean,
    ): MusicImportSummary {
        var imported = 0
        var updated = 0
        var skipped = 0
        val scannedUris = mutableListOf<String>()
        val total = documents.size.coerceAtLeast(1)

        documents.forEachIndexed { index, document ->
            val track = runCatching {
                buildTrackEntity(sourceUri, document)
            }.onFailure { error ->
                Timber.w(error, "Failed to read music metadata for %s", document.uri)
            }.getOrNull()

            if (track == null) {
                skipped += 1
            } else {
                scannedUris += track.uri
                val existing = musicDao.getTrackByUri(track.uri)
                if (existing == null) {
                    musicDao.insertTrack(track)
                    imported += 1
                } else {
                    musicDao.updateTrack(
                        track.copy(
                            id = existing.id,
                            sourceUri = existing.sourceUri ?: sourceUri,
                            addedAt = existing.addedAt,
                            lastPlayedAt = existing.lastPlayedAt,
                            playCount = existing.playCount,
                        )
                    )
                    updated += 1
                }
                musicDao.upsertSourceTrack(
                    MusicSourceTrackEntity(
                        sourceUri = sourceUri,
                        trackUri = track.uri,
                    )
                )
            }
            if (index % 10 == 0 || index == documents.lastIndex) {
                progress?.updateProgress(
                    ((index + 1) * 100 / total).coerceIn(0, 99),
                    "Проверено ${index + 1} из ${documents.size}",
                )
            }
        }

        if (deleteMissing) {
            if (scannedUris.isEmpty()) {
                musicDao.deleteSourceTrackLinks(sourceUri)
            } else {
                musicDao.deleteMissingSourceTrackLinks(sourceUri, scannedUris)
            }
            musicDao.deleteOrphanTracks()
            musicDao.deleteMissingPlaylistTrackLinks()
        }
        return MusicImportSummary(
            sourceLabel = sourceLabel,
            scannedCount = documents.size,
            importedCount = imported,
            updatedCount = updated,
            skippedCount = skipped,
        )
    }

    private suspend fun collectAudioDocuments(
        root: DocumentFile,
        progress: ToolJobProgressSink?,
    ): List<AudioDocument> {
        val queue = ArrayDeque<DocumentFile>()
        val result = mutableListOf<AudioDocument>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            visited += 1
            current.listFiles().forEach { child ->
                when {
                    child.isDirectory -> queue.add(child)
                    child.isFile -> {
                        val name = child.name ?: return@forEach
                        val mime = child.type
                        if (MusicFileSupport.isSupported(name, mime, settingsRepository.musicStrictAudioFilter)) {
                            result += AudioDocument(
                                uri = child.uri,
                                displayName = name,
                                mimeType = mime,
                                sizeBytes = child.length().takeIf { it >= 0L },
                            )
                        }
                    }
                }
            }
            if (visited % 25 == 0) {
                progress?.appendLog("Просмотрено папок: $visited, найдено аудио: ${result.size}")
            }
        }
        return result
    }

    private fun buildTrackEntity(sourceUri: String, document: AudioDocument): MusicTrackEntity {
        val metadata = readMediaMetadata(document.uri)
        val title = metadata.title
            ?: MusicFileSupport.cleanTitle(document.displayName)
        return MusicTrackEntity(
            sourceUri = sourceUri,
            uri = document.uri.toString(),
            displayName = document.displayName,
            title = title,
            artist = metadata.artist,
            albumArtist = metadata.albumArtist ?: metadata.artist,
            album = metadata.album,
            genre = metadata.genre,
            year = metadata.year,
            trackNumber = metadata.trackNumber,
            discNumber = metadata.discNumber,
            composer = metadata.composer,
            bitrate = metadata.bitrate,
            durationMs = metadata.durationMs,
            mimeType = document.mimeType,
            sizeBytes = document.sizeBytes,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun readMediaMetadata(uri: Uri): TrackMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            TrackMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() },
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() },
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.takeIf { it.isNotBlank() },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() },
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    ?.takeIf { it.isNotBlank() },
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?.takeDigits()
                    ?.toIntOrNull(),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.parseTrackNumber(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    ?.parseTrackNumber(),
                composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                    ?.takeIf { it.isNotBlank() },
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 },
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L },
            )
        } catch (error: Exception) {
            Timber.w(error, "Music metadata unavailable for %s", uri)
            TrackMetadata()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDocumentMeta(uri: Uri): DocumentMeta {
        val mime = context.contentResolver.getType(uri)
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                return DocumentMeta(
                    displayName = nameIndex.takeIf { it >= 0 }?.let { cursor.getString(it) },
                    sizeBytes = sizeIndex.takeIf { it >= 0 }?.let { cursor.getLong(it) },
                    mimeType = mime,
                )
            }
        }
        return DocumentMeta(
            displayName = uri.lastPathSegment,
            sizeBytes = null,
            mimeType = mime,
        )
    }

    private fun persistReadPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { error ->
            Timber.d(error, "Music URI permission was not persistable for %s", uri)
        }
    }

    private data class AudioDocument(
        val uri: Uri,
        val displayName: String,
        val mimeType: String?,
        val sizeBytes: Long?,
    )

    private data class DocumentMeta(
        val displayName: String?,
        val sizeBytes: Long?,
        val mimeType: String?,
    )

    private data class TrackMetadata(
        val title: String? = null,
        val artist: String? = null,
        val albumArtist: String? = null,
        val album: String? = null,
        val genre: String? = null,
        val year: Int? = null,
        val trackNumber: Int? = null,
        val discNumber: Int? = null,
        val composer: String? = null,
        val bitrate: Int? = null,
        val durationMs: Long? = null,
    )
}

private fun MusicPlaylistEntity.toCard(): MusicPlaylistCard =
    MusicPlaylistCard(
        id = id,
        name = name,
        description = description,
        mood = mood,
        trackCount = trackCount,
        coverSeed = coverSeed,
        updatedAt = updatedAt,
    )

private fun MusicTrackEntity.artistKey(): String =
    artist?.takeIf { it.isNotBlank() } ?: albumArtist?.takeIf { it.isNotBlank() } ?: "Неизвестный исполнитель"

private fun MusicTrackEntity.albumArtistKey(): String =
    albumArtist?.takeIf { it.isNotBlank() } ?: artistKey()

private fun MusicTrackEntity.genreParts(): List<String> =
    genre
        ?.split(';', ',', '/', '|')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinctBy { it.lowercase() }
        .orEmpty()

private fun List<MusicTrackEntity>.sortedForPlayback(): List<MusicTrackEntity> =
    sortedWith(
        compareBy<MusicTrackEntity> { it.discNumber ?: 0 }
            .thenBy { it.trackNumber ?: Int.MAX_VALUE }
            .thenBy { it.title.lowercase() },
    )

private fun buildRecommendationShelves(
    tracks: List<MusicTrackEntity>,
    sortedTracks: List<MusicTrackEntity>,
    artists: List<MusicArtistCard>,
    genres: List<MusicGenreCard>,
): List<MusicRecommendationShelf> {
    if (tracks.isEmpty()) return emptyList()
    val shelves = mutableListOf<MusicRecommendationShelf>()
    shelves += MusicRecommendationShelf(
        id = "recent",
        title = "Новое в медиатеке",
        subtitle = "Последние добавленные треки",
        trackIds = tracks.sortedByDescending { it.addedAt }.take(30).map { it.id },
    )

    val favorites = sortedTracks.filter { it.playCount > 0 }.take(40)
    if (favorites.isNotEmpty()) {
        shelves += MusicRecommendationShelf(
            id = "favorites",
            title = "Твой частый звук",
            subtitle = "То, что уже цепляло чаще остальных",
            trackIds = favorites.map { it.id },
        )
    }

    artists.firstOrNull { it.trackCount >= 2 }?.let { artist ->
        shelves += MusicRecommendationShelf(
            id = "artist_${artist.name.lowercase().hashCode()}",
            title = "Волна по исполнителю",
            subtitle = artist.name,
            trackIds = artist.trackIds.take(40),
        )
    }

    genres.firstOrNull { it.trackCount >= 2 }?.let { genre ->
        shelves += MusicRecommendationShelf(
            id = "genre_${genre.name.lowercase().hashCode()}",
            title = "Глубже в жанр",
            subtitle = genre.name,
            trackIds = genre.trackIds.take(40),
        )
    }

    val rediscovery = tracks
        .filter { it.playCount == 0 || it.lastPlayedAt == null }
        .sortedBy { it.addedAt }
        .take(30)
    if (rediscovery.isNotEmpty()) {
        shelves += MusicRecommendationShelf(
            id = "rediscovery",
            title = "Ещё не раскрыто",
            subtitle = "Треки, до которых руки не дошли",
            trackIds = rediscovery.map { it.id },
        )
    }
    return shelves.distinctBy { it.id }.filter { it.trackIds.isNotEmpty() }
}

private fun String.takeDigits(): String =
    filter { it.isDigit() }.take(4)

private fun String.parseTrackNumber(): Int? =
    trim()
        .substringBefore('/')
        .substringBefore('-')
        .filter { it.isDigit() }
        .toIntOrNull()
