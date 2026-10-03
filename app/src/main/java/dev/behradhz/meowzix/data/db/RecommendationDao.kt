package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query

/** Queries used only after candidate reduction; IN lists are capped well below SQLite bind limits. */
@Dao
interface RecommendationDao {
    @Query("""
        SELECT id FROM tracks WHERE hidden = 0 AND EXISTS (
            SELECT 1 FROM track_sources s WHERE s.trackId = tracks.id AND (
                (s.availability = 'AVAILABLE_LOCAL' AND
                    (LENGTH(TRIM(COALESCE(s.contentUri, ''))) > 0 OR LENGTH(TRIM(COALESCE(s.localPath, ''))) > 0))
                OR (:offlineOnly = 0 AND s.availability = 'REMOTE_ONLY' AND
                    EXISTS (SELECT 1 FROM telegram_track_sources tg WHERE tg.trackSourceId = s.id AND tg.tdFileId > 0))
            )
        ) ORDER BY id
    """)
    suspend fun eligibleTrackIds(offlineOnly: Boolean = false): List<String>

    @Query("SELECT t.normalizedArtist AS artist, t.album AS album, SUM(s.totalStarts) AS starts, SUM(s.totalCompletions) AS completions, SUM(s.earlySkips) AS earlySkips, SUM(s.lateSkips) AS lateSkips, SUM(s.manualSelections) AS manualSelections, SUM(s.replays) AS replays FROM track_preference_stats s INNER JOIN tracks t ON t.id = s.trackId GROUP BY t.normalizedArtist, t.album")
    suspend fun artistAlbumStats(): List<ArtistAlbumPreferenceRow>
    @Query(
        """
        SELECT t.id
        FROM tracks t
        WHERE t.favorite = 1
          AND t.hidden = 0
          AND EXISTS (
              SELECT 1 FROM track_sources s
              WHERE s.trackId = t.id AND s.availability != 'MISSING'
          )
        ORDER BY t.updatedAtEpochMs DESC
        LIMIT :limit
        """,
    )
    suspend fun favoriteTrackIds(limit: Int): List<String>

    @Query(
        """
        SELECT stats.trackId
        FROM track_preference_stats stats
        INNER JOIN tracks t ON t.id = stats.trackId
        WHERE t.hidden = 0
          AND EXISTS (
              SELECT 1 FROM track_sources s
              WHERE s.trackId = t.id AND s.availability != 'MISSING'
          )
        ORDER BY
            (stats.manualSelections * 4 + stats.totalCompletions * 2 + stats.replays * 3 - stats.earlySkips * 2) DESC,
            COALESCE(stats.lastPlayedAtEpochMs, 0) DESC
        LIMIT :limit
        """,
    )
    suspend fun preferredTrackIds(limit: Int): List<String>

    @Query(
        """
        SELECT t.id
        FROM tracks t
        WHERE t.hidden = 0
          AND EXISTS (
              SELECT 1 FROM track_sources s
              WHERE s.trackId = t.id AND s.availability != 'MISSING'
          )
        ORDER BY t.updatedAtEpochMs DESC, t.id ASC
        LIMIT :limit
        """,
    )
    suspend fun discoveryTrackIds(limit: Int): List<String>

    @Query("SELECT * FROM tracks WHERE id IN (:trackIds)")
    suspend fun tracksByIds(trackIds: List<String>): List<TrackEntity>

    @Query("SELECT * FROM track_sources WHERE trackId IN (:trackIds) AND availability IN ('AVAILABLE_LOCAL', 'REMOTE_ONLY', 'DOWNLOADING')")
    suspend fun activeSourcesForTracks(trackIds: List<String>): List<TrackSourceEntity>

    @Query("SELECT * FROM track_preference_stats WHERE trackId IN (:trackIds)")
    suspend fun trackStatsForTracks(trackIds: List<String>): List<TrackPreferenceStatsEntity>

    @Query("SELECT * FROM track_time_preferences WHERE trackId IN (:trackIds) AND timeBucket = :bucket")
    suspend fun timeStatsForTracks(trackIds: List<String>, bucket: String): List<TrackTimePreferenceEntity>

    @Query(
        """
        SELECT a.* FROM audio_feature_vectors a
        JOIN track_sources s ON s.id = a.sourceIdUsed
        WHERE a.trackId IN (:trackIds) AND (s.contentHashSha256 IS NULL OR s.contentHashSha256 = a.sourceContentHash)
          AND extractorName = :extractorName
          AND extractorVersion = :extractorVersion
          AND schemaVersion = :schemaVersion
        """,
    )
    suspend fun compatibleAudioVectorsForTracks(
        trackIds: List<String>,
        extractorName: String,
        extractorVersion: String,
        schemaVersion: Int,
    ): List<AudioFeatureVectorEntity>
}

data class ArtistAlbumPreferenceRow(val artist: String?, val album: String?, val starts: Int,
    val completions: Int, val earlySkips: Int, val lateSkips: Int, val manualSelections: Int, val replays: Int)
