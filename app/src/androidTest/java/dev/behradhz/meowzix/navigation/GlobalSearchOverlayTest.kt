package dev.behradhz.meowzix.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.data.repository.AlbumSummary
import dev.behradhz.meowzix.data.repository.ArtistSummary
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalSearchOverlayTest {
    @get:Rule val compose = createComposeRule()

    private val song = Track(
        id = UUID.randomUUID(),
        title = "Night Song",
        normalizedTitle = "night song",
        artist = "Night Artist",
        normalizedArtist = "night artist",
        album = "Night Album",
        durationMs = 125_000,
        trackNumber = null,
        year = null,
        artworkRef = null,
        favorite = false,
        hidden = false,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
    private val artist = ArtistSummary("Night Artist", "night artist", 3, null)
    private val album = AlbumSummary("Night Album", "Night Artist", "night artist", 2, null)

    private fun render(
        songs: List<Track>,
        onQueueArtist: () -> Unit = {},
        onNextAlbum: () -> Unit = {},
    ) {
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                DockSearchOverlay(
                    hazeState = rememberHazeState(),
                    scope = DockSearchScope.GLOBAL,
                    query = "night",
                    libraryResults = songs,
                    artistResults = listOf(artist),
                    albumResults = listOf(album),
                    playlistResults = emptyList(),
                    allPlaylists = emptyList(),
                    libraryAvailability = mapOf(song.id to LibraryTrackAvailability.CLOUD),
                    downloads = emptyMap(),
                    queueState = QueueState(),
                    currentTrackId = null,
                    onPlayLibraryTrack = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onPinOffline = {},
                    onRemoveOfflineCopy = {},
                    onRemoveTrack = { _, _, _ -> },
                    onFavorite = {},
                    onAddToPlaylist = { _, _ -> },
                    onGoToArtist = {},
                    onEnsureArtwork = {},
                    onTrackInteracted = {},
                    onPlayNextArtist = {},
                    onQueueArtist = { onQueueArtist() },
                    onPlayNextAlbum = { onNextAlbum() },
                    onQueueAlbum = {},
                    onOpenArtist = {},
                    onOpenAlbum = {},
                    onOpenPlaylist = {},
                    onPlayQueueIndex = {},
                )
            }
        }
    }

    @Test fun matchingSongsAreVisibleBeforeArtistsAndAlbums() {
        render(songs = listOf(song))
        compose.onNodeWithText("Songs").assertIsDisplayed()
        compose.onNodeWithText("Night Song").assertIsDisplayed()
    }

    @Test fun artistsExposeTheSharedQueueActions() {
        var queued = 0
        render(songs = emptyList(), onQueueArtist = { queued++ })
        compose.onAllNodesWithContentDescription("Queue actions")[0].performClick()
        compose.onNodeWithText("Add to queue").performClick()
        compose.runOnIdle { assertEquals(1, queued) }
    }

    @Test fun albumsExposePlayNextFromTheSameSharedCard() {
        var next = 0
        render(songs = emptyList(), onNextAlbum = { next++ })
        compose.onAllNodesWithContentDescription("Queue actions")[1].performClick()
        compose.onNodeWithText("Play next").performClick()
        compose.runOnIdle { assertEquals(1, next) }
    }
}
