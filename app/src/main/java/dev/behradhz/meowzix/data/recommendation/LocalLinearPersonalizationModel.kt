package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.recommendation.LinearRankerMath
import dev.behradhz.meowzix.domain.recommendation.PersonalizationFeatureVectorizer
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModelState
import dev.behradhz.meowzix.domain.recommendation.TrainingSample
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.personalizationModelDataStore by preferencesDataStore("personalization_model")

@Singleton
class LocalLinearPersonalizationModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PersonalizationModel {
    private val mutex = Mutex()
    @Volatile private var loaded = false
    private var weights = DoubleArray(PersonalizationFeatureVectorizer.FEATURE_COUNT)
    private var modelState = PersonalizationModelState()

    override suspend fun state(): PersonalizationModelState {
        ensureLoaded()
        return modelState
    }

    override suspend fun scoreBatch(features: Map<UUID, DoubleArray>): Map<UUID, Double> {
        ensureLoaded()
        val snapshotState = modelState
        if (!snapshotState.active) return emptyMap()
        val snapshotWeights = weights.copyOf()
        return features.mapValues { (_, vector) ->
            LinearRankerMath.preferenceScore(snapshotWeights, vector)
        }
    }

    override suspend fun update(samples: List<TrainingSample>) {
        if (samples.isEmpty()) return
        mutex.withLock {
            loadLocked()
            val freshSamples = samples.filter {
                it.dataVersion > modelState.trainingDataVersion && it.dataVersion > modelState.historyFloorVersion
            }
            if (freshSamples.isEmpty()) return@withLock
            freshSamples.sortedBy { it.dataVersion }.forEach { train(it, epochs = INCREMENTAL_EPOCHS) }
            val newCount = modelState.sampleCount + freshSamples.size
            modelState = modelState.copy(
                trainingDataVersion = freshSamples.maxOf { it.dataVersion },
                trainedAtEpochMs = System.currentTimeMillis(),
                sampleCount = newCount,
                active = newCount >= MIN_TRAINING_SAMPLES,
            )
            persistLocked()
        }
    }

    override suspend fun rebuild(samples: List<TrainingSample>) {
        mutex.withLock {
            loadLocked()
            val floor = modelState.historyFloorVersion
            val eligible = samples.filter { it.dataVersion > floor }
            weights = DoubleArray(PersonalizationFeatureVectorizer.FEATURE_COUNT)
            repeat(REBUILD_EPOCHS) {
                eligible.sortedBy { sample -> sample.dataVersion }.forEach { train(it, epochs = 1) }
            }
            modelState = PersonalizationModelState(
                trainingDataVersion = maxOf(floor, eligible.maxOfOrNull { it.dataVersion } ?: 0L),
                historyFloorVersion = floor,
                trainedAtEpochMs = System.currentTimeMillis(),
                sampleCount = eligible.size.toLong(),
                active = eligible.size >= MIN_TRAINING_SAMPLES,
            )
            persistLocked()
        }
    }

    override suspend fun reset(trainingDataVersion: Long) {
        mutex.withLock {
            val floor = trainingDataVersion.coerceAtLeast(0L)
            weights = DoubleArray(PersonalizationFeatureVectorizer.FEATURE_COUNT)
            modelState = PersonalizationModelState(
                trainingDataVersion = floor,
                historyFloorVersion = floor,
            )
            loaded = true
            persistLocked()
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock { loadLocked() }
    }

    private suspend fun loadLocked() {
        if (loaded) return
        val values = context.personalizationModelDataStore.data.first()
        val schema = values[FEATURE_SCHEMA] ?: PersonalizationFeatureVectorizer.SCHEMA_VERSION
        val encodedWeights = values[WEIGHTS]
        val decoded = encodedWeights?.split(',')?.mapNotNull(String::toDoubleOrNull)
        val trainingDataVersion = values[TRAINING_DATA_VERSION] ?: 0L
        val historyFloorVersion = values[HISTORY_FLOOR_VERSION] ?: 0L
        if (
            schema != PersonalizationFeatureVectorizer.SCHEMA_VERSION ||
            decoded == null || decoded.size != PersonalizationFeatureVectorizer.FEATURE_COUNT
        ) {
            weights = DoubleArray(PersonalizationFeatureVectorizer.FEATURE_COUNT)
            modelState = PersonalizationModelState(
                trainingDataVersion = maxOf(trainingDataVersion, historyFloorVersion),
                historyFloorVersion = historyFloorVersion,
            )
        } else {
            weights = decoded.toDoubleArray()
            val count = values[SAMPLE_COUNT] ?: 0L
            modelState = PersonalizationModelState(
                modelVersion = values[MODEL_VERSION] ?: MODEL_VERSION_VALUE,
                featureSchemaVersion = schema,
                trainingDataVersion = maxOf(trainingDataVersion, historyFloorVersion),
                historyFloorVersion = historyFloorVersion,
                trainedAtEpochMs = values[TRAINED_AT] ?: 0L,
                sampleCount = count,
                active = count >= MIN_TRAINING_SAMPLES,
            )
        }
        loaded = true
    }

    private fun train(sample: TrainingSample, epochs: Int) {
        LinearRankerMath.trainInPlace(
            weights = weights,
            sample = sample,
            epochs = epochs,
            learningRate = LEARNING_RATE,
            l2 = L2,
            maxWeight = MAX_WEIGHT,
        )
    }

    private suspend fun persistLocked() {
        val encoded = weights.joinToString(separator = ",") { value -> "%.8g".format(java.util.Locale.US, value) }
        context.personalizationModelDataStore.edit { values ->
            values[WEIGHTS] = encoded
            values[MODEL_VERSION] = modelState.modelVersion
            values[FEATURE_SCHEMA] = modelState.featureSchemaVersion
            values[TRAINING_DATA_VERSION] = modelState.trainingDataVersion
            values[HISTORY_FLOOR_VERSION] = modelState.historyFloorVersion
            values[TRAINED_AT] = modelState.trainedAtEpochMs
            values[SAMPLE_COUNT] = modelState.sampleCount
        }
    }

    companion object {
        const val MIN_TRAINING_SAMPLES = 50
        internal const val MODEL_VERSION_VALUE = "1"
        internal const val LEARNING_RATE = 0.06
        internal const val L2 = 0.0005
        internal const val MAX_WEIGHT = 4.0
        internal const val INCREMENTAL_EPOCHS = 2
        internal const val REBUILD_EPOCHS = 6

        private val WEIGHTS = stringPreferencesKey("weights")
        private val MODEL_VERSION = stringPreferencesKey("model_version")
        private val FEATURE_SCHEMA = intPreferencesKey("feature_schema")
        private val TRAINING_DATA_VERSION = longPreferencesKey("training_data_version")
        private val HISTORY_FLOOR_VERSION = longPreferencesKey("history_floor_version")
        private val TRAINED_AT = longPreferencesKey("trained_at")
        private val SAMPLE_COUNT = longPreferencesKey("sample_count")
    }
}
