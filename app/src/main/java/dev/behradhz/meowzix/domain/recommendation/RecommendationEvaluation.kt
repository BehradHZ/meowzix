package dev.behradhz.meowzix.domain.recommendation

import kotlin.math.abs

data class RewardPrediction(val observed: Double, val predicted: Double, val heuristic: Double, val bucket: String)
data class RewardEvaluation(val sampleCount: Int, val meanAbsoluteError: Double?, val heuristicMeanAbsoluteError: Double?,
    val pairwiseAccuracy: Double?, val meanPredictedReward: Double?, val meanObservedReward: Double?)
data class TemporalEvaluation(val trainCount: Int, val testCount: Int, val overall: RewardEvaluation, val byTimeBucket: Map<String, RewardEvaluation>)

/** Frozen temporal holdout, never the currently trained production model. No counterfactual claims. */
object RecommendationEvaluation {
    fun temporalHoldout(dataset: List<TrainingSample>): TemporalEvaluation {
        val samples = dataset.sortedBy { it.dataVersion }.takeLast(10_000)
        if (samples.size < 10) return TemporalEvaluation(0, 0, metrics(emptyList()), emptyMap())
        val split = (samples.size * 0.8).toInt().coerceIn(1, samples.size - 1)
        val model = SharedLinUcb()
        samples.take(split).forEach { model.update(it.features, it.reward, it.weight) }
        val predictions = samples.drop(split).map { sample ->
            val global = sample.features[RecommendationFeature.GLOBAL_AFFINITY.ordinal]
            val time = sample.features[RecommendationFeature.TIME_AFFINITY.ordinal]
            val heuristic = ((0.4 * global + 0.3 * time) / 0.7) * 2.0 - 1.0
            RewardPrediction(sample.reward, model.predict(sample.features).first, heuristic, sample.context?.timeBucket?.name ?: "UNKNOWN")
        }
        return TemporalEvaluation(split, predictions.size, metrics(predictions), predictions.groupBy { it.bucket }.mapValues { metrics(it.value) })
    }

    fun metrics(predictions: List<RewardPrediction>): RewardEvaluation {
        if (predictions.isEmpty()) return RewardEvaluation(0, null, null, null, null, null)
        // Bound quadratic pair comparisons for portable on-device reports.
        val pairs = predictions.takeLast(250)
        var total = 0
        var correct = 0.0
        for (i in pairs.indices) for (j in i + 1 until pairs.size) {
            val actual = pairs[i].observed - pairs[j].observed
            if (abs(actual) < 1e-9) continue
            val predicted = pairs[i].predicted - pairs[j].predicted
            total++
            if (abs(predicted) < 1e-9) correct += 0.5 else if (actual * predicted > 0.0) correct++
        }
        return RewardEvaluation(predictions.size, predictions.map { abs(it.observed - it.predicted) }.average(),
            predictions.map { abs(it.observed - it.heuristic) }.average(), if (total == 0) null else correct / total,
            predictions.map { it.predicted }.average(), predictions.map { it.observed }.average())
    }
}

interface PersonalizationMaintenance {
    suspend fun rebuild()
    suspend fun analyzeAvailableAudio()
    suspend fun debugReport(): String
}
