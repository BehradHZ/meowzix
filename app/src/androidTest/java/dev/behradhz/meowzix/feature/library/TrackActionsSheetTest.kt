package dev.behradhz.meowzix.feature.library

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackActionsMenuTest {
    @get:Rule val compose = createComposeRule()

    @Test fun libraryShowsFourQuickActionsAndOpensSecondPlaylistPanel() {
        val playlistId = UUID.randomUUID()
        var addedTo by mutableStateOf<UUID?>(null)
        var playNext by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                TrackActionsMenu(
                    expanded = true,
                    title = "Song", artist = "Artist", favorite = false, isQueue = false,
                    playlists = listOf(PlaylistSummary(id = playlistId, title = "Focus", trackCount = 3)),
                    onDismiss = {}, onPrimary = { playNext++ }, onAddToQueue = {},
                    onFavorite = {}, onForward = {}, onGoToArtist = {},
                    onAddToPlaylist = { addedTo = it }, onContinueVibe = {},
                    onEditMetadata = {}, onManageDuplicates = {}, onWhy = {},
                )
            }
        }
        compose.onNodeWithTag("glass-track-actions-menu").assertExists()
        compose.onNodeWithTag("track-action-play-next").assertExists()
        compose.onNodeWithTag("track-action-add-to-queue").assertExists()
        compose.onNodeWithTag("track-action-favorite").assertExists()
        compose.onNodeWithTag("track-action-forward").assertExists()
        compose.onNodeWithTag("track-action-row-go-to-artist").assertExists()
        compose.onNodeWithTag("track-action-row-view-in-chat").assertDoesNotExist()
        compose.onNodeWithTag("track-action-row-continue-the-vibe").assertExists()
        compose.onNodeWithTag("track-action-row-edit-metadata").assertExists()
        compose.onNodeWithTag("track-action-row-manage-duplicates").assertExists()
        compose.onNodeWithTag("track-action-row-why-this-song").assertExists()
        compose.onNodeWithTag("track-action-row-add-to-playlist").performClick()
        compose.onNodeWithTag("track-playlist-picker").assertExists()
        compose.onNodeWithText("Focus").performClick()
        compose.runOnIdle {
            assertEquals(playlistId, addedTo)
            assertEquals(0, playNext)
        }
    }

    @Test fun viewInChatAppearsOnlyForTracksWithLinksAndInvokesCallback() {
        var opened by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                TrackActionsMenu(
                    expanded = true,
                    title = "Telegram song", artist = "Artist", favorite = false, isQueue = false,
                    playlists = emptyList(), onDismiss = {}, onPrimary = {}, onAddToQueue = {},
                    onFavorite = {}, onForward = {}, onGoToArtist = {}, onAddToPlaylist = {},
                    onContinueVibe = {}, onEditMetadata = {}, onManageDuplicates = {}, onWhy = {},
                    onViewInChat = { opened++ },
                )
            }
        }
        compose.onNodeWithTag("track-action-row-view-in-chat").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    @Test fun queueReplacesPlayNextWithRemoveButKeepsOtherQuickActions() {
        var removed by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                TrackActionsMenu(
                    expanded = true,
                    title = "Queued song", artist = "Artist", favorite = true, isQueue = true,
                    playlists = emptyList(), onDismiss = {},
                    onPrimary = { removed++ }, onAddToQueue = {},
                    onFavorite = {}, onForward = {}, onGoToArtist = {},
                    onAddToPlaylist = {}, onContinueVibe = {},
                    onEditMetadata = {}, onManageDuplicates = {}, onWhy = {},
                )
            }
        }
        compose.onNodeWithTag("queue-track-actions").assertExists()
        compose.onNodeWithTag("track-action-play-next").assertDoesNotExist()
        compose.onNodeWithTag("track-action-remove-from-queue").performClick()
        compose.runOnIdle { assertEquals(1, removed) }
    }

    @Test fun opensAtOverflowAnchorAndClosesAfterAction() {
        var expanded by mutableStateOf(false)
        var selected by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                Box {
                    Button(
                        onClick = { expanded = true },
                        modifier = Modifier.testTag("track-actions-anchor"),
                    ) { Text("More") }
                    TrackActionsMenu(
                        expanded = expanded,
                        title = "Anchor song", artist = "Artist", favorite = false, isQueue = false,
                        playlists = emptyList(),
                        onDismiss = { expanded = false },
                        onPrimary = { selected++ }, onAddToQueue = {},
                        onFavorite = {}, onForward = {}, onGoToArtist = {},
                        onAddToPlaylist = {}, onContinueVibe = {},
                        onEditMetadata = {}, onManageDuplicates = {}, onWhy = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("glass-track-actions-menu").assertDoesNotExist()
        compose.onNodeWithTag("track-actions-anchor").performClick()
        compose.onNodeWithTag("glass-track-actions-menu").assertExists()
        compose.onNodeWithTag("track-action-play-next").performClick()
        compose.onNodeWithTag("glass-track-actions-menu").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, selected) }
    }
}
