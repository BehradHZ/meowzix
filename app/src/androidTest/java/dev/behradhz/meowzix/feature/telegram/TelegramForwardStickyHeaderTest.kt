package dev.behradhz.meowzix.feature.telegram

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelegramForwardStickyHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun sharingControlsRemainVisibleAfterDestinationListScrolls() {
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                ForwardPanelLayout(
                    modifier = Modifier.fillMaxWidth().height(560.dp),
                    header = {
                        Text("Send to Telegram", Modifier.testTag("test-forward-title"))
                        Text("Sender / Caption / Remember", Modifier.testTag("test-forward-options"))
                        Text("Search accounts and chats", Modifier.testTag("test-forward-search"))
                        Text("All chats / Personal", Modifier.testTag("test-forward-folders"))
                    },
                    listContent = {
                        items(50) { index ->
                            Text(
                                "Chat $index",
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                                    .testTag("test-forward-chat-$index"),
                            )
                        }
                    },
                )
            }
        }

        compose.onNodeWithTag("test-forward-chat-0").assertIsDisplayed()
        compose.onNodeWithTag("telegram-forward-list").performScrollToIndex(40)
        compose.onNodeWithTag("telegram-forward-header").assertIsDisplayed()
        compose.onNodeWithTag("test-forward-title").assertIsDisplayed()
        compose.onNodeWithTag("test-forward-options").assertIsDisplayed()
        compose.onNodeWithTag("test-forward-search").assertIsDisplayed()
        compose.onNodeWithTag("test-forward-folders").assertIsDisplayed()
        compose.onNodeWithTag("test-forward-chat-40").assertIsDisplayed()
    }
}
