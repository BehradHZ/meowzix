package dev.behradhz.meowzix.feature.library

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
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
class TrackActionsSheetTest {
    @get:Rule val compose = createComposeRule()

    @Test fun libraryShowsFourQuickActionsAndOpensSecondPlaylistPanel() {
        val playlistId = UUID.randomUUID()
        var addedTo by mutableStateOf<UUID?>(null)
        var playNext by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                TrackActionsSheet(
                    title = "Song", artist = "Artist", favorite = false, isQueue = false,
                    playlists = listOf(PlaylistSummary(id = playlistId, title = "Focus", trackCount = 3)),
                    onDismiss = {}, onPrimary = { playNext++ }, onAddToQueue = {},
                    onFavorite = {}, onForward = {}, onGoToArtist = {},
                    onAddToPlaylist = { addedTo = it }, onContinueVibe = {},
                    onEditMetadata = {}, onManageDuplicates = {}, onWhy = {},
                )
            }
        }
        compose.onNodeWithTag("glass-track-actions-sheet").assertExists()
        compose.onNodeWithTag("track-action-play-next").assertExists()
        compose.onNodeWithTag("track-action-add-to-queue").assertExists()
        compose.onNodeWithTag("track-action-favorite").assertExists()
        compose.onNodeWithTag("track-action-forward").assertExists()
        compose.onNodeWithTag("track-action-row-go-to-artist").assertExists()
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

    @Test fun queueReplacesPlayNextWithRemoveButKeepsOtherQuickActions() {
        var removed by mutableIntStateOf(0)
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                TrackActionsSheet(
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
}
