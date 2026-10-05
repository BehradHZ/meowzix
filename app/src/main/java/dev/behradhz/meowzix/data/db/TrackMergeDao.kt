package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query

/** Narrow DAO used by reversible duplicate merges; no arbitrary SQL is exposed above the data layer. */
@Dao
interface TrackMergeDao {
    @Query("SELECT id FROM listening_events WHERE trackId = :trackId ORDER BY eventSequence")
    suspend fun eventIdsForTrack(trackId: String): List<String>

    @Query("UPDATE listening_events SET trackId = :toTrackId WHERE id = :eventId AND trackId = :fromTrackId")
    suspend fun moveEvent(eventId: String, fromTrackId: String, toTrackId: String): Int
}