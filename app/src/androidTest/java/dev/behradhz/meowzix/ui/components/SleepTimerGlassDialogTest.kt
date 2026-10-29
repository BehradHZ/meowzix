package dev.behradhz.meowzix.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.SleepTimerMode
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import dev.behradhz.meowzix.domain.settings.SleepTimerPresetMode
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepTimerGlassDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glassDialogKeepsPresetsAndStatusAndDismiss() {
        var requestedMinutes by mutableIntStateOf(0)
        var enabledRequested by mutableStateOf(false)
        var dismissCount by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                SleepTimerGlassDialog(
                    state = SleepTimerState.Off,
                    endOfTrackEnabled = false,
                    lastMinutes = 30,
                    lastMode = SleepTimerPresetMode.DURATION,
                    onDismiss = { dismissCount++ },
                    onSetMinutes = { requestedMinutes = it },
                    onEndOfTrack = {},
                    onExtend = {},
                    onSetEnabled = { enabledRequested = it },
                )
            }
        }
        compose.onNodeWithTag("glass-sleep-timer-dialog").assertIsDisplayed()
        compose.onNodeWithText("Sleep timer").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-status").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-preset-15").performClick()
        compose.runOnIdle {
            assertEquals(15, requestedMinutes)
            assertEquals(false, enabledRequested)
        }
        compose.onNodeWithTag("sleep-timer-enabled").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-enabled").performClick()
        compose.runOnIdle { assertEquals(true, enabledRequested) }
        compose.onNodeWithTag("sleep-timer-close").performClick()
        compose.runOnIdle { assertEquals(1, dismissCount) }
    }
    @Test fun compactOptionsExpandOnlyForCustom() {
        var requestedMinutes by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                SleepTimerGlassDialog(
                    state = SleepTimerState.Off,
                    endOfTrackEnabled = true,
                    lastMinutes = 45,
                    lastMode = SleepTimerPresetMode.DURATION,
                    onDismiss = {},
                    onSetMinutes = { requestedMinutes = it },
                    onEndOfTrack = {},
                    onExtend = {},
                    onSetEnabled = {},
                )
            }
        }
        compose.onNodeWithTag("sleep-timer-end-of-track").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-preset-15").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-preset-60").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-option-custom").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-preset-30").assertDoesNotExist()
        compose.onNodeWithTag("sleep-timer-custom-slider").assertDoesNotExist()

        compose.onNodeWithTag("sleep-timer-option-custom").performClick()
        compose.onNodeWithTag("sleep-timer-custom-slider").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-custom-start").performClick()
        compose.runOnIdle { assertEquals(45, requestedMinutes) }

        compose.onNodeWithTag("sleep-timer-preset-60").performClick()
        compose.onNodeWithTag("sleep-timer-custom-slider").assertDoesNotExist()
        compose.runOnIdle { assertEquals(60, requestedMinutes) }

        compose.onNodeWithTag("sleep-timer-option-custom").performClick()
        compose.onNodeWithTag("sleep-timer-custom-slider").assertIsDisplayed()
        compose.onNodeWithTag("sleep-timer-option-custom").performClick()
        compose.onNodeWithTag("sleep-timer-custom-slider").assertDoesNotExist()
    }

    @Test fun endOfTrackOptionRequiresCurrentTrack() {
        var hasTrack by mutableStateOf(false)
        var endOfTrackRequests by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                SleepTimerGlassDialog(
                    state = SleepTimerState.Off,
                    endOfTrackEnabled = hasTrack,
                    lastMinutes = 15,
                    lastMode = SleepTimerPresetMode.END_OF_TRACK,
                    onDismiss = {},
                    onSetMinutes = {},
                    onEndOfTrack = { endOfTrackRequests++ },
                    onExtend = {},
                    onSetEnabled = {},
                )
            }
        }
        compose.onNodeWithTag("sleep-timer-end-of-track").assertIsNotEnabled()
        compose.runOnIdle { hasTrack = true }
        compose.onNodeWithTag("sleep-timer-end-of-track").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, endOfTrackRequests) }
    }

    @Test fun footerActionsAreRightAlignedAndClickable() {
        var extended by mutableIntStateOf(0)
        var dismissed by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                SleepTimerGlassDialog(
                    state = SleepTimerState(
                        mode = SleepTimerMode.DURATION,
                        remainingMs = 44 * 60_000L,
                    ),
                    endOfTrackEnabled = true,
                    lastMinutes = 45,
                    lastMode = SleepTimerPresetMode.DURATION,
                    onDismiss = { dismissed++ },
                    onSetMinutes = {},
                    onEndOfTrack = {},
                    onExtend = { extended++ },
                    onSetEnabled = {},
                )
            }
        }

        val actions = compose.onNodeWithTag("sleep-timer-actions").fetchSemanticsNode().boundsInRoot
        val extend = compose.onNodeWithTag("sleep-timer-extend").fetchSemanticsNode().boundsInRoot
        val done = compose.onNodeWithTag("sleep-timer-done").fetchSemanticsNode().boundsInRoot
        assertTrue("+15 must be immediately left of Done", extend.right < done.left)
        assertEquals("Done must align to the footer's right edge", actions.right, done.right, 2f)
        assertEquals("Actions must share the same bottom edge", extend.bottom, done.bottom, 2f)

        compose.onNodeWithTag("sleep-timer-extend").performClick()
        compose.runOnIdle {
            assertEquals(1, extended)
            assertEquals(0, dismissed)
        }
        compose.onNodeWithTag("sleep-timer-done").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    @Test fun extendIsHiddenOutsideDurationModeButDoneRemainsAvailable() {
        var timerMode by mutableStateOf(SleepTimerMode.OFF)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                SleepTimerGlassDialog(
                    state = SleepTimerState(mode = timerMode, remainingMs = 30 * 60_000L),
                    endOfTrackEnabled = true,
                    lastMinutes = 30,
                    lastMode = SleepTimerPresetMode.DURATION,
                    onDismiss = {},
                    onSetMinutes = {},
                    onEndOfTrack = {},
                    onExtend = {},
                    onSetEnabled = {},
                )
            }
        }
        compose.onNodeWithTag("sleep-timer-extend").assertDoesNotExist()
        compose.onNodeWithTag("sleep-timer-done").assertIsDisplayed()

        compose.runOnIdle { timerMode = SleepTimerMode.END_OF_TRACK }
        compose.onNodeWithTag("sleep-timer-extend").assertDoesNotExist()
        compose.runOnIdle { timerMode = SleepTimerMode.DURATION }
        compose.onNodeWithTag("sleep-timer-extend").assertIsDisplayed()
    }

}
