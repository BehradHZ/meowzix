package dev.behradhz.meowzix.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import dev.behradhz.meowzix.core.model.SourceAvailability
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT DISTINCT t.* FROM tracks t INNER JOIN track_sources s ON s.trackId = t.id WHERE s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL' AND t.hidden = 0 ORDER BY t.normalizedTitle")
    fun observeAvailableLocalTracks(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT DISTINCT t.*
        FROM tracks t
        WHERE t.hidden = 0
          AND EXISTS (
              SELECT 1
              FROM track_sources active
              WHERE active.trackId = t.id
                AND active.availability != 'MISSING'
          )
          AND (
              EXISTS (
                  SELECT 1
                  FROM track_sources local
                  WHERE local.trackId = t.id
                    AND local.type = 'LOCAL_MEDIASTORE'
                    AND local.availability != 'MISSING'
              )
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  INNER JOIN telegram_selected_sources selected
                      ON selected.accountId = tg.accountId
                     AND selected.chatId = tg.chatId
                  WHERE origin.trackId = t.id
              )
          )
        ORDER BY t.normalizedTitle
        """,
    )
    fun observeAvailableTracks(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT DISTINCT t.*
        FROM tracks t
        WHERE t.hidden = 0
          AND EXISTS (
              SELECT 1
              FROM track_sources active
              WHERE active.trackId = t.id
                AND active.availability != 'MISSING'
          )
          AND (
              EXISTS (
                  SELECT 1
                  FROM track_sources local
                  WHERE local.trackId = t.id
                    AND local.type = 'LOCAL_MEDIASTORE'
                    AND local.availability != 'MISSING'
              )
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  INNER JOIN telegram_selected_sources selected
                      ON selected.accountId = tg.accountId
                     AND selected.chatId = tg.chatId
                  WHERE origin.trackId = t.id
              )
          )
        ORDER BY t.normalizedTitle
        """,
    )
    fun pagingAvailableTracks(): PagingSource<Int, TrackEntity>

    /**
     * Main library list projection. SQL text is assembled only from closed enum values in
     * PagedLibraryTracks, so sort/filter selection stays database-backed without duplicating five
     * near-identical Room queries.
     */
    @RawQuery(
        observedEntities = [
            TrackEntity::class,
            TrackSourceEntity::class,
            TelegramTrackSourceEntity::class,
            TelegramSelectedSourceEntity::class,
        ],
    )
    fun pagingLibraryRows(query: SupportSQLiteQuery): PagingSource<Int, LibraryTrackRow>

    @Query(
        """
        SELECT COUNT(*)
        FROM tracks t
        WHERE t.hidden = 0
          AND EXISTS (
              SELECT 1 FROM track_sources active
              WHERE active.trackId = t.id AND active.availability != 'MISSING'
          )
          AND (
              EXISTS (
                  SELECT 1 FROM track_sources local
                  WHERE local.trackId = t.id
                    AND local.type = 'LOCAL_MEDIASTORE'
                    AND local.availability != 'MISSING'
              )
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  INNER JOIN telegram_selected_sources selected
                      ON selected.accountId = tg.accountId AND selected.chatId = tg.chatId
                  WHERE origin.trackId = t.id
              )
          )
        """,
    )
    fun observeAvailableTrackCount(): Flow<Int>

    @Query(
        """
        SELECT DISTINCT t.*
        FROM tracks t
        WHERE t.hidden = 0
          AND EXISTS (
              SELECT 1
              FROM track_sources active
              WHERE active.trackId = t.id
                AND active.availability != 'MISSING'
          )
          AND (
              EXISTS (
                  SELECT 1
                  FROM track_sources local
                  WHERE local.trackId = t.id
                    AND local.type = 'LOCAL_MEDIASTORE'
                    AND local.availability != 'MISSING'
              )
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  INNER JOIN telegram_selected_sources selected
                      ON selected.accountId = tg.accountId
                     AND selected.chatId = tg.chatId
                  WHERE origin.trackId = t.id
              )
          )
        ORDER BY t.normalizedTitle
        """,
    )
    suspend fun availableTracks(): List<TrackEntity>

    @Query(
        """
        SELECT * FROM tracks
        WHERE normalizedTitle = :normalizedTitle
          AND normalizedArtist = :normalizedArtist
        """,
    )
    suspend fun matchingTracks(normalizedTitle: String, normalizedArtist: String): List<TrackEntity>

    /** Bounded SQL candidate set for the expensive UnifiedTrackMatcher. */
    @Query(
        """
        SELECT id, normalizedTitle, normalizedArtist, durationMs
        FROM tracks
        WHERE normalizedTitle = :normalizedTitle
          AND normalizedArtist = :normalizedArtist
          AND durationMs BETWEEN :minDurationMs AND :maxDurationMs
        LIMIT :limit
        """,
    )
    suspend fun matchingTrackCandidates(
        normalizedTitle: String,
        normalizedArtist: String,
        minDurationMs: Long,
        maxDurationMs: Long,
        limit: Int = 32,
    ): List<TrackMatchRow>

    @Query(
        """
        SELECT t.id AS trackId, t.normalizedTitle, t.normalizedArtist, t.durationMs,
               s.contentHashSha256
        FROM track_sources s
        INNER JOIN tracks t ON t.id = s.trackId
        WHERE s.contentHashSha256 = :contentHashSha256
        LIMIT :limit
        """,
    )
    suspend fun matchingTracksByContentHash(
        contentHashSha256: String,
        limit: Int = 8,
    ): List<TrackHashMatchRow>

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

    @Query("SELECT * FROM local_media_sources")
    suspend fun allLocalMediaSources(): List<LocalMediaSourceEntity>

    @Query("SELECT DISTINCT t.* FROM tracks t INNER JOIN track_sources s ON s.trackId = t.id WHERE s.type = 'LOCAL_MEDIASTORE'")
    suspend fun allTracksWithLocalSources(): List<TrackEntity>

    @Query("SELECT MAX(lastVerifiedAtEpochMs) FROM track_sources WHERE type = 'LOCAL_MEDIASTORE'")
    suspend fun latestLocalVerificationEpochMs(): Long?

    @Upsert
    suspend fun upsertTrack(track: TrackEntity)

    @Upsert
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Upsert
    suspend fun upsertSource(source: TrackSourceEntity)

    @Upsert
    suspend fun upsertSources(sources: List<TrackSourceEntity>)

    @Upsert
    suspend fun upsertLocalMediaSource(source: LocalMediaSourceEntity)

    @Upsert
    suspend fun upsertLocalMediaSources(sources: List<LocalMediaSourceEntity>)

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

    @Query("UPDATE tracks SET favorite = :favorite, updatedAtEpochMs = :updatedAt WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE tracks SET artworkRef = :artworkRef, updatedAtEpochMs = :updatedAt WHERE id = :trackId")
    suspend fun setArtworkRef(trackId: String, artworkRef: String?, updatedAt: Long)
}

data class LibraryTrackRow(
    val id: String,
    val title: String,
    val normalizedTitle: String,
    val artist: String?,
    val normalizedArtist: String?,
    val album: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val artworkRef: String?,
    val favorite: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val hasOfflineSource: Boolean,
    val hasCloudSource: Boolean,
)

data class TrackMatchRow(
    val id: String,
    val normalizedTitle: String,
    val normalizedArtist: String?,
    val durationMs: Long,
)

data class TrackHashMatchRow(
    val trackId: String,
    val normalizedTitle: String,
    val normalizedArtist: String?,
    val durationMs: Long,
    val contentHashSha256: String,
)

data class LocalPlaybackRow(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkRef: String?,
    val contentUri: String,
)
