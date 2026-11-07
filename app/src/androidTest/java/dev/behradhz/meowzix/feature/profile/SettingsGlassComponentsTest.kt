package dev.behradhz.meowzix.feature.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import dev.behradhz.meowzix.feature.library.PlaylistGlassBackdrop
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.behradhz.meowzix.ui.components.MeowzixStickyHeader
import dev.behradhz.meowzix.ui.components.MeowzixHeaderAction
import org.junit.Assert.assertEquals
import kotlin.math.abs
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
    fun settingsBrandOpensAndClosesFullscreenViewer() {
        compose.setContent {
            var showBrand by remember { mutableStateOf(false) }
            MeowzixTheme {
                PlaylistGlassBackdrop { hazeState ->
                    SettingsGlassHero(
                        hazeState,
                        connected = false,
                        smartEnabled = true,
                        downloads = DownloadSettingsSummary(),
                        onOpenBrand = { showBrand = true },
                    )
                }
                if (showBrand) SettingsBrandDialog(onDismiss = { showBrand = false })
            }
        }

        compose.onNodeWithText("Meowzix").assertIsDisplayed()
        compose.onNodeWithContentDescription("Open Meowzix cat fullscreen").performClick()
        compose.onNodeWithContentDescription("Fullscreen Meowzix cat").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close fullscreen cat").performClick()
        compose.onNodeWithContentDescription("Fullscreen Meowzix cat").assertDoesNotExist()
    }

    @Test
    fun headerTitleIsVerticallyCenteredWithBackIconWhenSubtitleIsBlank() {
        compose.setContent {
            MeowzixTheme {
                PlaylistGlassBackdrop { hazeState ->
                    Box(Modifier.fillMaxWidth()) {
                        MeowzixStickyHeader(
                            title = "Settings",
                            subtitle = "",
                            hazeState = hazeState,
                            leadingActions = {
                                MeowzixHeaderAction(
                                    "Back",
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    onClick = {},
                                )
                            },
                        )
                    }
                }
            }
        }
        val title = compose.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Title and back icon should share a vertical centerline",
            abs(title.center.y - back.center.y) < 5f,
        )
    }

    @Test
    fun categoryIndexOpensOnlyTheSelectedSection() {
        var opened: SettingsCategory? = null
        compose.setContent {
            MeowzixTheme {
                PlaylistGlassBackdrop { hazeState ->
                    SettingsCategoryIndex(hazeState) { opened = it }
                }
            }
        }
        compose.onNodeWithText("Sound").performClick()
        compose.runOnIdle { assertEquals(SettingsCategory.SOUND, opened) }
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

        // Choices are always visible; no dropdown needs to be opened.
        compose.onNodeWithText("Dark").assertIsDisplayed()
        compose.onNodeWithText("Dark").performClick()
        compose.runOnIdle { assertEquals("DARK", selected) }
    }
}
