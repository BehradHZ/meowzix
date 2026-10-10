package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface RulePlaylistQueryDao {
    @RawQuery(
        observedEntities = [
            TrackEntity::class,
            TrackSourceEntity::class,
            TelegramTrackSourceEntity::class,
            TelegramSelectedSourceEntity::class,
            ListeningEventEntity::class,
            TrackMetadataOverrideEntity::class,
            TrackSearchFtsEntity::class,
        ],
    )
    fun observeTracks(query: SupportSQLiteQuery): Flow<List<SearchTrackRow>>

    @Query(
        "SELECT MIN(createdAtEpochMs + :windowMs) FROM tracks " +
            "WHERE hidden = 0 AND createdAtEpochMs + :windowMs > :nowEpochMs",
    )
    suspend fun nextAddedExpiry(windowMs: Long, nowEpochMs: Long): Long?

    @Query(
        "SELECT MIN(occurredAtEpochMs + :windowMs) FROM listening_events " +
            "WHERE type IN ('PLAY_COMPLETED', 'PLAY_STOPPED', 'SKIPPED_LATE') " +
            "AND occurredAtEpochMs + :windowMs > :nowEpochMs",
    )
    suspend fun nextMeaningfulListenExpiry(windowMs: Long, nowEpochMs: Long): Long?
}