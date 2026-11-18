package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
class CollectionSelectionControlTest {
    @get:Rule val compose = createComposeRule()

    @Test fun artistCheckboxAppearsOnlyDuringSelectionAndOpensDetailsSeparately() {
        var selecting by mutableStateOf(false)
        var selectedCount by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box(Modifier) {
                    SwipeableCollectionRow(
                        title = "Sample artist",
                        subtitle = "3 tracks",
                        icon = Icons.Rounded.Person,
                        onClick = {},
                        onPlayNext = {},
                        onAddToQueue = {},
                        onSelect = { selectedCount++ },
                        selectionMode = selecting,
                    )
                }
            }
        }
        compose.onNodeWithTag("collection-selection-checkbox").assertDoesNotExist()
        compose.runOnIdle { selecting = true }
        compose.waitForIdle()
        compose.onNodeWithTag("collection-selection-checkbox").assertExists().performClick()
        compose.runOnIdle { assertEquals(1, selectedCount) }
        compose.runOnIdle { selecting = false }
        compose.waitForIdle()
        compose.onNodeWithTag("collection-selection-checkbox").assertDoesNotExist()
    }
}
