package com.soll.domain.music

enum class MusicSourceType {
    FOLDER,
    TRACKS,
}

enum class MusicRepeatMode {
    OFF,
    ONE,
    ALL,
}

enum class MusicLibraryView {
    OVERVIEW,
    TRACKS,
    ARTISTS,
    ALBUMS,
    GENRES,
    PLAYLISTS,
}

data class MusicSettings(
    val resumeLastTrack: Boolean = true,
    val pauseMusicForTts: Boolean = true,
    val stopTtsOnMusicStart: Boolean = true,
    val headsetControlsEnabled: Boolean = true,
    val autoRescanOnOpen: Boolean = false,
    val strictAudioFilter: Boolean = true,
    val showBackgroundHints: Boolean = true,
    val defaultShuffle: Boolean = false,
    val defaultRepeatMode: MusicRepeatMode = MusicRepeatMode.OFF,
)

data class MusicImportSummary(
    val sourceLabel: String,
    val scannedCount: Int,
    val importedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
) {
    fun toUserMessage(): String =
        "Музыка: $sourceLabel. Добавлено $importedCount, обновлено $updatedCount, пропущено $skippedCount."
}

data class MusicArtistCard(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val playCount: Int,
    val lastPlayedAt: Long?,
    val trackIds: List<Long>,
)

data class MusicAlbumCard(
    val title: String,
    val artist: String?,
    val year: Int?,
    val genre: String?,
    val trackCount: Int,
    val durationMs: Long,
    val playCount: Int,
    val trackIds: List<Long>,
)

data class MusicGenreCard(
    val name: String,
    val trackCount: Int,
    val artistCount: Int,
    val playCount: Int,
    val trackIds: List<Long>,
)

data class MusicPlaylistCard(
    val id: String,
    val name: String,
    val description: String,
    val mood: String?,
    val trackCount: Int,
    val coverSeed: Int,
    val updatedAt: Long,
)

data class MusicRecommendationShelf(
    val id: String,
    val title: String,
    val subtitle: String,
    val trackIds: List<Long>,
)

data class MusicLibraryInsights(
    val trackCount: Int = 0,
    val artistCount: Int = 0,
    val albumCount: Int = 0,
    val genreCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val artists: List<MusicArtistCard> = emptyList(),
    val albums: List<MusicAlbumCard> = emptyList(),
    val genres: List<MusicGenreCard> = emptyList(),
    val recommendations: List<MusicRecommendationShelf> = emptyList(),
)

data class MusicPlayerState(
    val isServiceActive: Boolean = false,
    val isPreparing: Boolean = false,
    val currentTrackId: Long? = null,
    val currentTrackUri: String? = null,
    val title: String = "Музыка",
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: MusicRepeatMode = MusicRepeatMode.OFF,
    val queueSize: Int = 0,
    val statusText: String? = null,
    val errorMessage: String? = null,
)
