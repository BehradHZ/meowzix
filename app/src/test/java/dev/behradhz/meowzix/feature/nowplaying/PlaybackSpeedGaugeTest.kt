package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class PlaybackSpeedGaugeTest {
    private fun point(degrees: Double): Offset {
        val centerX = 100f
        val centerY = 200f * 0.54f
        val radius = 80f
        val angle = Math.toRadians(degrees)
        return Offset(centerX + radius * cos(angle).toFloat(),
            centerY + radius * sin(angle).toFloat())
    }

    @Test fun dialMapsBothEndpointsAndMidpointToExpectedSpeeds() {
        assertEquals(0.5f, gaugeSpeed(point(150.0), 200f, 200f), 0.001f)
        assertEquals(2.75f, gaugeSpeed(point(270.0), 200f, 200f), 0.001f)
        assertEquals(5f, gaugeSpeed(point(390.0), 200f, 200f), 0.001f)
    }

    @Test fun speedLabelsAreUnambiguous() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("0.5×", speedLabel(0.5f))
        assertEquals("2.25×", speedLabel(2.25f))
        assertEquals("5×", speedLabel(5f))
    }
}
