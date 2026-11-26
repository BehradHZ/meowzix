package dev.behradhz.meowzix.feature.telegram

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramForwardOptionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun senderCaptionPillsAndKeepToggleAreIndependent() {
        var sender by mutableStateOf(false)
        var caption by mutableStateOf(false)
        var remember by mutableStateOf(false)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                GlassOptionGroup(
                    includeSourceAttribution = sender,
                    keepCaption = caption,
                    rememberDefaults = remember,
                    onSourceAttributionChange = { sender = it; if (it) caption = true },
                    onKeepCaptionChange = { caption = it; if (!it) sender = false },
                    onRememberDefaultsChange = { remember = it },
                )
            }
        }
        compose.onNodeWithTag("telegram-option-sender").assertIsOff().performClick()
        compose.onNodeWithTag("telegram-option-sender").assertIsOn()
        compose.onNodeWithTag("telegram-option-caption").assertIsOn().performClick()
        compose.onNodeWithTag("telegram-option-sender").assertIsOff()
        compose.onNodeWithTag("telegram-option-caption").assertIsOff()
        compose.onNodeWithTag("telegram-option-remember").assertTextEquals("Keep").assertIsOff().performClick()
        compose.onNodeWithTag("telegram-option-remember").assertIsOn()
    }

    @Test fun timestampStartsOffAndTogglesIndependentlyOfRemember() {
        var timestampSelected by mutableStateOf(false)
        var remembered by mutableStateOf(false)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                GlassOptionGroup(
                    includeSourceAttribution = true,
                    keepCaption = true,
                    rememberDefaults = remembered,
                    onSourceAttributionChange = {},
                    onKeepCaptionChange = {},
                    onRememberDefaultsChange = { remembered = it },
                    timestampMs = 102345L,
                    timestampSelected = timestampSelected,
                    onTimestampSelected = { timestampSelected = it },
                )
            }
        }
        compose.onNodeWithTag("telegram-option-timestamp").assertIsOff().performClick()
        compose.onNodeWithTag("telegram-option-timestamp").assertIsOn()
        compose.onNodeWithTag("telegram-option-remember").assertIsOff().performClick()
        compose.onNodeWithTag("telegram-option-timestamp").assertIsOn().performClick()
        compose.onNodeWithTag("telegram-option-timestamp").assertIsOff()
        compose.onNodeWithTag("telegram-option-remember").assertIsOn()
    }

    @Test fun heldTimestampShowsLiveBubbleAndFreezesOnRelease() {
        var selected by mutableStateOf(false)
        var position by mutableStateOf(102000L)
        var live by mutableStateOf(110000L)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box(Modifier.padding(top = 72.dp)) {
                    GlassOptionGroup(
                        includeSourceAttribution = false,
                        keepCaption = false,
                        rememberDefaults = false,
                        onSourceAttributionChange = {},
                        onKeepCaptionChange = {},
                        onRememberDefaultsChange = {},
                        timestampMs = position,
                        timestampSelected = selected,
                        onTimestampSelected = { selected = it },
                        onTimestampPosition = { position = it },
                        liveTimestamp = { live },
                        reduceMotion = true,
                    )
                }
            }
        }
        compose.onNodeWithTag("telegram-option-timestamp").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(750)
        compose.waitUntil(3_000) { compose.onAllNodesWithTag("telegram-timestamp-bubble").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("telegram-timestamp-bubble").assertTextEquals("1:50")
        compose.runOnIdle { live = 112000L }
        compose.mainClock.advanceTimeBy(150)
        compose.waitUntil(3_000) { compose.onAllNodesWithText("1:52").fetchSemanticsNodes().size >= 2 }
        compose.onNodeWithTag("telegram-timestamp-bubble").assertTextEquals("1:52")
        compose.onNodeWithTag("telegram-option-timestamp").performTouchInput { up() }
        compose.onNodeWithTag("telegram-option-timestamp").assertIsOn().assertTextEquals("1:52")
        compose.onNodeWithTag("telegram-timestamp-bubble").assertDoesNotExist()
        compose.runOnIdle {
            live = 119000L
            assertEquals(112000L, position)
        }
    }

}
