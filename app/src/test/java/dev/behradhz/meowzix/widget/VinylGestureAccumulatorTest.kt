package dev.behradhz.meowzix.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class VinylGestureAccumulatorTest {
    @Test fun horizontalLeftAndRightSkipTracks() {
        val left = VinylGestureAccumulator(100f, 100f, 180f, 80f)
        left.move(125f, 80f)
        left.move(55f, 80f)
        assertEquals(VinylGestureAction.Next, left.finish())
        val right = VinylGestureAccumulator(100f, 100f, 20f, 80f)
        right.move(90f, 80f)
        right.move(180f, 80f)
        assertEquals(VinylGestureAction.Previous, right.finish())
    }

    @Test fun clockwiseQuarterTurnSeeksForwardFiveSeconds() {
        val g = VinylGestureAccumulator(100f, 100f, 200f, 100f)
        for (step in 1..12) {
            val angle = step * (Math.PI / 2) / 12
            g.move((100 + cos(angle) * 100).toFloat(), (100 + sin(angle) * 100).toFloat())
        }
        val action = g.finish() as VinylGestureAction.SeekBy
        assertTrue(kotlin.math.abs(action.milliseconds - 5_000L) <= 200L)
    }

    @Test fun counterclockwiseSeeksBackAndCenterJitterDoesNothing() {
        val g = VinylGestureAccumulator(100f, 100f, 200f, 100f)
        for (step in 1..12) {
            val angle = -step * (Math.PI / 2) / 12
            g.move((100 + cos(angle) * 100).toFloat(), (100 + sin(angle) * 100).toFloat())
        }
        val action = g.finish() as VinylGestureAction.SeekBy
        assertTrue(kotlin.math.abs(action.milliseconds + 5_000L) <= 200L)
        val jitter = VinylGestureAccumulator(100f, 100f, 100f, 100f)
        jitter.move(101f, 99f)
        assertEquals(VinylGestureAction.None, jitter.finish())
    }

    @Test fun straightSwipeAcrossDiscMustNotBeMisclassifiedAsRotation() {
        val g = VinylGestureAccumulator(100f, 100f, 180f, 55f)
        for (x in 160 downTo 20 step 20) g.move(x.toFloat(), 55f)
        assertEquals(VinylGestureAction.Next, g.finish())
    }
}
