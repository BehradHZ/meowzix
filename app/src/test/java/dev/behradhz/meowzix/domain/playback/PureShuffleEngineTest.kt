package dev.behradhz.meowzix.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PureShuffleEngineTest {
    @Test
    fun `every item appears exactly once in thousands of cycles`() {
        val eligible = (0 until 32).toList()
        var previousLast: Int? = null

        repeat(5_000) { seed ->
            val cycle = PureShuffleEngine.newCycle(eligible, previousLast, seed.toLong())

            assertEquals(eligible.size, cycle.order.size)
            assertEquals(eligible.toSet(), cycle.order.toSet())
            if (previousLast != null) assertNotEquals(previousLast, cycle.order.first())
            previousLast = cycle.order.last()
        }
    }

    @Test
    fun `restoring a seed reproduces the same order`() {
        val eligible = (0 until 100).toList()

        val first = PureShuffleEngine.newCycle(eligible, previousLastItem = 42, seed = 8675309)
        val restored = PureShuffleEngine.newCycle(eligible, previousLastItem = 42, seed = first.seed)

        assertEquals(first, restored)
    }

    @Test
    fun `first position is not trivially fixed or concentrated`() {
        val eligible = (0 until 16).toList()
        val counts = IntArray(eligible.size)

        repeat(8_000) { seed ->
            counts[PureShuffleEngine.newCycle(eligible, seed = seed.toLong()).order.first()]++
        }

        assertTrue(counts.all { it > 350 })
        assertTrue(counts.all { it < 650 })
    }

    @Test
    fun `cycle boundary avoids an immediate duplicate`() {
        val eligible = listOf("a", "b", "c")
        val initial = PureShuffleEngine.newCycle(eligible, seed = 1)
        val next = PureShuffleEngine.newCycle(eligible, initial.order.last(), seed = 2)

        assertNotEquals(initial.order.last(), next.order.first())
    }

    @Test
    fun `single item cycles without artificial bias`() {
        assertEquals(listOf("only"), PureShuffleEngine.newCycle(listOf("only"), "only", 1).order)
    }

    @Test
    fun `direct selections get independent random cycles with the chosen track first`() {
        val eligible = (0 until 50).toList()
        val first = PureShuffleEngine.newCycleStartingWith(eligible, selectedItem = 23, seed = 101L)
        val second = PureShuffleEngine.newCycleStartingWith(eligible, selectedItem = 23, seed = 202L)

        assertEquals(23, first.order.first())
        assertEquals(23, second.order.first())
        assertEquals(eligible.size, first.order.size)
        assertEquals(eligible.toSet(), first.order.toSet())
        assertEquals(eligible.toSet(), second.order.toSet())
        assertNotEquals(first.seed, second.seed)
        assertNotEquals(first.order, second.order)
        assertEquals(first, PureShuffleEngine.newCycleStartingWith(eligible, 23, seed = first.seed))
    }

    @Test
    fun `selected item is required and duplicates are rejected for anchored cycles`() {
        assertThrows(IllegalArgumentException::class.java) {
            PureShuffleEngine.newCycleStartingWith(listOf(1, 2), 3, seed = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PureShuffleEngine.newCycleStartingWith(listOf(1, 1, 2), 1, seed = 1)
        }
        assertEquals(listOf("one"), PureShuffleEngine.newCycleStartingWith(listOf("one"), "one", 1).order)
    }

    @Test
    fun `direct selection distributes remaining tracks without a fixed position`() {
        val counts = IntArray(10)
        repeat(5_000) { seed ->
            val cycle = PureShuffleEngine.newCycleStartingWith((0..10).toList(), 10, seed.toLong())
            assertEquals(10, cycle.order.first())
            counts[cycle.order[1]]++
        }
        assertTrue(counts.all { it in 370..630 })
    }

    @Test
    fun `duplicate eligible inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PureShuffleEngine.newCycle(listOf("a", "a"), seed = 1)
        }
    }
}
