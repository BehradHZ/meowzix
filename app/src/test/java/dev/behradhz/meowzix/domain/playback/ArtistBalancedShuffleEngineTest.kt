package dev.behradhz.meowzix.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistBalancedShuffleEngineTest {
    @Test fun `seeded cycles cover every song exactly once`() {
        val tracks = (0 until 300).map { ArtistShuffleItem(it, "artist${it % 12}") }
        repeat(200) { seed ->
            val cycle = ArtistBalancedShuffleEngine.newCycle(tracks, seed = seed.toLong())
            assertEquals(300, cycle.order.size)
            assertEquals((0 until 300).toSet(), cycle.order.toSet())
            assertEquals(cycle, ArtistBalancedShuffleEngine.newCycle(tracks, seed = cycle.seed))
        }
    }
    @Test fun `early artist exposure is balanced without dropping large artists`() {
        val tracks = (0 until 100).map { ArtistShuffleItem(it, "large") } +
            (1..90).flatMap { a -> (0 until 10).map { ArtistShuffleItem(100 + a * 10 + it, "artist$a") } }
        val plays = (0 until 120).sumOf { seed ->
            ArtistBalancedShuffleEngine.newCycle(tracks, seed = seed.toLong())
                .order.take(100).count { it < 100 }
        }
        val share = plays.toDouble() / 12000
        assertTrue("early exposure=$share", share in 0.04..0.085)
        assertEquals(100, ArtistBalancedShuffleEngine.newCycle(tracks, seed = 1).order.count { it < 100 })
    }
    @Test fun `bounded exposure avoids starting with recently played tracks`() {
        val tracks = (0 until 500).map { ArtistShuffleItem(it, "artist${it % 50}") }
        val recent = (0 until 40).toList()
        assertFalse(ArtistBalancedShuffleEngine.newCycle(tracks, recent, seed = 87)
            .order.take(25).any { it in recent })
    }
    @Test fun `manual selections stay anchored and single artist queues work`() {
        val tracks = (0 until 10).map { ArtistShuffleItem(it, "one") }
        val cycle = ArtistBalancedShuffleEngine.newCycleStartingWith(tracks, 6, seed = 44)
        assertEquals(6, cycle.order.first())
        assertEquals((0 until 10).toSet(), cycle.order.toSet())
        assertEquals(cycle, ArtistBalancedShuffleEngine.newCycleStartingWith(tracks, 6, seed = 44))
    }
    @Test fun `exploration choice remains seeded and source agnostic`() {
        val items = (0 until 20).map { ArtistShuffleItem(it, if (it < 10) "major" else "artist$it") }
        repeat(100) { seed ->
            val selected = ArtistBalancedShuffleEngine.pickExploration(items, listOf("major"), seed.toLong())
            assertTrue(selected in 0 until 20)
            assertEquals(selected, ArtistBalancedShuffleEngine.pickExploration(items, listOf("major"), seed.toLong()))
        }
    }
}
