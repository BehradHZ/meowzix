package dev.behradhz.meowzix.navigation

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiniPlayerStackLayerTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun nextSlidesForegroundInFromRightWithoutMovingOrResizingUnderlay() {
        verify(
            direction = MiniCardDirection.NEXT,
            startingColors = listOf(AndroidColor.GREEN, AndroidColor.RED),
            endingColors = listOf(AndroidColor.YELLOW, AndroidColor.BLUE),
        )
    }

    @Test
    fun previousSlidesCurrentRightAndUncoversStationaryPreviousTrack() {
        verify(
            direction = MiniCardDirection.PREVIOUS,
            startingColors = listOf(AndroidColor.YELLOW, AndroidColor.BLUE),
            endingColors = listOf(AndroidColor.GREEN, AndroidColor.RED),
        )
    }

    private fun verify(
        direction: MiniCardDirection,
        startingColors: List<Int>,
        endingColors: List<Int>,
    ) {
        var progress by mutableFloatStateOf(0f)
        compose.setContent {
            MiniPlayerStackLayer(
                direction = direction,
                progress = progress,
                modifier = Modifier.width(200.dp).height(72.dp).testTag("mini-player-stack"),
                underlay = {
                    Box(Modifier.fillMaxSize().background(Color.Red)) {
                        Box(Modifier.width(20.dp).fillMaxHeight().align(Alignment.CenterStart).background(Color.Green))
                    }
                },
                foreground = {
                    Box(Modifier.fillMaxSize().background(Color.Blue)) {
                        Box(Modifier.width(20.dp).fillMaxHeight().align(Alignment.CenterStart).background(Color.Yellow))
                    }
                },
            )
        }

        assertPixels(5 to startingColors[0], 80 to startingColors[1])
        compose.runOnIdle { progress = 0.5f }
        // A stationary background marker stays at 5%, while the moving foreground
        // marker arrives at 50%-60%, proving translation rather than content shrinkage.
        assertPixels(5 to AndroidColor.GREEN, 55 to AndroidColor.YELLOW, 80 to AndroidColor.BLUE)
        assertCardsHaveGapAndClippedCorners()
        compose.runOnIdle { progress = 1f }
        assertPixels(5 to endingColors[0], 80 to endingColors[1])
    }

    private fun assertCardsHaveGapAndClippedCorners() {
        val bitmap = compose.onNodeWithTag("mini-player-stack").captureToImage().asAndroidBitmap()
        val gap = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        // At 50% progress, an 8.dp gap separates the two cards; neither card
        // paints the center pixel, even though both use fully opaque test colors.
        assertNotEquals("Outgoing card leaks into gap", AndroidColor.RED, gap)
        assertNotEquals("Incoming card overlaps gap", AndroidColor.BLUE, gap)
        val roundedCorner = bitmap.getPixel(0, 0)
        assertNotEquals("Square outgoing corner", AndroidColor.RED, roundedCorner)
        assertNotEquals("Square incoming corner", AndroidColor.BLUE, roundedCorner)
    }

    private fun assertPixels(vararg probes: Pair<Int, Int>) {
        val bitmap = compose.onNodeWithTag("mini-player-stack").captureToImage().asAndroidBitmap()
        for ((xPercent, expectedColor) in probes) {
            assertEquals(
                "Incorrect stack layer at $xPercent%",
                expectedColor,
                bitmap.getPixel(bitmap.width * xPercent / 100, bitmap.height / 2),
            )
        }
    }
}
