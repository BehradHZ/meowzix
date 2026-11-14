package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StagedSwipeRevealClippingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun primaryAndRemoveNeverBleedThroughTheUncoveredAreaForEitherDirection() {
        var offset by mutableFloatStateOf(0f)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box(
                    Modifier
                        .size(320.dp, 80.dp)
                        .background(Color.Black)
                        .testTag("clipped-reveal"),
                ) {
                    StagedSwipeReveal(
                        offsetPx = offset,
                        primaryLabel = if (offset >= 0f) "Play next" else "Add to queue",
                        primaryIcon = Icons.Rounded.PlaylistPlay,
                        secondaryLabel = "Remove",
                        secondaryThresholdPx = with(LocalDensity.current) {
                            TRACK_SWIPE_REMOVE_THRESHOLD.toPx()
                        },
                    )
                }
            }
        }

        listOf(220f, -220f).forEach { swipe ->
            compose.runOnIdle { offset = swipe }
            compose.mainClock.advanceTimeBy(240L)
            compose.waitForIdle()

            val pixels = compose.onNodeWithTag("clipped-reveal").captureToImage().toPixelMap()
            val center = pixels[pixels.width / 2, pixels.height / 2]
            // This location is still beneath the translucent foreground.
            // Without the stationary gutter mask it would contain action color.
            assertTrue(
                "Action leaked under song card at swipe $swipe: $center",
                center.red + center.green + center.blue < 0.035f,
            )

            val exposedX = if (swipe > 0f) 12 else pixels.width - 13
            val exposed = pixels[exposedX, pixels.height / 2]
            assertTrue(
                "Swipe gutter was not painted for $swipe: $exposed",
                exposed.red + exposed.green + exposed.blue > 0.04f,
            )
        }
    }
}
