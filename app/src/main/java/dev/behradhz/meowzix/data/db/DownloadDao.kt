package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_records ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Query(
        """
        SELECT d.trackId AS trackId,
               d.status AS status,
               d.downloadedBytes AS downloadedBytes,
               d.totalBytes AS totalBytes,
               d.pinned AS pinned,
               d.failureReason AS failureReason,
               d.updatedAtEpochMs AS updatedAtEpochMs,
               COALESCE(t.title, 'Unknown track') AS title,
               t.artworkRef AS artworkRef
        FROM download_records d
        LEFT JOIN tracks t ON t.id = d.trackId
        ORDER BY d.updatedAtEpochMs DESC
        """,
    )
    fun observeDisplayRows(): Flow<List<DownloadDisplayRow>>

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


data class DownloadDisplayRow(
    val trackId: String,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val pinned: Boolean,
    val failureReason: String?,
    val updatedAtEpochMs: Long,
    val title: String,
    val artworkRef: String?,
)
