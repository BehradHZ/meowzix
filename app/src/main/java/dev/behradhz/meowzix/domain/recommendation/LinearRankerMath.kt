package dev.behradhz.meowzix.domain.recommendation

import kotlin.math.tanh

/** Pure deterministic math used by the persisted local model and JVM evaluation tests. */
object LinearRankerMath {
    fun preferenceScore(weights: DoubleArray, features: DoubleArray): Double =
        ((tanh(rawScore(weights, features)) + 1.0) / 2.0).coerceIn(0.0, 1.0)

    fun rawScore(weights: DoubleArray, features: DoubleArray): Double {
        val limit = minOf(weights.size, features.size)
        var result = 0.0
        for (index in 0 until limit) result += weights[index] * features[index]
        return result
    }

    fun trainInPlace(
        weights: DoubleArray,
        sample: TrainingSample,
        epochs: Int,
        learningRate: Double,
        l2: Double,
        maxWeight: Double,
    ) {
        require(sample.features.size == weights.size) { "Unexpected personalization feature vector size." }
        repeat(epochs.coerceAtLeast(0)) {
            val prediction = tanh(rawScore(weights, sample.features))
            val error = sample.reward.coerceIn(-1.0, 1.0) - prediction
            for (index in weights.indices) {
                val feature = sample.features[index]
                if (feature == 0.0) continue
                val gradient = error * feature * sample.weight - l2 * weights[index]
                weights[index] = (weights[index] + learningRate * gradient).coerceIn(-maxWeight, maxWeight)
            }
        }
    }
}
