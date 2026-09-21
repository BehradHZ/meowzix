package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT p.id, p.title, COUNT(pt.trackId) AS trackCount FROM playlists p LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id GROUP BY p.id ORDER BY p.title COLLATE NOCASE")
    fun observePlaylists(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT t.* FROM playlist_tracks pt INNER JOIN tracks t ON t.id = pt.trackId WHERE pt.playlistId = :playlistId ORDER BY pt.position")
    fun observeTracks(playlistId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    suspend fun entries(playlistId: String): List<PlaylistTrackEntity>

    @Upsert
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET title = :title, updatedAtEpochMs = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, title: String, updatedAt: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(entry: PlaylistTrackEntity): Long

    @Upsert
    suspend fun upsertTracks(entries: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearTracks(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: String, trackId: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)
}

data class PlaylistSummaryRow(val id: String, val title: String, val trackCount: Int)
