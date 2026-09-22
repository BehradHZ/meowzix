package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_feature_vectors",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("trackId"),
        Index("sourceIdUsed"),
        Index(
            value = ["trackId", "extractorName", "extractorVersion", "schemaVersion"],
            unique = true,
        ),
    ],
)
data class AudioFeatureVectorEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val sourceIdUsed: String,
    val extractorName: String,
    val extractorVersion: String,
    val schemaVersion: Int,
    val vectorFormat: String,
    val vectorBlob: ByteArray,
    val generatedAtEpochMs: Long,
    val sourceContentHash: String?,
)
