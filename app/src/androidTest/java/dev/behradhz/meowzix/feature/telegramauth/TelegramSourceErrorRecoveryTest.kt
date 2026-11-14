package dev.behradhz.meowzix.feature.telegramauth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.behradhz.meowzix.domain.telegram.TelegramMusicSourceState
import dev.behradhz.meowzix.domain.telegram.TelegramSourceRetry
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TelegramSourceErrorRecoveryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chatLoadFailureOffersTheSafeRefreshAction() {
        var refreshes = 0
        var syncs = 0
        compose.setContent {
            MeowzixTheme {
                TelegramMusicSourcePicker(
                    state = TelegramMusicSourceState(
                        errorMessage = "Can't reach Telegram.",
                        retryAction = TelegramSourceRetry.REFRESH_CHATS,
                    ),
                    enabled = true,
                    onRefresh = { refreshes++ },
                    onSetMode = { _, _ -> },
                    onSync = { syncs++ },
                    onClearError = {},
                )
            }
        }
        compose.onNodeWithText("Try again").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            assertEquals(0, syncs)
        }
    }

    @Test fun syncFailureOffersRetrySyncNotUnrelatedChatRefresh() {
        var refreshes = 0
        var syncs = 0
        compose.setContent {
            MeowzixTheme {
                TelegramMusicSourcePicker(
                    state = TelegramMusicSourceState(
                        selectedChatIds = setOf(42L),
                        errorMessage = "Telegram could not finish syncing.",
                        retryAction = TelegramSourceRetry.SYNC_SELECTED,
                    ),
                    enabled = true,
                    onRefresh = { refreshes++ },
                    onSetMode = { _, _ -> },
                    onSync = { syncs++ },
                    onClearError = {},
                )
            }
        }
        compose.onNodeWithText("Try again").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(0, refreshes)
            assertEquals(1, syncs)
        }
    }
}
