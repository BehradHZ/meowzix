package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType

@Entity(
    tableName = "track_sources",
    foreignKeys = [ForeignKey(
        entity = TrackEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("trackId"), Index(value = ["contentUri"], unique = true)],
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
