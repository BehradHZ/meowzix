package dev.behradhz.meowzix.domain.recommendation

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSelectorTest {
    @Test
    fun largeScoreMapProducesEveryTrackExactlyOnce() {
        val scores = (0 until 10_000).associate { index ->
            trackId(index) to ScoreBreakdown(
                total = index.toDouble(),
                globalAffinity = 0.0,
                timeAffinity = 0.0,
                exploration = 0.0,
                recencyPenalty = 0.0,
                artistPenalty = 0.0,
                sessionSkipPenalty = 0.0,
            )
        }

        val ordered = SmartSelector.order(scores, seed = 42L)

        assertEquals(scores.size, ordered.size)
        assertEquals(scores.keys, ordered.toSet())
        assertEquals(ordered.size, ordered.distinct().size)
    }

    @Test
    fun seededOrderingIsDeterministicButNotPureSort() {
        val scores = (0 until 40).associate { index ->
            trackId(index) to ScoreBreakdown(
                total = (40 - index).toDouble(),
                globalAffinity = 0.0,
                timeAffinity = 0.0,
                exploration = 0.0,
                recencyPenalty = 0.0,
                artistPenalty = 0.0,
                sessionSkipPenalty = 0.0,
            )
        }
        val first = SmartSelector.order(scores, seed = 7L)
        val same = SmartSelector.order(scores, seed = 7L)
        val other = SmartSelector.order(scores, seed = 8L)

        assertEquals(first, same)
        assertNotEquals(first, other)
        assertTrue(first.take(10).all(scores.keys::contains))
    }

    private fun trackId(index: Int): UUID = UUID.nameUUIDFromBytes("selector-$index".toByteArray())
}
