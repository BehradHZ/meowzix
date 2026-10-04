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
    suspend fun trainingOutcomes(after: Long, floor: Long, limit: Int, latestFirst: Boolean = true, through: Long = Long.MAX_VALUE): List<TrainingOutcomeReference>

    @Query("SELECT eventSequence AS sequence, * FROM listening_events WHERE eventSequence > :after AND eventSequence <= :through AND (type NOT IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_EARLY', 'SKIPPED_LATE', 'QUEUE_REMOVED') OR outcomeKey IS NOT NULL) ORDER BY eventSequence LIMIT :limit")
    suspend fun trainingEventPage(after: Long, limit: Int, through: Long = Long.MAX_VALUE): List<SequencedListeningEvent>

    @Query("SELECT eventSequence AS sequence, * FROM listening_events ORDER BY eventSequence")
    suspend fun sequencedEvents(): List<SequencedListeningEvent>

    @Query("SELECT COALESCE(MAX(eventSequence), 0) FROM listening_events WHERE occurredAtEpochMs <= :epochMs")
    suspend fun sequenceAtOrBefore(epochMs: Long): Long

    @Query("SELECT COALESCE((SELECT lastSequence FROM recommendation_event_clock WHERE id = 1), 0)")
    suspend fun latestEventSequence(): Long

    @Query("SELECT * FROM listening_events ORDER BY eventSequence DESC LIMIT :limit")
    suspend fun recentRecommendationEvents(limit: Int): List<ListeningEventEntity>

    @Query("SELECT * FROM listening_events WHERE eventSequence > :floor AND occurredAtEpochMs >= :since ORDER BY eventSequence DESC LIMIT :limit")
    suspend fun recommendationWindow(since: Long, floor: Long, limit: Int): List<ListeningEventEntity>

    @Query("SELECT COUNT(*) FROM listening_events WHERE sessionId = :sessionId AND type = 'PLAY_STARTED' AND eventSequence > :floor")
    suspend fun sessionPosition(sessionId: String, floor: Long): Int

    /** Restore the same aggregates used by live ranking without racing new playback writes. */
    @Transaction
    suspend fun rebuildPreferenceStats(floor: Long) {
        clearPreferenceStats()
        rebuildTrackStats(floor)
        rebuildTimeStats(floor)
    }

    @Transaction
    suspend fun clearPreferenceStats() {
        clearTrackStats()
        clearTimeStats()
    }

    @Query("""
        INSERT INTO track_preference_stats
            (trackId, totalStarts, totalCompletions, earlySkips, lateSkips, manualSelections, replays, lastPlayedAtEpochMs)
        SELECT e.trackId,
            SUM(CASE WHEN e.type = 'PLAY_STARTED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.type = 'PLAY_COMPLETED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.type = 'SKIPPED_EARLY' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.type = 'SKIPPED_LATE' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.type = 'MANUAL_SELECTED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.type = 'REPLAYED' THEN 1 ELSE 0 END),
            MAX(CASE WHEN e.type = 'PLAY_STARTED' THEN e.occurredAtEpochMs END)
        FROM listening_events e
        WHERE e.eventSequence > :floor
            AND (e.type NOT IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_EARLY', 'SKIPPED_LATE', 'QUEUE_REMOVED') OR e.outcomeKey IS NOT NULL)
            AND NOT EXISTS (SELECT 1 FROM listening_events old WHERE old.playbackInstanceId = e.playbackInstanceId AND old.eventSequence <= :floor)
        GROUP BY e.trackId
    """)
    suspend fun rebuildTrackStats(floor: Long)

    @Query("""
        INSERT INTO track_time_preferences
            (trackId, timeBucket, starts, completions, earlySkips, manualSelections, lastInteractionAtEpochMs)
        SELECT e.trackId, first_event.timeBucket,
            SUM(CASE WHEN e.type = 'PLAY_STARTED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.type = 'PLAY_COMPLETED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.type = 'SKIPPED_EARLY' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.type = 'MANUAL_SELECTED' THEN 1 ELSE 0 END),
            MAX(e.occurredAtEpochMs)
        FROM listening_events e
        JOIN (SELECT playbackInstanceId, MIN(eventSequence) AS sequence FROM listening_events GROUP BY playbackInstanceId) initial
            ON initial.playbackInstanceId = e.playbackInstanceId
        JOIN listening_events first_event ON first_event.eventSequence = initial.sequence
        WHERE first_event.eventSequence > :floor
            AND (e.type NOT IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_EARLY', 'SKIPPED_LATE', 'QUEUE_REMOVED') OR e.outcomeKey IS NOT NULL)
        GROUP BY e.trackId, first_event.timeBucket
    """)
    suspend fun rebuildTimeStats(floor: Long)

    @Query("DELETE FROM listening_events") suspend fun clearEvents()
    @Query("DELETE FROM listening_sessions") suspend fun clearSessions()
    @Query("DELETE FROM track_preference_stats") suspend fun clearTrackStats()
    @Query("DELETE FROM track_time_preferences") suspend fun clearTimeStats()
}

data class SequencedListeningEvent(val sequence: Long, @Embedded val event: ListeningEventEntity)
data class TrainingOutcomeReference(val playbackInstanceId: String, val sequence: Long)
