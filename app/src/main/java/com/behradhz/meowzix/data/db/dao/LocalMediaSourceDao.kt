package com.behradhz.meowzix.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.behradhz.meowzix.data.db.entity.LocalMediaSourceEntity

@Dao
interface LocalMediaSourceDao {
    @Query("SELECT * FROM local_media_sources WHERE contentUri = :contentUri LIMIT 1")
    suspend fun findByContentUri(contentUri: String): LocalMediaSourceEntity?

    @Query("SELECT * FROM local_media_sources")
    suspend fun getAll(): List<LocalMediaSourceEntity>

    @Upsert
    suspend fun upsert(source: LocalMediaSourceEntity)

    @Query("DELETE FROM local_media_sources WHERE trackSourceId = :trackSourceId")
    suspend fun deleteByTrackSourceId(trackSourceId: String)
}
