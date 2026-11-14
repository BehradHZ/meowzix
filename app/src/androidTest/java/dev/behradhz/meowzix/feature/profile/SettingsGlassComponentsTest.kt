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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import dev.behradhz.meowzix.feature.library.PlaylistGlassBackdrop
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.domain.settings.LauncherIconColorSource
import dev.behradhz.meowzix.ui.theme.LauncherIconPalette
import dev.behradhz.meowzix.ui.theme.LauncherIconVariant
import androidx.test.core.app.ApplicationProvider
import android.content.Context
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
    fun headerTitleWithSubtitleRemainsAlignedWithActionIcon() {
        compose.setContent {
            MeowzixTheme {
                PlaylistGlassBackdrop { hazeState ->
                    Box(Modifier.fillMaxWidth()) {
                        MeowzixStickyHeader(
                            title = "Library",
                            subtitle = "Your music",
                            hazeState = hazeState,
                            actions = {
                                MeowzixHeaderAction(
                                    "Action", Icons.Rounded.Palette, onClick = {},
                                )
                            },
                        )
                    }
                }
            }
        }
        val title = compose.onNodeWithText("Library").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithContentDescription("Action").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(title.center.y - action.center.y) < 5f)
        compose.onNodeWithText("Your music").assertIsDisplayed()
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
    fun settingsIndexExposesTelegramAndDownloadsWithoutAnIntermediateCategory() {
        val opened = mutableListOf<SettingsCategory>()
        compose.setContent {
            MeowzixTheme {
                PlaylistGlassBackdrop { haze ->
                    SettingsCategoryIndex(haze) { opened += it }
                }
            }
        }
        compose.onNodeWithTag("settings-category-telegram").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-category-downloads").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(listOf(SettingsCategory.TELEGRAM, SettingsCategory.DOWNLOADS), opened)
            assertTrue(SettingsCategory.TELEGRAM.isDirectDestination())
            assertTrue(SettingsCategory.DOWNLOADS.isDirectDestination())
            assertTrue(!SettingsCategory.DATA.isDirectDestination())
            assertTrue(SettingsCategory.entries.none { it.name == "CONNECTIONS" })
        }
    }

    @Test
    fun launcherShowsPackagedOldAppAndWallpaperIconsAndSelectionIsExplicit() {
        var requested: LauncherIconColorSource? = null
        compose.setContent {
            MeowzixTheme {
                LauncherIconPreviews(
                    AppearanceSettings(colorTheme = ColorThemePreference.OCEAN),
                ) { requested = it }
            }
        }
        compose.onNodeWithTag("launcher-icon-old").assertIsDisplayed()
        compose.onNodeWithTag("launcher-icon-new").assertIsDisplayed()
        compose.onNodeWithTag("launcher-icon-wallpaper").assertIsDisplayed()
        compose.onNodeWithTag("launcher-icon-new").performClick()
        compose.runOnIdle { assertEquals(LauncherIconColorSource.APP_THEME, requested) }
    }

    @Test
    fun lightLauncherPreviewColorMatchesPackagedIconBackgroundExactly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ocean = LauncherIconVariant(LauncherIconPalette.OCEAN, false)
        val coral = LauncherIconVariant(LauncherIconPalette.CORAL, false)
        assertEquals(0xFF187CB9.toInt(), context.getColor(launcherBackgroundResource(ocean)))
        assertEquals(0xFFE45A5C.toInt(), context.getColor(launcherBackgroundResource(coral)))
    }

    @Test
    fun playerExampleUsesSelectedLyricScaleWithoutChangingPlayback() {
        var appearance by mutableStateOf(AppearanceSettings(lyricsTextScalePercent = 80))
        compose.setContent {
            MeowzixTheme {
                SettingsPlayerPreview(appearance)
            }
        }
        compose.onNodeWithTag("settings-flower-cover").assertIsDisplayed()
        compose.onNodeWithText("Every color feels like home").assertIsDisplayed()
        val small = compose.onNodeWithText("Every color feels like home")
            .fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle {
            appearance = appearance.copy(lyricsTextScalePercent = 160)
        }
        val large = compose.onNodeWithText("Every color feels like home")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("Preview lyric must visibly grow with the preference", large > small)
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
