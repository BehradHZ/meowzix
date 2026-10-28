package dev.behradhz.meowzix.feature.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsGlassComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun toggleRowChangesValueThroughTheWholeRow() {
        var updated = false
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            MeowzixTheme {
                SettingsToggleRow(
                    title = "Reduced motion",
                    subtitle = "Shorter animations",
                    icon = Icons.Rounded.Animation,
                    checked = checked,
                    onChecked = { checked = it; updated = it },
                )
            }
        }

        compose.onNodeWithText("Reduced motion").performClick()
        compose.runOnIdle { assertTrue(updated) }
    }

    @Test
    fun tappingProfileCatOpensAndClosesFullscreenViewer() {
        compose.setContent {
            MeowzixTheme {
                ProfileRoute(onOpenOffline = {}, onOpenHistory = {}, onOpenTelegram = {})
            }
        }

        compose.onNodeWithContentDescription("Open Meowzix cat fullscreen").performClick()
        compose.onNodeWithContentDescription("Fullscreen Meowzix cat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close fullscreen cat").performClick()
        compose.onNodeWithContentDescription("Fullscreen Meowzix cat").assertDoesNotExist()
    }

    @Test
    fun choiceRowShowsOptionsAndReturnsSelectedValue() {
        var selected = ""
        compose.setContent {
            MeowzixTheme {
                SettingsChoiceRow(
                    title = "Theme",
                    subtitle = null,
                    icon = Icons.Rounded.Palette,
                    current = "Light",
                    options = listOf("LIGHT", "DARK"),
                    onChoice = { selected = it },
                )
            }
        }

        compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals("DARK", selected) }
    }
}
