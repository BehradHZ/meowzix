package com.behradhz.meowzix.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.behradhz.meowzix.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query(
        """
        SELECT * FROM tracks
        WHERE hidden = 0
        ORDER BY normalizedTitle COLLATE NOCASE ASC,
                 normalizedArtist COLLATE NOCASE ASC
        """,
    )
    fun observeVisibleTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TrackEntity?

    @Upsert
    suspend fun upsert(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)
}
