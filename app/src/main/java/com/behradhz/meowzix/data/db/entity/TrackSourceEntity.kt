package com.behradhz.meowzix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.behradhz.meowzix.core.model.SourceAvailability
import com.behradhz.meowzix.core.model.TrackSourceType

@Entity(
    tableName = "track_sources",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trackId"])],
)
data class TrackSourceEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val type: TrackSourceType,
    val availability: SourceAvailability,
    val contentUri: String?,
    val localPath: String?,
    val mimeType: String?,
    val fileSizeBytes: Long?,
    val contentHashSha256: String?,
    val trainingEligible: Boolean,
    val createdAtEpochMs: Long,
    val lastVerifiedAtEpochMs: Long?,
)
