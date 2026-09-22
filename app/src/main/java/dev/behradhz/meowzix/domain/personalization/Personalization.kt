package dev.behradhz.meowzix.domain.personalization

import dev.behradhz.meowzix.domain.history.TimeBucket
import java.util.UUID
import kotlin.math.abs

const val FEATURE_SCHEMA_VERSION = 1
const val MODEL_VERSION = 1

data class AudioFeatureVector(
    val durationSeconds: Double,
    val fileSizeMegabytes: Double,
    val bitrateKbps: Double? = null,
    val schemaVersion: Int = FEATURE_SCHEMA_VERSION,
)

data class TrainingSample(
    val trackId: UUID,
    val features: List<Double>,
    val reward: Double,
)

data class LinearModelState(
    val weights: List<Double>,
    val sampleCount: Int,
    val modelVersion: Int = MODEL_VERSION,
    val featureSchemaVersion: Int = FEATURE_SCHEMA_VERSION,
)

data class PersonalizationEvaluation(
    val samples: Int,
    val meanAbsoluteError: Double,
    val modelVersion: Int,
    val featureSchemaVersion: Int,
)

interface TrainingDatasetBuilder {
    suspend fun build(timeBucket: TimeBucket): List<TrainingSample>
}

interface AudioFeatureExtractor {
    suspend fun extract(trackId: UUID): AudioFeatureVector?
}

interface PersonalizationModel {
    fun train(samples: List<TrainingSample>, initial: LinearModelState? = null): LinearModelState
    fun score(features: List<Double>, state: LinearModelState): Double
    fun evaluate(samples: List<TrainingSample>, state: LinearModelState): PersonalizationEvaluation
}

interface PersonalizationRepository {
    suspend fun scoreAdjustments(trackIds: List<UUID>, timeBucket: TimeBucket): Map<UUID, Double>
    suspend fun rebuild(timeBucket: TimeBucket): PersonalizationEvaluation?
    suspend fun reset()
    suspend fun extractMissingAudioFeatures(): Int
}

class OnlineLinearPersonalizationModel(
    private val learningRate: Double = 0.08,
    private val epochs: Int = 30,
) : PersonalizationModel {
    override fun train(samples: List<TrainingSample>, initial: LinearModelState?): LinearModelState {
        if (samples.isEmpty()) return LinearModelState(emptyList(), 0)
        val width = samples.first().features.size
        val weights = initial?.weights?.takeIf { it.size == width }?.toMutableList()
            ?: MutableList(width) { 0.0 }
        repeat(epochs) {
            samples.sortedBy { it.trackId }.forEach { sample ->
                val prediction = dot(sample.features, weights)
                val error = sample.reward - prediction
                weights.indices.forEach { index ->
                    weights[index] += learningRate * error * sample.features[index]
                }
            }
        }
        return LinearModelState(weights, samples.size)
    }

    override fun score(features: List<Double>, state: LinearModelState): Double =
        if (features.size != state.weights.size) 0.0 else dot(features, state.weights)

    override fun evaluate(samples: List<TrainingSample>, state: LinearModelState): PersonalizationEvaluation {
        val error = samples.map { abs(it.reward - score(it.features, state)) }.average().takeUnless(Double::isNaN) ?: 0.0
        return PersonalizationEvaluation(samples.size, error, state.modelVersion, state.featureSchemaVersion)
    }

    private fun dot(features: List<Double>, weights: List<Double>) =
        features.indices.sumOf { features[it] * weights[it] }
}
