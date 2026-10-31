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

    @Test
    fun discoveryReservesUnplayedTracksAndAdvancesWhenTheyAreHeard() {
        val allowed = (0 until 2_000).map(::trackId)
        val priorities = allowed.take(150)
        val unheard = allowed.drop(500).take(900)
        val first = reduceRecommendationCandidateIds(allowed, priorities, 800, 42L, unheard, 50)

        assertEquals(800, first.size)
        assertEquals(800, first.distinct().size)
        assertTrue(priorities.all(first::contains))
        assertTrue(unheard.take(400).all(first::contains))

        // After first plays, remaining never-played IDs take the reserved slots.
        val nextUnheard = unheard.drop(40)
        val next = reduceRecommendationCandidateIds(allowed, priorities, 800, 43L, nextUnheard, 50)
        assertTrue(nextUnheard.take(400).all(next::contains))
        assertTrue(next.all(allowed.toSet()::contains))
    }

    @Test
    fun zeroExplorationLeavesLegacySamplingUnchanged() {
        val allowed = (0 until 2_000).map(::trackId)
        val unheard = allowed.drop(400)
        val baseline = reduceRecommendationCandidateIds(allowed, emptyList(), 400, 7L)
        val off = reduceRecommendationCandidateIds(allowed, emptyList(), 400, 7L, unheard, 0)
        assertEquals(baseline, off)
    }

}
