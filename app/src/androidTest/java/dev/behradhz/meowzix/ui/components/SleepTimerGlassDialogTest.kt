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
import dev.behradhz.meowzix.domain.playback.SleepTimerState
import dev.behradhz.meowzix.domain.settings.SleepTimerPresetMode
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertEquals
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

}
