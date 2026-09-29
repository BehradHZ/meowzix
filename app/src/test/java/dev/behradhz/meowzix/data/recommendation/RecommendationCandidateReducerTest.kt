package dev.behradhz.meowzix.data.recommendation

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationCandidateReducerTest {
    @Test
    fun tenThousandTracksAreReducedToBoundedDistinctPoolWithPriorities() {
        val allowed = (0 until 10_000).map(::trackId)
        val priority = allowed.take(200)

        val reduced = reduceRecommendationCandidateIds(
            allowedTrackIds = allowed,
            priorityTrackIds = priority,
            limit = 800,
            seed = 42L,
        )

        assertEquals(800, reduced.size)
        assertEquals(800, reduced.distinct().size)
        assertTrue(priority.all(reduced::contains))
        assertTrue(reduced.all(allowed.toHashSet()::contains))
    }

    @Test
    fun samplingIsDeterministicForSeedAndChangesAcrossSeeds() {
        val allowed = (0 until 2_000).map(::trackId)
        val first = reduceRecommendationCandidateIds(allowed, emptyList(), 400, 7L)
        val same = reduceRecommendationCandidateIds(allowed, emptyList(), 400, 7L)
        val other = reduceRecommendationCandidateIds(allowed, emptyList(), 400, 8L)

        assertEquals(first, same)
        assertNotEquals(first, other)
    }

    @Test
    fun smallAllowedSetIsPreservedExactly() {
        val allowed = (0 until 20).map(::trackId)
        val reduced = reduceRecommendationCandidateIds(allowed, allowed.reversed(), 800, 1L)
        assertEquals(allowed, reduced)
    }

    private fun trackId(index: Int): UUID = UUID.nameUUIDFromBytes("track-$index".toByteArray())
}
