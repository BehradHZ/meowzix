package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackSpeedGlassDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun centeredGlassDialogRevealsFineControlsOnlyForCustom() {
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlaybackSpeedGlassDialog(
                    currentSpeed = 1f,
                    onSpeedChange = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("playback-speed-dialog").assertIsDisplayed()
        compose.onNodeWithTag("playback-speed-done").assertDoesNotExist()
        compose.onNodeWithTag("speed-option-5").assertIsDisplayed()
        compose.onNodeWithTag("speed-step-plus").assertDoesNotExist()
        compose.onNodeWithTag("speed-option-5").performClick()
        compose.onNodeWithTag("custom-speed-gauge").assertIsDisplayed()
        compose.onNodeWithTag("speed-step-minus").assertIsDisplayed()
        compose.onNodeWithTag("speed-step-plus").assertIsDisplayed()
        compose.onNodeWithTag("speed-option-5").performClick()
        compose.onNodeWithTag("speed-step-plus").assertDoesNotExist()
    }

    @Test fun buttonsIncrementInFineStepsThroughFive() {
        var playedSpeed by mutableFloatStateOf(1f)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                PlaybackSpeedGlassDialog(
                    currentSpeed = playedSpeed,
                    onSpeedChange = { playedSpeed = it },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag("speed-option-5").performClick()
        compose.onNodeWithTag("speed-step-plus").performClick()
        compose.runOnIdle { assertEquals(1.1f, playedSpeed, 0.001f) }
        compose.onNodeWithTag("speed-step-minus").performClick()
        compose.runOnIdle { assertEquals(1f, playedSpeed, 0.001f) }

        compose.onNodeWithTag("speed-option-3").performClick()
        compose.runOnIdle { assertEquals(2f, playedSpeed, 0.001f) }
        compose.onNodeWithTag("speed-option-5").performClick()
        compose.onNodeWithTag("speed-step-plus").performClick()
        compose.runOnIdle { assertEquals(2.1f, playedSpeed, 0.001f) }
        compose.onNodeWithTag("speed-step-minus").performClick()
        compose.runOnIdle { assertEquals(2f, playedSpeed, 0.001f) }
        compose.onNodeWithTag("speed-step-minus").performClick()
        compose.runOnIdle { assertEquals(1.9f, playedSpeed, 0.001f) }
    }
}
