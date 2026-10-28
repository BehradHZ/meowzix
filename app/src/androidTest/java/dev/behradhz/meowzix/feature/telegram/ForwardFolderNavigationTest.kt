package dev.behradhz.meowzix.feature.telegram

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.telegram.TelegramForwardFolder
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForwardFolderNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun telegramFoldersAppearWhenLateDataArrivesAndAreSelectable() {
        var folders by mutableStateOf(emptyList<TelegramForwardFolder>())
        var selected by mutableStateOf<Int?>(null)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                ForwardFolderNavigation(
                    folders = folders,
                    selectedFolderId = selected,
                    loading = folders.isEmpty(),
                    onSelectFolder = { selected = it },
                )
            }
        }

        compose.onNodeWithTag("telegram-forward-folders").assertExists()
        compose.onNodeWithText("Loading folders").assertExists()

        compose.runOnIdle {
            folders = listOf(TelegramForwardFolder(42, "Friends", emptyList()))
        }
        compose.onNodeWithText("Friends").assertExists().performClick()
        compose.runOnIdle { assertEquals(42, selected) }
        compose.onNodeWithText("All chats").performClick()
        compose.runOnIdle { assertEquals(null, selected) }
    }
}
