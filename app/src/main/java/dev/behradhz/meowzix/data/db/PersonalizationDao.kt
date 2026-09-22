package dev.behradhz.meowzix.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PersonalizationDao {
    @Query("SELECT * FROM audio_features") suspend fun allAudioFeatures(): List<AudioFeatureEntity>
    @Query("SELECT trackId FROM audio_features") suspend fun tracksWithAudioFeatures(): List<String>
    @Upsert suspend fun upsertAudioFeatures(features: AudioFeatureEntity)

    @Query("SELECT * FROM personalization_models WHERE id = 'active' LIMIT 1")
    suspend fun activeModel(): PersonalizationModelEntity?
    @Upsert suspend fun upsertModel(model: PersonalizationModelEntity)
    @Query("DELETE FROM personalization_models") suspend fun clearModels()
    @Query("DELETE FROM audio_features") suspend fun clearAudioFeatures()
}
