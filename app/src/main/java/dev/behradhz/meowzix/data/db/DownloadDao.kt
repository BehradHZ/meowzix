package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_records ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Query("SELECT * FROM download_records WHERE trackId = :trackId LIMIT 1")
    suspend fun byTrackId(trackId: String): DownloadRecordEntity?

    @Query("SELECT * FROM download_records WHERE tdFileId = :tdFileId LIMIT 1")
    suspend fun byTdFileId(tdFileId: Int): DownloadRecordEntity?

    @Query("SELECT * FROM download_records")
    suspend fun all(): List<DownloadRecordEntity>

    @Upsert
    suspend fun upsert(record: DownloadRecordEntity)

    @Query("DELETE FROM download_records WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)
}
