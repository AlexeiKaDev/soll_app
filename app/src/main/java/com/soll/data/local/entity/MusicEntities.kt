package com.soll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "music_sources")
data class MusicSourceEntity(
    @PrimaryKey
    val uri: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "source_type")
    val sourceType: String,
    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long? = null,
)

@Entity(
    tableName = "music_tracks",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["source_uri"]),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["album_artist"]),
        Index(value = ["genre"]),
    ],
)
data class MusicTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "source_uri")
    val sourceUri: String?,
    val uri: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    val title: String,
    val artist: String?,
    @ColumnInfo(name = "album_artist")
    val albumArtist: String? = null,
    val album: String?,
    val genre: String? = null,
    val year: Int? = null,
    @ColumnInfo(name = "track_number")
    val trackNumber: Int? = null,
    @ColumnInfo(name = "disc_number")
    val discNumber: Int? = null,
    val composer: String? = null,
    @ColumnInfo(name = "bitrate")
    val bitrate: Int? = null,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long?,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: Long? = null,
    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,
)

@Entity(
    tableName = "music_playlists",
    indices = [
        Index(value = ["name"]),
        Index(value = ["updated_at"]),
    ],
)
data class MusicPlaylistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val mood: String? = null,
    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0,
    @ColumnInfo(name = "cover_seed")
    val coverSeed: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "music_playlist_tracks",
    primaryKeys = ["playlist_id", "track_id"],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["track_id"]),
        Index(value = ["playlist_id", "position"]),
    ],
)
data class MusicPlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    @ColumnInfo(name = "track_id")
    val trackId: Long,
    val position: Int,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "music_source_tracks",
    primaryKeys = ["source_uri", "track_uri"],
    indices = [
        Index(value = ["source_uri"]),
        Index(value = ["track_uri"]),
    ],
)
data class MusicSourceTrackEntity(
    @ColumnInfo(name = "source_uri")
    val sourceUri: String,
    @ColumnInfo(name = "track_uri")
    val trackUri: String,
    @ColumnInfo(name = "linked_at")
    val linkedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "music_playback_state")
data class MusicPlaybackStateEntity(
    @PrimaryKey
    val id: String = PRIMARY_ID,
    @ColumnInfo(name = "current_track_id")
    val currentTrackId: Long?,
    @ColumnInfo(name = "position_ms")
    val positionMs: Long = 0L,
    @ColumnInfo(name = "shuffle_enabled")
    val shuffleEnabled: Boolean = false,
    @ColumnInfo(name = "repeat_mode")
    val repeatMode: String = "OFF",
    @ColumnInfo(name = "queue_track_ids_csv")
    val queueTrackIdsCsv: String = "",
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val PRIMARY_ID = "primary"
    }
}
