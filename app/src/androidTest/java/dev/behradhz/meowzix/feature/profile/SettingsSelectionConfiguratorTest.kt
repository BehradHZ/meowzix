package dev.behradhz.meowzix.feature.profile

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction
import dev.behradhz.meowzix.domain.settings.LibrarySelectionSettings
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SettingsSelectionConfiguratorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dockSlotIsReplacedFromHorizontalIconPicker() {
        var selection by mutableStateOf(LibrarySelectionSettings())
        compose.setContent {
            MeowzixTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsSelectionConfigurator(
                    settings = selection,
                    reduceMotion = true,
                    onDockSlot = { index, action -> selection = selection.setDockSlot(index, action) },
                    onMoreEnabled = { action, enabled -> selection = selection.setMoreEnabled(action, enabled) },
                    onMoreMove = { action, index -> selection = selection.moveMoreAction(action, index) },
                    )
                }
            }
        }
        compose.onNodeWithTag("selection-settings-dock").assertExists()
        compose.onNodeWithTag("selection-settings-slot-2").performClick()
        compose.onNodeWithTag("selection-settings-option-download").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(LibrarySelectionAction.DOWNLOAD, selection.dock[2])
            assertFalse(LibrarySelectionAction.DOWNLOAD in selection.more)
        }
    }

    @Test fun selectionMenuPreviewExposesPersistentToggles() {
        var selection by mutableStateOf(LibrarySelectionSettings())
        compose.setContent {
            MeowzixTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsSelectionConfigurator(
                    settings = selection,
                    reduceMotion = true,
                    onDockSlot = { index, action -> selection = selection.setDockSlot(index, action) },
                    onMoreEnabled = { action, enabled -> selection = selection.setMoreEnabled(action, enabled) },
                    onMoreMove = { action, index -> selection = selection.moveMoreAction(action, index) },
                    )
                }
            }
        }
        compose.onNodeWithTag("selection-settings-more-preview").assertExists()
        compose.onNodeWithTag("selection-settings-more-toggle-like").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(LibrarySelectionAction.LIKE in selection.more) }
        compose.onNodeWithTag("selection-settings-more-handle-like").assertExists()
    }
}
