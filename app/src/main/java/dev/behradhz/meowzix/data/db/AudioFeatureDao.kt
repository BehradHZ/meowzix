package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AudioFeatureDao {
    @Query(
        """
        SELECT * FROM audio_feature_vectors
        WHERE trackId = :trackId
          AND extractorName = :extractorName
          AND extractorVersion = :extractorVersion
          AND schemaVersion = :schemaVersion
        LIMIT 1
        """,
    )
    suspend fun compatibleVector(
        trackId: String,
        extractorName: String,
        extractorVersion: String,
        schemaVersion: Int,
    ): AudioFeatureVectorEntity?

    @Query(
        """
        SELECT * FROM audio_feature_vectors
        WHERE extractorName = :extractorName
          AND extractorVersion = :extractorVersion
          AND schemaVersion = :schemaVersion
        """,
    )
    suspend fun compatibleVectors(
        extractorName: String,
        extractorVersion: String,
        schemaVersion: Int,
    ): List<AudioFeatureVectorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(vector: AudioFeatureVectorEntity)

    @Query("SELECT MAX(generatedAtEpochMs) FROM audio_feature_vectors")
    suspend fun latestGeneratedAt(): Long?

    @Query("DELETE FROM audio_feature_vectors WHERE trackId = :trackId")
    suspend fun deleteForTrack(trackId: String)
}
