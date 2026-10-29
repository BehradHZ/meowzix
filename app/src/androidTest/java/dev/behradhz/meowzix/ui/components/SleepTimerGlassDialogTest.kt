package dev.behradhz.meowzix.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
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
}
