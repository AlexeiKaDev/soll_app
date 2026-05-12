package com.soll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.soll.data.local.entity.MusicPlaybackStateEntity
import com.soll.data.local.entity.MusicPlaylistEntity
import com.soll.data.local.entity.MusicPlaylistTrackEntity
import com.soll.data.local.entity.MusicSourceEntity
import com.soll.data.local.entity.MusicSourceTrackEntity
import com.soll.data.local.entity.MusicTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM music_sources ORDER BY added_at DESC")
    fun observeSources(): Flow<List<MusicSourceEntity>>

    @Query("SELECT * FROM music_sources ORDER BY added_at DESC")
    suspend fun getSources(): List<MusicSourceEntity>

    @Query("SELECT * FROM music_playlists ORDER BY updated_at DESC, name COLLATE NOCASE ASC")
    fun observePlaylists(): Flow<List<MusicPlaylistEntity>>

    @Query("SELECT * FROM music_playlists ORDER BY updated_at DESC, name COLLATE NOCASE ASC")
    suspend fun getPlaylists(): List<MusicPlaylistEntity>

    @Query("SELECT * FROM music_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: String): MusicPlaylistEntity?

    @Query(
        """
        SELECT * FROM music_tracks
        ORDER BY artist COLLATE NOCASE ASC,
                 album COLLATE NOCASE ASC,
                 disc_number ASC,
                 track_number ASC,
                 title COLLATE NOCASE ASC
        """
    )
    fun observeTracks(): Flow<List<MusicTrackEntity>>

    @Query(
        """
        SELECT * FROM music_tracks
        ORDER BY artist COLLATE NOCASE ASC,
                 album COLLATE NOCASE ASC,
                 disc_number ASC,
                 track_number ASC,
                 title COLLATE NOCASE ASC
        """
    )
    suspend fun getAllTracks(): List<MusicTrackEntity>

    @Query("SELECT * FROM music_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrack(id: Long): MusicTrackEntity?

    @Query("SELECT * FROM music_tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<Long>): List<MusicTrackEntity>

    @Query(
        """
        SELECT * FROM music_tracks
        WHERE uri IN (
            SELECT track_uri FROM music_source_tracks WHERE source_uri = :sourceUri
        )
        ORDER BY title COLLATE NOCASE ASC, artist COLLATE NOCASE ASC
        """
    )
    suspend fun getTracksForSource(sourceUri: String): List<MusicTrackEntity>

    @Query("SELECT * FROM music_tracks WHERE uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): MusicTrackEntity?

    @Query(
        """
        SELECT music_tracks.* FROM music_tracks
        INNER JOIN music_playlist_tracks ON music_playlist_tracks.track_id = music_tracks.id
        WHERE music_playlist_tracks.playlist_id = :playlistId
        ORDER BY music_playlist_tracks.position ASC, music_playlist_tracks.added_at ASC
        """
    )
    suspend fun getTracksForPlaylist(playlistId: String): List<MusicTrackEntity>

    @Query(
        """
        SELECT music_tracks.* FROM music_tracks
        INNER JOIN music_playlist_tracks ON music_playlist_tracks.track_id = music_tracks.id
        WHERE music_playlist_tracks.playlist_id = :playlistId
        ORDER BY music_playlist_tracks.position ASC, music_playlist_tracks.added_at ASC
        """
    )
    fun observeTracksForPlaylist(playlistId: String): Flow<List<MusicTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: MusicSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: MusicPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistTrack(link: MusicPlaylistTrackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: MusicTrackEntity): Long

    @Update
    suspend fun updateTrack(track: MusicTrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceTrack(link: MusicSourceTrackEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM music_playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun nextPlaylistPosition(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM music_playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun hasPlaylistTrack(playlistId: String, trackId: Long): Int

    @Query("SELECT COUNT(*) FROM music_playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun countPlaylistTracks(playlistId: String): Int

    @Query(
        """
        UPDATE music_playlists
        SET track_count = :trackCount,
            updated_at = :updatedAt
        WHERE id = :playlistId
        """
    )
    suspend fun updatePlaylistCount(
        playlistId: String,
        trackCount: Int,
        updatedAt: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM music_playlist_tracks WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removePlaylistTrack(playlistId: String, trackId: Long)

    @Query("DELETE FROM music_playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: String)

    @Query("DELETE FROM music_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM music_source_tracks WHERE source_uri = :sourceUri")
    suspend fun deleteSourceTrackLinks(sourceUri: String)

    @Query("DELETE FROM music_source_tracks WHERE source_uri = :sourceUri AND track_uri NOT IN (:uris)")
    suspend fun deleteMissingSourceTrackLinks(sourceUri: String, uris: List<String>)

    @Query("DELETE FROM music_tracks WHERE uri NOT IN (SELECT track_uri FROM music_source_tracks)")
    suspend fun deleteOrphanTracks()

    @Query(
        """
        DELETE FROM music_playlist_tracks
        WHERE track_id NOT IN (SELECT id FROM music_tracks)
        """
    )
    suspend fun deleteMissingPlaylistTrackLinks()

    @Query("SELECT COUNT(*) FROM music_source_tracks WHERE source_uri = :sourceUri")
    suspend fun countTracksForSource(sourceUri: String): Int

    @Query("DELETE FROM music_sources WHERE uri = :sourceUri")
    suspend fun deleteSource(sourceUri: String)

    @Query(
        """
        UPDATE music_sources
        SET track_count = :trackCount,
            last_error = :lastError,
            last_scanned_at = :lastScannedAt
        WHERE uri = :sourceUri
        """
    )
    suspend fun updateSourceScanState(
        sourceUri: String,
        trackCount: Int,
        lastError: String?,
        lastScannedAt: Long,
    )

    @Query(
        """
        UPDATE music_tracks
        SET last_played_at = :playedAt,
            play_count = play_count + 1
        WHERE id = :trackId
        """
    )
    suspend fun markTrackPlayed(trackId: Long, playedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackState(state: MusicPlaybackStateEntity)

    @Query("SELECT * FROM music_playback_state WHERE id = :id LIMIT 1")
    suspend fun getPlaybackState(id: String = MusicPlaybackStateEntity.PRIMARY_ID): MusicPlaybackStateEntity?
}
