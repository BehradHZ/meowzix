package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM listening_events ORDER BY occurredAtEpochMs DESC")
    fun observeEvents(): Flow<List<ListeningEventEntity>>

    @Query("SELECT * FROM track_preference_stats ORDER BY lastPlayedAtEpochMs DESC")
    fun observeTrackStats(): Flow<List<TrackPreferenceStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: ListeningEventEntity): Long

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
        "SELECT COALESCE(MAX(occurredAtEpochMs), 0) FROM listening_events " +
            "WHERE type IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_EARLY', 'SKIPPED_LATE')",
    )
    suspend fun latestOutcomeVersion(): Long

    @Query("DELETE FROM listening_events") suspend fun clearEvents()
    @Query("DELETE FROM listening_sessions") suspend fun clearSessions()
    @Query("DELETE FROM track_preference_stats") suspend fun clearTrackStats()
    @Query("DELETE FROM track_time_preferences") suspend fun clearTimeStats()
}
