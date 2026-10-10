package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryBrowseDao {
    @RawQuery(
        observedEntities = [
            TrackEntity::class,
            TrackSourceEntity::class,
            TelegramTrackSourceEntity::class,
            TelegramSelectedSourceEntity::class,
        ],
    )
    suspend fun searchTracks(query: SupportSQLiteQuery): List<SearchTrackRow>

    @RawQuery(
        observedEntities = [
            TrackEntity::class,
            TrackSourceEntity::class,
            TelegramTrackSourceEntity::class,
            TelegramSelectedSourceEntity::class,
        ],
    )
    suspend fun libraryTrackIds(query: SupportSQLiteQuery): List<TrackIdRow>

    /**
     * Persisted downloads and Telegram origins are retained after deselection, but the
     * selected-source table controls which Telegram tracks appear in the library.
     */
    @Query(
        """
        SELECT
            s.trackId AS trackId,
            MAX(CASE WHEN s.availability = 'AVAILABLE_LOCAL' THEN 1 ELSE 0 END) AS hasOfflineSource,
            MAX(CASE WHEN chosen.chatId IS NOT NULL AND s.availability != 'MISSING'
                     THEN 1 ELSE 0 END) AS hasCloudSource
        FROM track_sources s
        LEFT JOIN telegram_track_sources tg ON tg.trackSourceId = s.id
        LEFT JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
        GROUP BY s.trackId
        """,
    )
    fun observeAvailabilityRows(): Flow<List<TrackAvailabilityRow>>

    @Query(
        """
        SELECT COUNT(*)
        FROM tracks t
        WHERE t.favorite = 1 AND t.hidden = 0
          AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        """,
    )
    fun observeFavoriteCount(): Flow<Int>

    @Query(
        """
        SELECT
            MIN(COALESCE(NULLIF(TRIM(t.artist), ''), 'Unknown artist')) AS name,
            COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist') AS normalizedName,
            COUNT(*) AS trackCount,
            MAX(t.artworkRef) AS artworkRef
        FROM tracks t
        WHERE t.hidden = 0
          AND (
              EXISTS (
                  SELECT 1 FROM track_sources local
                  WHERE local.trackId = t.id AND local.type = 'LOCAL_MEDIASTORE' AND local.availability = 'AVAILABLE_LOCAL'
              )
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        GROUP BY COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist')
        ORDER BY normalizedName ASC
        """,
    )
    fun observeArtistSummaries(): Flow<List<ArtistSummaryRow>>

    @Query(
        """
        SELECT
            COALESCE(NULLIF(TRIM(t.album), ''), 'Unknown album') AS name,
            MIN(COALESCE(NULLIF(TRIM(t.artist), ''), 'Unknown artist')) AS artist,
            COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist') AS normalizedArtist,
            COUNT(*) AS trackCount,
            MAX(t.artworkRef) AS artworkRef
        FROM tracks t
        WHERE t.hidden = 0
          AND (
              EXISTS (
                  SELECT 1 FROM track_sources local
                  WHERE local.trackId = t.id AND local.type = 'LOCAL_MEDIASTORE' AND local.availability = 'AVAILABLE_LOCAL'
              )
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        GROUP BY COALESCE(NULLIF(TRIM(t.album), ''), 'Unknown album'),
                 COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist')
        ORDER BY name COLLATE NOCASE ASC, artist COLLATE NOCASE ASC
        """,
    )
    fun observeAlbumSummaries(): Flow<List<AlbumSummaryRow>>

    @Query(
        """
        SELECT t.*
        FROM tracks t
        WHERE t.hidden = 0
          AND COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist') = :normalizedArtist
          AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        ORDER BY t.normalizedTitle ASC
        """,
    )
    fun observeTracksByArtist(normalizedArtist: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.*
        FROM tracks t
        WHERE t.hidden = 0
          AND COALESCE(NULLIF(TRIM(t.album), ''), 'Unknown album') = :album
          AND COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist') = :normalizedArtist
          AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        ORDER BY t.normalizedTitle ASC
        """,
    )
    fun observeTracksByAlbum(album: String, normalizedArtist: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.*
        FROM tracks t
        WHERE t.hidden = 0 AND t.favorite = 1
          AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        ORDER BY t.normalizedTitle ASC
        """,
    )
    fun observeFavoriteTracks(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.*
        FROM tracks t
        WHERE t.hidden = 0 AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        ORDER BY t.normalizedTitle ASC, t.id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun browseTracks(limit: Int, offset: Int): List<TrackEntity>

    @Query(
        """
        SELECT t.*
        FROM tracks t
        WHERE t.hidden = 0 AND t.favorite = 1 AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        ORDER BY t.normalizedTitle ASC, t.id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun browseFavoriteTracks(limit: Int, offset: Int): List<TrackEntity>

    @Query(
        """
        SELECT
            MIN(COALESCE(NULLIF(TRIM(t.artist), ''), 'Unknown artist')) AS name,
            COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist') AS normalizedName,
            COUNT(*) AS trackCount,
            MAX(t.artworkRef) AS artworkRef
        FROM tracks t
        WHERE t.hidden = 0 AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        GROUP BY COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist')
        ORDER BY normalizedName ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun browseArtistSummaries(limit: Int, offset: Int): List<ArtistSummaryRow>

    @Query(
        """
        SELECT
            COALESCE(NULLIF(TRIM(t.album), ''), 'Unknown album') AS name,
            MIN(COALESCE(NULLIF(TRIM(t.artist), ''), 'Unknown artist')) AS artist,
            COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist') AS normalizedArtist,
            COUNT(*) AS trackCount,
            MAX(t.artworkRef) AS artworkRef
        FROM tracks t
        WHERE t.hidden = 0 AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        GROUP BY COALESCE(NULLIF(TRIM(t.album), ''), 'Unknown album'),
                 COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist')
        ORDER BY name COLLATE NOCASE ASC, artist COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun browseAlbumSummaries(limit: Int, offset: Int): List<AlbumSummaryRow>

    @Query(
        """
        SELECT t.*
        FROM tracks t
        WHERE t.hidden = 0
          AND COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist') = :normalizedArtist
          AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        ORDER BY t.normalizedTitle ASC, t.id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun browseTracksByArtist(
        normalizedArtist: String,
        limit: Int,
        offset: Int,
    ): List<TrackEntity>

    @Query(
        """
        SELECT t.*
        FROM tracks t
        WHERE t.hidden = 0
          AND COALESCE(NULLIF(TRIM(t.album), ''), 'Unknown album') = :album
          AND COALESCE(NULLIF(t.normalizedArtist, ''), 'unknown artist') = :normalizedArtist
          AND (
              EXISTS (SELECT 1 FROM track_sources s WHERE s.trackId = t.id AND s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL')
              OR EXISTS (
                  SELECT 1
                  FROM telegram_track_sources tg
                  INNER JOIN telegram_selected_sources chosen ON chosen.accountId = tg.accountId AND chosen.chatId = tg.chatId
                  INNER JOIN track_sources origin ON origin.id = tg.trackSourceId
                  WHERE origin.trackId = t.id AND origin.availability != 'MISSING'
              )
          )
        ORDER BY t.normalizedTitle ASC, t.id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun browseTracksByAlbum(
        album: String,
        normalizedArtist: String,
        limit: Int,
        offset: Int,
    ): List<TrackEntity>

}

data class SearchTrackRow(
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
    val hidden: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class TrackIdRow(val id: String)

data class TrackAvailabilityRow(
    val trackId: String,
    val hasOfflineSource: Boolean,
    val hasCloudSource: Boolean,
)

data class ArtistSummaryRow(
    val name: String,
    val normalizedName: String,
    val trackCount: Int,
    val artworkRef: String?,
)

data class AlbumSummaryRow(
    val name: String,
    val artist: String,
    val normalizedArtist: String,
    val trackCount: Int,
    val artworkRef: String?,
)
