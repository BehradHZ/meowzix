package com.behradhz.meowzix.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.behradhz.meowzix.data.db.entity.TrackSourceEntity

@Dao
interface TrackSourceDao {
    @Query("SELECT * FROM track_sources WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TrackSourceEntity?

    @Query("SELECT COUNT(*) FROM track_sources WHERE trackId = :trackId")
    suspend fun countForTrack(trackId: String): Int

    @Upsert
    suspend fun upsert(source: TrackSourceEntity)

    @Query("DELETE FROM track_sources WHERE id = :id")
    suspend fun deleteById(id: String)
}
