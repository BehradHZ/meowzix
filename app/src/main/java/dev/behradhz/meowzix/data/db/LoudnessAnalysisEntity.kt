package dev.behradhz.meowzix.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loudness_analyses",
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
        Index("sourceContentHash"),
        Index(value = ["trackId", "analysisVersion", "algorithmVersion"], unique = true),
    ],
)
data class LoudnessAnalysisEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val sourceIdUsed: String?,
    val analysisVersion: Int,
    val algorithm: String,
    val algorithmVersion: String,
    val measuredValueDb: Double?,
    val suggestedGainDb: Float,
    val confidence: Float,
    val sourceContentHash: String?,
    val sourceProvenance: String,
    val generatedAtEpochMs: Long,
)
