package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM listening_events ORDER BY occurredAtEpochMs DESC")
    fun observeEvents(): Flow<List<ListeningEventEntity>>

    @Query(
        "SELECT * FROM listening_events " +
            "WHERE type IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_EARLY', 'SKIPPED_LATE') " +
            "ORDER BY eventSequence DESC LIMIT :limit",
    )
    fun observeRecentFinalizedOutcomes(limit: Int): Flow<List<ListeningEventEntity>>

    @Query(
        "SELECT * FROM listening_events " +
            "WHERE type IN ('PLAY_STARTED', 'MANUAL_SELECTED') " +
            "ORDER BY occurredAtEpochMs DESC LIMIT :limit",
    )
    fun observeRecentHomeSelections(limit: Int): Flow<List<ListeningEventEntity>>

    @Query("SELECT * FROM track_preference_stats ORDER BY lastPlayedAtEpochMs DESC")
    fun observeTrackStats(): Flow<List<TrackPreferenceStatsEntity>>

    @Transaction
    suspend fun insertEvent(event: ListeningEventEntity): Long {
        ensureClock()
        advanceClock()
        val final = event.type in setOf("PLAY_COMPLETED", "PLAY_STOPPED", "SKIPPED_EARLY", "SKIPPED_LATE", "QUEUE_REMOVED")
        return insertSequencedEvent(event.copy(eventSequence = eventClock(), outcomeKey = if (final) event.playbackInstanceId else null))
    }
    @Query("INSERT OR IGNORE INTO recommendation_event_clock (id, lastSequence) VALUES (1, 0)")
    suspend fun ensureClock()
    @Query("UPDATE recommendation_event_clock SET lastSequence = lastSequence + 1 WHERE id = 1")
    suspend fun advanceClock()
    @Query("SELECT lastSequence FROM recommendation_event_clock WHERE id = 1")
    suspend fun eventClock(): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSequencedEvent(event: ListeningEventEntity): Long

    @Upsert suspend fun upsertSession(session: ListeningSessionEntity)
    @Upsert suspend fun upsertTrackStats(stats: TrackPreferenceStatsEntity)
    @Upsert suspend fun upsertTimeStats(stats: TrackTimePreferenceEntity)

    @Query("SELECT * FROM track_preference_stats WHERE trackId = :trackId LIMIT 1")
    suspend fun trackStats(trackId: String): TrackPreferenceStatsEntity?

    @Query("SELECT * FROM track_time_preferences WHERE trackId = :trackId AND timeBucket = :bucket LIMIT 1")
    suspend fun timeStats(trackId: String, bucket: String): TrackTimePreferenceEntity?

    @Query("SELECT * FROM track_preference_stats") suspend fun allTrackStats(): List<TrackPreferenceStatsEntity>
    @Query("SELECT * FROM track_time_preferences WHERE timeBucket = :bucket") suspend fun timeStatsForBucket(bucket: String): List<TrackTimePreferenceEntity>
    @Query("SELECT * FROM listening_events WHERE type IN ('PLAY_STARTED', 'SKIPPED_EARLY') ORDER BY occurredAtEpochMs DESC LIMIT :limit")
    suspend fun recentSelectionEvents(limit: Int): List<ListeningEventEntity>

    @Query("SELECT * FROM listening_events ORDER BY occurredAtEpochMs ASC")
    suspend fun allEventsChronological(): List<ListeningEventEntity>

    @Query("SELECT * FROM listening_events WHERE playbackInstanceId = :playbackInstanceId ORDER BY occurredAtEpochMs ASC")
    suspend fun eventsForPlayback(playbackInstanceId: String): List<ListeningEventEntity>

    @Query(
        "SELECT COALESCE(MAX(eventSequence), 0) FROM listening_events " +
            "WHERE type IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_EARLY', 'SKIPPED_LATE', 'QUEUE_REMOVED')",
    )
    suspend fun latestOutcomeVersion(): Long

    @Query("SELECT e.playbackInstanceId, e.eventSequence AS sequence FROM listening_events e WHERE e.eventSequence > :after AND e.eventSequence <= :through AND e.outcomeKey IS NOT NULL AND NOT EXISTS (SELECT 1 FROM listening_events old WHERE old.playbackInstanceId = e.playbackInstanceId AND old.eventSequence <= :floor) ORDER BY CASE WHEN :latestFirst THEN e.eventSequence END DESC, e.eventSequence ASC LIMIT :limit")
    suspend fun finalizedPlaybackIds(after: Long, through: Long, floor: Long, limit: Int, latestFirst: Boolean): List<FinalizedPlaybackVersion>

    @Query("SELECT * FROM listening_events WHERE eventSequence > :after AND eventSequence <= :through AND playbackInstanceId IN (:playbackIds) ORDER BY eventSequence ASC")
    suspend fun eventsForPlaybackIds(playbackIds: List<String>, after: Long, through: Long): List<ListeningEventEntity>

    @Query("SELECT COUNT(*) FROM listening_events WHERE eventSequence > :after AND eventSequence <= :through AND outcomeKey IS NOT NULL AND NOT EXISTS (SELECT 1 FROM listening_events old WHERE old.playbackInstanceId = listening_events.playbackInstanceId AND old.eventSequence <= :floor)")
    suspend fun finalizedPlaybackCount(after: Long, through: Long, floor: Long): Int

    @Query("SELECT * FROM listening_events WHERE eventSequence > :after AND eventSequence <= :through ORDER BY eventSequence ASC")
    suspend fun eventsBySequence(after: Long, through: Long): List<ListeningEventEntity>

    @Query("SELECT COALESCE(MAX(eventSequence), 0) FROM listening_events")
    suspend fun latestEventSequence(): Long

    @Query("SELECT COUNT(*) FROM listening_events") suspend fun eventCount(): Int
    @Query("SELECT COUNT(*) FROM listening_events WHERE type = :type") suspend fun eventCount(type: String): Int
    @Query("DELETE FROM listening_events") suspend fun deleteAllEvents()
    @Query("DELETE FROM listening_sessions") suspend fun deleteAllSessions()
    @Query("DELETE FROM track_preference_stats") suspend fun deleteAllTrackStats()
    @Query("DELETE FROM track_time_preferences") suspend fun deleteAllTimeStats()
    @Query("DELETE FROM recommendation_event_clock") suspend fun deleteEventClock()

    @Query("SELECT * FROM track_preference_stats WHERE trackId IN (:trackIds)")
    suspend fun trackStatsForTracks(trackIds: List<String>): List<TrackPreferenceStatsEntity>

    @Query("SELECT * FROM track_time_preferences WHERE trackId IN (:trackIds) AND timeBucket = :bucket")
    suspend fun timeStatsForTracks(trackIds: List<String>, bucket: String): List<TrackTimePreferenceEntity>

    @Query("SELECT * FROM listening_events WHERE occurredAtEpochMs >= :sinceEpochMs AND eventSequence > :floor ORDER BY occurredAtEpochMs DESC LIMIT :limit")
    suspend fun recommendationWindow(sinceEpochMs: Long, floor: Long, limit: Int): List<ListeningEventEntity>

    @Query("SELECT COUNT(*) FROM listening_events WHERE sessionId = :sessionId AND type = 'PLAY_STARTED' AND eventSequence > :floor")
    suspend fun sessionPosition(sessionId: String, floor: Long): Int

    @Query("SELECT t.normalizedArtist AS artist, t.album AS album, s.totalStarts AS starts, s.totalCompletions AS completions, s.earlySkips AS earlySkips, s.manualSelections AS manualSelections, s.replays AS replays, s.lateSkips AS lateSkips FROM track_preference_stats s JOIN tracks t ON t.id = s.trackId")
    suspend fun artistAlbumStats(): List<ArtistAlbumPreferenceRow>

    @Query("SELECT COUNT(*) FROM track_preference_stats") suspend fun trackStatsCount(): Int
    @Query("SELECT COUNT(*) FROM track_time_preferences") suspend fun timeStatsCount(): Int

    @Query("SELECT * FROM listening_sessions WHERE endedAtEpochMs IS NULL ORDER BY startedAtEpochMs DESC LIMIT 1")
    suspend fun activeSession(): ListeningSessionEntity?
}

data class FinalizedPlaybackVersion(
    val playbackInstanceId: String,
    val sequence: Long,
)

data class ArtistAlbumPreferenceRow(
    val artist: String?,
    val album: String?,
    val starts: Int,
    val completions: Int,
    val earlySkips: Int,
    val manualSelections: Int,
    val replays: Int,
    val lateSkips: Int,
)
