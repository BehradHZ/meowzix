package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_features",
    foreignKeys = [ForeignKey(entity = TrackEntity::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
)
data class AudioFeatureEntity(
    @PrimaryKey val trackId: String,
    val durationSeconds: Double,
    val fileSizeMegabytes: Double,
    val bitrateKbps: Double?,
    val schemaVersion: Int,
    val extractedAtEpochMs: Long,
)

@Entity(tableName = "personalization_models")
data class PersonalizationModelEntity(
    @PrimaryKey val id: String = "active",
    val modelVersion: Int,
    val featureSchemaVersion: Int,
    val weights: String,
    val sampleCount: Int,
    val updatedAtEpochMs: Long,
)
