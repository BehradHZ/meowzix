package dev.behradhz.meowzix.domain.recommendation

import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class RecommendationEvaluationTest {
    private fun sample(i: Int, reward: Double) = TrainingSample(UUID(0, i.toLong()), DoubleArray(PersonalizationFeatureVectorizer.FEATURE_COUNT).also {
        it[RecommendationFeature.BIAS.ordinal] = 1.0
    }, reward, dataVersion = i.toLong())
    @Test fun insufficientDataIsExplicitlyUnevaluated() {
        val result = RecommendationEvaluation.temporalHoldout((1..9).map { sample(it, 0.5) })
        assertNull(result.overall.meanAbsoluteError)
        assertEquals(0, result.testCount)
    }
    @Test fun futureLabelsDoNotChangeFrozenModelPredictions() {
        val training = (1..80).map { sample(it, 0.8) }
        val positive = RecommendationEvaluation.temporalHoldout(training + (81..100).map { sample(it, 1.0) })
        val negative = RecommendationEvaluation.temporalHoldout(training + (81..100).map { sample(it, -1.0) })
        assertEquals(80, positive.trainCount)
        assertEquals(20, positive.testCount)
        assertEquals(positive.overall.meanPredictedReward, negative.overall.meanPredictedReward)
        assertNotEquals(positive.overall.meanAbsoluteError, negative.overall.meanAbsoluteError)
    }
    @Test fun rankingTiesGetHalfCredit() {
        val metrics = RecommendationEvaluation.metrics(listOf(RewardPrediction(1.0, 0.0, 0.0, "NIGHT"), RewardPrediction(-1.0, 0.0, 0.0, "NIGHT")))
        assertEquals(0.5, metrics.pairwiseAccuracy!!, 0.0)
        assertEquals(1.0, metrics.meanAbsoluteError!!, 0.0)
    }
    @Test fun chronologicalSortingMakesInputOrderIrrelevant() {
        val samples = (1..100).map { sample(it, if (it % 2 == 0) 0.5 else -0.5) }
        assertEquals(RecommendationEvaluation.temporalHoldout(samples), RecommendationEvaluation.temporalHoldout(samples.reversed()))
    }
}
