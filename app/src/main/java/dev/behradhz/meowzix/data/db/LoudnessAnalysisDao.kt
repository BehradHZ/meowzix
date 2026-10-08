package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LoudnessAnalysisDao {
    @Query("""
        SELECT * FROM loudness_analyses
        WHERE trackId = :trackId AND analysisVersion = :analysisVersion
        ORDER BY generatedAtEpochMs DESC
        LIMIT 1
    """)
    suspend fun latest(trackId: String, analysisVersion: Int): LoudnessAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: LoudnessAnalysisEntity)

    @Query("DELETE FROM loudness_analyses WHERE trackId = :trackId AND analysisVersion != :analysisVersion")
    suspend fun deleteStaleVersions(trackId: String, analysisVersion: Int)
}
