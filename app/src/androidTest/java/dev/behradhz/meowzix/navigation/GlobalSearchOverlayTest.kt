package dev.behradhz.meowzix.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
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
import org.junit.Assert.assertTrue
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
        onNextSong: () -> Unit = {},
        onQueueSong: () -> Unit = {},
        includeCollections: Boolean = true,
        queryState: MutableState<String>? = null,
        listState: LazyListState = LazyListState(),
    ) {
        compose.setContent {
            MeowzixTheme(darkTheme = true) {
                DockSearchOverlay(
                    hazeState = rememberHazeState(),
                    scope = DockSearchScope.GLOBAL,
                    query = queryState?.value ?: "night",
                    libraryResults = songs,
                    artistResults = if (includeCollections) listOf(artist) else emptyList(),
                    albumResults = if (includeCollections) listOf(album) else emptyList(),
                    playlistResults = emptyList(),
                    allPlaylists = emptyList(),
                    libraryAvailability = mapOf(song.id to LibraryTrackAvailability.CLOUD),
                    downloads = emptyMap(),
                    queueState = QueueState(),
                    currentTrackId = null,
                    onPlayLibraryTrack = {},
                    onPlayNext = { onNextSong() },
                    onAddToQueue = { onQueueSong() },
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
                    listState = listState,
                )
            }
        }
    }

    @Test fun matchingSongsAreVisibleBeforeArtistsAndAlbums() {
        render(songs = listOf(song))
        compose.onNodeWithText("Songs").assertIsDisplayed()
        compose.onNodeWithText("Night Song").assertIsDisplayed()
        val songsTop = compose.onNodeWithText("Songs").getUnclippedBoundsInRoot().top
        val artistsTop = compose.onNodeWithText("Artists").getUnclippedBoundsInRoot().top
        val albumsTop = compose.onNodeWithText("Albums").getUnclippedBoundsInRoot().top
        assertTrue(songsTop < artistsTop)
        assertTrue(artistsTop < albumsTop)
    }

    @Test fun changingQueryReturnsThePanelToSongsAtTheTop() {
        val query = mutableStateOf("night")
        val listState = LazyListState()
        val manySongs = List(20) { index ->
            song.copy(
                id = UUID.randomUUID(),
                title = "Night Song $index",
                normalizedTitle = "night song $index",
            )
        }
        render(songs = manySongs, queryState = query, listState = listState)

        compose.onNodeWithTag("global-search-panel").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue("The test must scroll beyond the Songs section", listState.firstVisibleItemIndex > 0)

        compose.runOnIdle { query.value = "night changed" }
        compose.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        compose.onNodeWithText("Songs").assertIsDisplayed()
    }

    @Test fun matchingSongsAppearEvenWithoutArtistOrAlbumMatches() {
        render(songs = listOf(song), includeCollections = false)
        compose.onNodeWithText("Songs").assertIsDisplayed()
        compose.onNodeWithText("Night Song").assertIsDisplayed()
    }

    @Test fun songCardsAreInsetInsideTheGlassSearchPanel() {
        render(songs = listOf(song), includeCollections = false)
        val panel = compose.onNodeWithTag("global-search-panel").getUnclippedBoundsInRoot()
        val songRow = compose.onNodeWithTag("global-search-song:${song.id}").getUnclippedBoundsInRoot()
        assertTrue(songRow.left > panel.left)
        assertTrue(songRow.right < panel.right)
    }

    @Test fun songCardsSupportBothSharedSwipeActions() {
        var next = 0
        var queued = 0
        render(
            songs = listOf(song),
            includeCollections = false,
            onNextSong = { next++ },
            onQueueSong = { queued++ },
        )

        val songCard = compose.onNodeWithTag("global-search-song:${song.id}")
        songCard.assertIsDisplayed()
        songCard.performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals(1, next) }

        songCard.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, queued) }
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
