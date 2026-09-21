package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.behradhz.meowzix.core.model.SourceAvailability
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT DISTINCT t.* FROM tracks t INNER JOIN track_sources s ON s.trackId = t.id WHERE s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL' AND t.hidden = 0 ORDER BY t.normalizedTitle")
    fun observeAvailableLocalTracks(): Flow<List<TrackEntity>>

    @Query("SELECT DISTINCT t.* FROM tracks t INNER JOIN track_sources s ON s.trackId = t.id WHERE s.availability != 'MISSING' AND t.hidden = 0 ORDER BY t.normalizedTitle")
    fun observeAvailableTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track_sources WHERE availability != 'MISSING'")
    fun observeActiveSources(): Flow<List<TrackSourceEntity>>

    @Query("SELECT t.id, t.title, t.artist, t.album, t.durationMs, t.artworkRef, CASE WHEN s.contentUri IS NOT NULL THEN s.contentUri ELSE 'file://' || s.localPath END AS contentUri FROM tracks t INNER JOIN track_sources s ON s.trackId = t.id WHERE s.availability = 'AVAILABLE_LOCAL' AND (s.contentUri IS NOT NULL OR s.localPath IS NOT NULL) AND t.hidden = 0 ORDER BY t.normalizedTitle, CASE s.type WHEN 'LOCAL_MEDIASTORE' THEN 0 WHEN 'APP_OFFLINE_COPY' THEN 1 WHEN 'TDLIB_LOCAL' THEN 2 ELSE 3 END, s.createdAtEpochMs")
    suspend fun availableLocalPlaybackRows(): List<LocalPlaybackRow>

    @Query("SELECT * FROM tracks")
    suspend fun allTracks(): List<TrackEntity>

    @Query("SELECT * FROM track_sources")
    suspend fun allSources(): List<TrackSourceEntity>

    @Query("SELECT * FROM track_sources WHERE trackId = :trackId ORDER BY createdAtEpochMs")
    suspend fun sourcesForTrack(trackId: String): List<TrackSourceEntity>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun trackById(id: String): TrackEntity?

    @Query("SELECT * FROM track_sources WHERE id = :id LIMIT 1")
    suspend fun sourceById(id: String): TrackSourceEntity?

    @Query("SELECT * FROM track_sources WHERE type = 'LOCAL_MEDIASTORE'")
    suspend fun allLocalSources(): List<TrackSourceEntity>

    @Query("SELECT DISTINCT t.* FROM tracks t INNER JOIN track_sources s ON s.trackId = t.id WHERE s.type = 'LOCAL_MEDIASTORE'")
    suspend fun allTracksWithLocalSources(): List<TrackEntity>

    @Upsert
    suspend fun upsertTrack(track: TrackEntity)

    @Upsert
    suspend fun upsertSource(source: TrackSourceEntity)

    @Upsert
    suspend fun upsertLocalMediaSource(source: LocalMediaSourceEntity)

    @Query("UPDATE track_sources SET availability = :availability, lastVerifiedAtEpochMs = :verifiedAt WHERE id = :sourceId")
    suspend fun updateAvailability(sourceId: String, availability: SourceAvailability, verifiedAt: Long)

    @Query("UPDATE track_sources SET availability = :availability, lastVerifiedAtEpochMs = :verifiedAt WHERE id IN (:sourceIds)")
    suspend fun updateAvailability(sourceIds: List<String>, availability: SourceAvailability, verifiedAt: Long)

    @Query("UPDATE track_sources SET trackId = :trackId WHERE id = :sourceId")
    suspend fun moveSource(sourceId: String, trackId: String)

    @Query("DELETE FROM tracks WHERE id = :trackId AND NOT EXISTS (SELECT 1 FROM track_sources WHERE trackId = :trackId)")
    suspend fun deleteTrackIfOrphaned(trackId: String)

    @Query("DELETE FROM track_sources WHERE id = :sourceId AND type = 'APP_OFFLINE_COPY'")
    suspend fun deleteOfflineSource(sourceId: String)
}

data class LocalPlaybackRow(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkRef: String?,
    val contentUri: String,
)
