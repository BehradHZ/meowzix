package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PlaybackSpeedGaugeTest {
    private fun point(degrees: Double): Offset {
        val centerX = 100f
        val centerY = 200f * 0.54f
        val radius = 80f
        val angle = Math.toRadians(degrees)
        return Offset(
            centerX + radius * cos(angle).toFloat(),
            centerY + radius * sin(angle).toFloat(),
        )
    }

    @Test fun dialMapsEndpointsAndFineCoarseBoundaryToSupportedSpeeds() {
        assertEquals(0.5f, gaugeSpeed(point(150.0), 200f, 200f), 0.001f)
        assertEquals(2.0f, gaugeSpeed(point(294.0), 200f, 200f), 0.001f)
        assertEquals(3.5f, gaugeSpeed(point(342.0), 200f, 200f), 0.001f)
        assertEquals(5f, gaugeSpeed(point(390.0), 200f, 200f), 0.001f)
    }

    @Test fun fineStepsCoverTheWholeRangeThroughFive() {
        assertEquals(0.6f, stepPlaybackSpeed(0.5f, 1), 0.001f)
        assertEquals(1.9f, stepPlaybackSpeed(2f, -1), 0.001f)
        assertEquals(2f, stepPlaybackSpeed(1.9f, 1), 0.001f)
        assertEquals(2.1f, stepPlaybackSpeed(2f, 1), 0.001f)
        assertEquals(2.4f, stepPlaybackSpeed(2.5f, -1), 0.001f)
        assertEquals(2.6f, stepPlaybackSpeed(2.5f, 1), 0.001f)
        assertEquals(3.4f, stepPlaybackSpeed(3.3f, 1), 0.001f)
        assertEquals(4.9f, stepPlaybackSpeed(5f, -1), 0.001f)
    }

    @Test fun steppingClampsAtLimitsAndSnapsLegacyFractionalSpeeds() {
        assertEquals(0.5f, stepPlaybackSpeed(0.5f, -1), 0.001f)
        assertEquals(5f, stepPlaybackSpeed(5f, 1), 0.001f)
        assertEquals(1.3f, stepPlaybackSpeed(1.25f, 1), 0.001f)
        assertEquals(1.2f, stepPlaybackSpeed(1.25f, -1), 0.001f)
        assertEquals(2.3f, stepPlaybackSpeed(2.25f, 1), 0.001f)
        assertEquals(2.2f, stepPlaybackSpeed(2.25f, -1), 0.001f)
        assertThrows(IllegalArgumentException::class.java) { stepPlaybackSpeed(1f, 0) }
    }

    @Test fun dialOnlyTicksAcrossHalfSpeedBoundaries() {
        assertEquals(false, crossesHalfSpeedMark(2.3f, 2.4f))
        assertEquals(true, crossesHalfSpeedMark(2.4f, 2.5f))
        assertEquals(false, crossesHalfSpeedMark(2.5f, 2.4f))
        assertEquals(true, crossesHalfSpeedMark(2.6f, 2.5f))
        assertEquals(false, crossesHalfSpeedMark(2.5f, 2.6f))
        assertEquals(false, crossesHalfSpeedMark(2.4f, 2.3f))
        assertEquals(true, crossesHalfSpeedMark(3.1f, 3.0f))
        assertEquals(false, crossesHalfSpeedMark(3.0f, 2.9f))
        assertEquals(true, crossesHalfSpeedMark(2.8f, 2.1f))
        assertEquals(true, crossesHalfSpeedMark(2.1f, 2.8f))
        assertEquals(false, crossesHalfSpeedMark(3.7f, 3.8f))
        assertEquals(true, crossesHalfSpeedMark(3.9f, 4.0f))
    }

    @Test fun dialCanSelectThreePointFour() {
        assertEquals(3.4f, gaugeSpeed(point(338.8), 200f, 200f), 0.001f)
    }

    @Test fun speedLabelsAreUnambiguous() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("0.5×", speedLabel(0.5f))
        assertEquals("1.1×", speedLabel(1.1f))
        assertEquals("2.25×", speedLabel(2.25f))
        assertEquals("5×", speedLabel(5f))
    }
}
