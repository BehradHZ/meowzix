package dev.behradhz.meowzix.data.recommendation

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.domain.recommendation.PersonalizationFeatureVectorizer
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModel
import dev.behradhz.meowzix.domain.recommendation.PersonalizationModelState
import dev.behradhz.meowzix.domain.recommendation.TrainingSample
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.tanh
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
        val snapshotWeights = weights
        return features.mapValues { (_, vector) ->
            // Map the linear model's [-1, 1] predicted reward into [0, 1] for Smart scoring.
            ((tanh(dot(snapshotWeights, vector)) + 1.0) / 2.0).coerceIn(0.0, 1.0)
        }
    }

    override suspend fun update(samples: List<TrainingSample>) {
        if (samples.isEmpty()) return
        mutex.withLock {
            loadLocked()
            val freshSamples = samples.filter { it.dataVersion > modelState.trainingDataVersion }
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
            val floor = modelState.trainingDataVersion.takeIf { modelState.sampleCount == 0L } ?: 0L
            val eligible = samples.filter { it.dataVersion > floor }
            weights = DoubleArray(PersonalizationFeatureVectorizer.FEATURE_COUNT)
            repeat(REBUILD_EPOCHS) {
                eligible.sortedBy { sample -> sample.dataVersion }.forEach { train(it, epochs = 1) }
            }
            modelState = PersonalizationModelState(
                trainingDataVersion = maxOf(floor, eligible.maxOfOrNull { it.dataVersion } ?: 0L),
                trainedAtEpochMs = System.currentTimeMillis(),
                sampleCount = eligible.size.toLong(),
                active = eligible.size >= MIN_TRAINING_SAMPLES,
            )
            persistLocked()
        }
    }

    override suspend fun reset(trainingDataVersion: Long) {
        mutex.withLock {
            weights = DoubleArray(PersonalizationFeatureVectorizer.FEATURE_COUNT)
            modelState = PersonalizationModelState(trainingDataVersion = trainingDataVersion.coerceAtLeast(0L))
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
        if (
            schema != PersonalizationFeatureVectorizer.SCHEMA_VERSION ||
            decoded == null || decoded.size != PersonalizationFeatureVectorizer.FEATURE_COUNT
        ) {
            weights = DoubleArray(PersonalizationFeatureVectorizer.FEATURE_COUNT)
            modelState = PersonalizationModelState(
                trainingDataVersion = values[TRAINING_DATA_VERSION] ?: 0L,
            )
        } else {
            weights = decoded.toDoubleArray()
            val count = values[SAMPLE_COUNT] ?: 0L
            modelState = PersonalizationModelState(
                modelVersion = values[MODEL_VERSION] ?: MODEL_VERSION_VALUE,
                featureSchemaVersion = schema,
                trainingDataVersion = values[TRAINING_DATA_VERSION] ?: 0L,
                trainedAtEpochMs = values[TRAINED_AT] ?: 0L,
                sampleCount = count,
                active = count >= MIN_TRAINING_SAMPLES,
            )
        }
        loaded = true
    }

    private fun train(sample: TrainingSample, epochs: Int) {
        require(sample.features.size == weights.size) { "Unexpected personalization feature vector size." }
        repeat(epochs) {
            val prediction = tanh(dot(weights, sample.features))
            val error = sample.reward.coerceIn(-1.0, 1.0) - prediction
            for (index in weights.indices) {
                val feature = sample.features[index]
                if (feature == 0.0) continue
                val gradient = error * feature * sample.weight - L2 * weights[index]
                weights[index] = (weights[index] + LEARNING_RATE * gradient).coerceIn(-MAX_WEIGHT, MAX_WEIGHT)
            }
        }
    }

    private suspend fun persistLocked() {
        val encoded = weights.joinToString(separator = ",") { value -> "%.8g".format(java.util.Locale.US, value) }
        context.personalizationModelDataStore.edit { values ->
            values[WEIGHTS] = encoded
            values[MODEL_VERSION] = modelState.modelVersion
            values[FEATURE_SCHEMA] = modelState.featureSchemaVersion
            values[TRAINING_DATA_VERSION] = modelState.trainingDataVersion
            values[TRAINED_AT] = modelState.trainedAtEpochMs
            values[SAMPLE_COUNT] = modelState.sampleCount
        }
    }

    private fun dot(a: DoubleArray, b: DoubleArray): Double {
        val limit = minOf(a.size, b.size)
        var result = 0.0
        for (index in 0 until limit) result += a[index] * b[index]
        return result
    }

    companion object {
        const val MIN_TRAINING_SAMPLES = 50
        private const val MODEL_VERSION_VALUE = "1"
        private const val LEARNING_RATE = 0.06
        private const val L2 = 0.0005
        private const val MAX_WEIGHT = 4.0
        private const val INCREMENTAL_EPOCHS = 2
        private const val REBUILD_EPOCHS = 6

        private val WEIGHTS = stringPreferencesKey("weights")
        private val MODEL_VERSION = stringPreferencesKey("model_version")
        private val FEATURE_SCHEMA = intPreferencesKey("feature_schema")
        private val TRAINING_DATA_VERSION = longPreferencesKey("training_data_version")
        private val TRAINED_AT = longPreferencesKey("trained_at")
        private val SAMPLE_COUNT = longPreferencesKey("sample_count")
    }
}
