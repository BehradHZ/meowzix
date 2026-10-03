package dev.behradhz.meowzix.domain.recommendation

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SharedLinUcbTest {
    @Test fun solverMatchesKnownSpdSolution() {
        val matrix = doubleArrayOf(4.0, 1.0, 1.0, 3.0)
        val result = PositiveDefiniteSolver.solve(PositiveDefiniteSolver.cholesky(matrix, 2), doubleArrayOf(1.0, 2.0))
        assertArrayEquals(doubleArrayOf(1.0 / 11.0, 7.0 / 11.0), result, 1e-12)
    }
    @Test fun solverReconstructsManySeededSystems() {
        val random = Random(42)
        for (n in 1..12) {
            val model = SharedLinUcb(n)
            repeat(100) { model.update(DoubleArray(n) { random.nextDouble(-1.0, 1.0) }, random.nextDouble(-1.0, 1.0)) }
            val expected = DoubleArray(n) { random.nextDouble(-1.0, 1.0) }
            val rhs = DoubleArray(n) { i -> (0 until n).sumOf { j -> model.a[i * n + j] * expected[j] } }
            val actual = PositiveDefiniteSolver.solve(PositiveDefiniteSolver.cholesky(model.a, n), rhs)
            assertArrayEquals(expected, actual, 1e-10)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsIndefiniteMatrix() {
        PositiveDefiniteSolver.cholesky(doubleArrayOf(1.0, 2.0, 2.0, 1.0), 2)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNonSymmetricMatrix() {
        PositiveDefiniteSolver.cholesky(doubleArrayOf(1.0, 0.0, 1.0, 1.0), 2)
    }
    @Test fun learnsRewardAndReducesContextualUncertainty() {
        val model = SharedLinUcb(2)
        val x = doubleArrayOf(1.0, 0.0)
        val neutral = model.predict(x)
        repeat(100) { model.update(x, 0.8) }
        val learned = model.predict(x)
        assertTrue(learned.first > 0.75)
        assertTrue(learned.second < neutral.second)
        assertEquals(1.0, model.predict(doubleArrayOf(0.0, 1.0)).second, 1e-12)
    }
    @Test fun weightedUpdateMatchesRepeatedObservation() {
        val weighted = SharedLinUcb(2)
        val repeated = SharedLinUcb(2)
        val x = doubleArrayOf(1.0, 0.5)
        weighted.update(x, -0.7, 3.0)
        repeat(3) { repeated.update(x, -0.7) }
        assertArrayEquals(repeated.a, weighted.a, 1e-12)
        assertArrayEquals(repeated.b, weighted.b, 1e-12)
    }
    @Test fun positiveEveningAndNegativeMorningLearnDifferentContexts() {
        val model = SharedLinUcb()
        val track = TrackPersonalizationFeatures(java.util.UUID.randomUUID(), "artist", false, 180_000)
        val evening = PersonalizationFeatureVectorizer.vectorize(RecommendationContext(19, 2, false, dev.behradhz.meowzix.domain.history.TimeBucket.EVENING), track)
        val morning = PersonalizationFeatureVectorizer.vectorize(RecommendationContext(9, 2, false, dev.behradhz.meowzix.domain.history.TimeBucket.MORNING), track)
        repeat(100) { model.update(evening, 0.95); model.update(morning, -0.70) }
        assertTrue(model.predict(evening).first > model.predict(morning).first + 0.8)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsNanFeatures() {
        SharedLinUcb(1).update(doubleArrayOf(Double.NaN), 0.5)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroWeight() {
        SharedLinUcb(1).update(doubleArrayOf(1.0), 0.5, 0.0)
    }
    @Test fun copiedModelDoesNotMutateOriginal() {
        val original = SharedLinUcb(2)
        val copied = original.copy()
        copied.update(doubleArrayOf(1.0, 1.0), 1.0)
        assertEquals(0.0, original.predict(doubleArrayOf(1.0, 1.0)).first, 0.0)
    }
}
