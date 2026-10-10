package dev.behradhz.meowzix.feature.telegram

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramForwardOptionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun senderCaptionRememberAreIndependentOneRowTogglePills() {
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
        compose.onNodeWithTag("telegram-option-remember").assertIsOff().performClick()
        compose.onNodeWithTag("telegram-option-remember").assertIsOn()
    }
}
