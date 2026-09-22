package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_records",
    foreignKeys = [ForeignKey(
        entity = TrackEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("trackId", unique = true),
        Index("tdFileId"),
        Index("updatedAtEpochMs"),
    ],
)
data class DownloadRecordEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val trackSourceId: String,
    val tdFileId: Int?,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val localPath: String?,
    val pinned: Boolean,
    val failureReason: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
