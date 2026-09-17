package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.behradhz.meowzix.core.model.SourceAvailability
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT DISTINCT t.* FROM tracks t INNER JOIN track_sources s ON s.trackId = t.id WHERE s.type = 'LOCAL_MEDIASTORE' AND s.availability = 'AVAILABLE_LOCAL' AND t.hidden = 0 ORDER BY t.normalizedTitle")
    fun observeAvailableLocalTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track_sources WHERE contentUri = :contentUri LIMIT 1")
    suspend fun sourceByUri(contentUri: String): TrackSourceEntity?

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun trackById(id: String): TrackEntity?

    @Query("SELECT * FROM track_sources WHERE type = 'LOCAL_MEDIASTORE'")
    suspend fun allLocalSources(): List<TrackSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: TrackSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalMediaSource(source: LocalMediaSourceEntity)

    @Query("UPDATE track_sources SET availability = :availability, lastVerifiedAtEpochMs = :verifiedAt WHERE id = :sourceId")
    suspend fun updateAvailability(sourceId: String, availability: SourceAvailability, verifiedAt: Long)
}
