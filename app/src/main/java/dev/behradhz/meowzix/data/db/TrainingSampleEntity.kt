package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Derived, rebuildable data. One row per canonical playback occurrence, not per source. */
@Entity(tableName = "training_samples", foreignKeys = [ForeignKey(entity = TrackEntity::class,
    parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("trackId"), Index("dataVersion")])
data class TrainingSampleEntity(
    @PrimaryKey val playbackInstanceId: String,
    val trackId: String,
    val features: ByteArray,
    val reward: Double,
    val weight: Double,
    val dataVersion: Long,
    val featureSchemaVersion: Int,
    val rewardSchemaVersion: Int,
    val generatedAtEpochMs: Long,
    val eventIds: String,
    val sourceIdUsedForAudio: String?,
    val audioExtractorVersion: String?,
)

@Dao
interface TrainingSampleDao {
    @Upsert suspend fun put(samples: List<TrainingSampleEntity>)
    @Query("SELECT * FROM training_samples WHERE dataVersion > :after AND featureSchemaVersion = :feature AND rewardSchemaVersion = :reward ORDER BY dataVersion")
    suspend fun samplesAfter(after: Long, feature: Int, reward: Int): List<TrainingSampleEntity>
    @Query("DELETE FROM training_samples") suspend fun clear()
}
