package dev.behradhz.meowzix.feature.library

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import java.io.File
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistExperienceTest {
    @get:Rule val compose = createComposeRule()

    private val tracks = listOf(track("Zulu", "Artist B"), track("Alpha", "Artist A"), track("Beta", "Artist A"))
    private val playlist = PlaylistSummary(UUID.randomUUID(), "Night Drive", 3, "The soundtrack for the way home.", updatedAt = Instant.parse("2026-10-03T00:00:00Z"))

    private class Calls {
        val plays = mutableListOf<Pair<List<UUID>, PlaybackMode>>()
        val moves = mutableListOf<Pair<Int, Int>>()
        var saved: Triple<String, String?, String?>? = null
        var next = 0
        var queue = 0
        var downloads = 0
        var favorites = 0
        var removed: UUID? = null
        var deleted = 0
        var artist: UUID? = null
        var added: Pair<UUID, UUID>? = null
    }

    private fun showDetail(
        calls: Calls = Calls(),
        songs: List<Track> = tracks,
        editable: Boolean = true,
        canEditMetadata: Boolean = editable,
        favorites: Boolean = false,
        dark: Boolean = true,
        artworkRef: String? = null,
        fontScale: Float = 1f,
    ): Calls {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MeowzixTheme(darkTheme = dark) {
                    PlaylistDetailScreenV3(
                        playlist = if (favorites) null else playlist.copy(artworkRef = artworkRef),
                        title = if (favorites) "Favorites" else playlist.title,
                        tracks = songs,
                        artworkRef = artworkRef,
                        editable = editable,
                        canEditMetadata = canEditMetadata,
                        isFavorites = favorites,
                        currentTrackId = songs.firstOrNull()?.id,
                        availability = songs.associate { it.id to LibraryTrackAvailability.CLOUD },
                        downloads = emptyMap(),
                        playlists = listOf(playlist),
                        onBack = {},
                        onSaveMetadata = { title, description, artwork -> calls.saved = Triple(title, description, artwork) },
                        onPlay = { queue, mode -> calls.plays.add(queue.map(Track::id) to mode) },
                        onPlayTrack = { song, queue -> calls.plays.add(queue.map(Track::id) to PlaybackMode.ORDERED) },
                        onPlayNext = { calls.next++ },
                        onAddToQueue = { calls.queue++ },
                        onPinOffline = { calls.downloads++ },
                        onFavorite = { calls.favorites++ },
                        onAddToPlaylist = { song, id -> calls.added = song.id to id },
                        onGoToArtist = { calls.artist = it.id },
                        onMove = { from, to -> calls.moves.add(from to to) },
                        onRemove = { calls.removed = it.id },
                        onEnsureArtwork = {},
                        onDelete = { calls.deleted++ },
                    )
                }
            }
        }
        return calls
    }

    private fun scrollToText(text: String) {
        compose.onNodeWithTag("playlist-detail-list").performScrollToNode(hasText(text))
    }

    private fun firstTrackMenu() {
        val tag = "playlist-track:${tracks.first().id}"
        compose.onNodeWithTag("playlist-detail-list").performScrollToNode(hasTestTag(tag))
        compose.onNode(hasContentDescription("Track actions") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performClick()
    }

    @Test fun playbackModesRemainSeparateAndRespectDisplayedOrder() {
        val calls = showDetail()
        listOf("Play", "Shuffle", "Smart").forEach { label ->
            scrollToText(label)
            compose.onNodeWithText(label).performClick()
        }
        compose.runOnIdle {
            assertEquals(listOf(PlaybackMode.ORDERED, PlaybackMode.PURE_SHUFFLE, PlaybackMode.SMART_SHUFFLE), calls.plays.map { it.second })
            assertEquals(tracks.map(Track::id), calls.plays.first().first)
        }
        scrollToText("Sort · Custom")
        compose.onNodeWithText("Sort · Custom").performClick()
        compose.onNodeWithText("Name").performClick()
        scrollToText("Play")
        compose.onNodeWithText("Play").performClick()
        compose.runOnIdle { assertEquals(tracks.sortedBy(Track::title).map(Track::id), calls.plays.last().first) }
        scrollToText("Sort · Name")
        compose.onNodeWithContentDescription("Ascending order").performClick()
        scrollToText("Play")
        compose.onNodeWithText("Play").performClick()
        compose.runOnIdle { assertEquals(tracks.sortedByDescending(Track::title).map(Track::id), calls.plays.last().first) }
    }

    @Test fun groupingPreservesSongsAndCustomRestoresManualOrder() {
        showDetail()
        scrollToText("Group by · None")
        compose.onNodeWithText("Group by · None").performClick()
        compose.onNodeWithText("Artist", substring = false).performClick()
        scrollToText("Artist A")
        compose.onNodeWithText("Artist A", substring = false).assertIsDisplayed()
        scrollToText("Sort · Name")
        compose.onNodeWithText("Sort · Name").performClick()
        compose.onNodeWithText("Custom").performClick()
        compose.onNodeWithText("Group by · None").assertIsDisplayed()
    }

    @Test fun editingValidatesNameAndSavesTrimmedMetadata() {
        val calls = showDetail()
        compose.onNodeWithContentDescription("Edit playlist").performClick()
        compose.onNodeWithTag("playlist-edit-list").performScrollToNode(hasText("Playlist name"))
        compose.onNodeWithText("Playlist name").performTextReplacement("   ")
        compose.onNodeWithContentDescription("Save playlist").assertIsNotEnabled()
        compose.onNodeWithText("Playlist name").performTextReplacement("  Evening favorites  ")
        compose.onNodeWithText("Description").performTextReplacement("  A calmer mix.  ")
        // Save is in the fixed header, not inside the scrollable edit list.
        compose.onNodeWithContentDescription("Save playlist").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(Triple("Evening favorites", "A calmer mix.", null), calls.saved) }
    }

    @Test fun removingCustomCoverIsIncludedInSavedMetadata() {
        val calls = showDetail(artworkRef = "content://test/playlist-cover")
        compose.onNodeWithContentDescription("Edit playlist").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithContentDescription("Save playlist").performClick()
        compose.runOnIdle { assertEquals(null, calls.saved?.third) }
    }

    @Test fun deletingPlaylistRequiresExplicitConfirmation() {
        val calls = showDetail()
        compose.onNodeWithContentDescription("Delete playlist").performClick()
        compose.onNodeWithText("Delete playlist?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls.deleted) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, calls.deleted) }
        compose.onNodeWithContentDescription("Delete playlist").performClick()
        compose.onNodeWithText("Delete playlist", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, calls.deleted) }
    }

    @Test fun managedPlaylistsKeepPlaybackAndTrackActionsWithoutMembershipEditing() {
        val calls = showDetail(editable = false, canEditMetadata = true)
        compose.onNodeWithContentDescription("Delete playlist").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit playlist").assertIsDisplayed()
        firstTrackMenu()
        compose.onNodeWithText("Remove from playlist").assertDoesNotExist()
        compose.onNodeWithContentDescription("Favorite").performClick()
        compose.runOnIdle { assertEquals(1, calls.favorites) }
    }

    @Test fun managedPlaylistMetadataCanBeEditedWithoutDeletingTheSource() {
        val calls = showDetail(editable = false, canEditMetadata = true)
        compose.onNodeWithContentDescription("Edit playlist").performClick()
        compose.onNodeWithTag("playlist-edit-list").performScrollToNode(hasText("Playlist name"))
        compose.onNodeWithText("Playlist name").performTextReplacement("Music archive")
        compose.onNodeWithContentDescription("Save playlist").performClick()
        compose.runOnIdle { assertEquals("Music archive", calls.saved?.first) }
        compose.onNodeWithContentDescription("Delete playlist").assertDoesNotExist()
    }

    @Test fun swipesDownloadRemovalAndAccessibleReorderStillWork() {
        val calls = showDetail()
        val tag = "playlist-track:${tracks.first().id}"
        compose.onNodeWithTag("playlist-detail-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performTouchInput { swipeRight() }
        compose.onNodeWithTag(tag).performTouchInput { swipeLeft() }
        compose.onNode(hasContentDescription("Download") and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).performClick()
        firstTrackMenu()
        compose.onNodeWithText("Move down in playlist").performClick()
        firstTrackMenu()
        compose.onNodeWithText("Remove from playlist").performClick()
        compose.runOnIdle {
            assertEquals(1, calls.next)
            assertEquals(1, calls.queue)
            assertEquals(1, calls.downloads)
            assertEquals(listOf(0 to 1), calls.moves)
            assertEquals(tracks.first().id, calls.removed)
        }
    }

    @Test fun dragHandleCommitsPlaylistReorder() {
        val calls = showDetail()
        val dragTag = "playlist-drag:${tracks.first().id}"
        compose.onNodeWithTag("playlist-detail-list").performScrollToNode(hasTestTag(dragTag))
        compose.onNodeWithTag(dragTag).performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, height * 2f), delayMillis = 100)
            up()
        }
        compose.runOnIdle { assertEquals(listOf(0 to 1), calls.moves) }
    }

    @Test fun collectionEntryPointsKeepFavoritesCreationAndQueueSaving() {
        var created = 0
        var savedQueue = 0
        var opened: UUID? = null
        var favoritesOpened = 0
        compose.setContent {
            MeowzixTheme(darkTheme = false) {
                PlaylistsSectionV3(
                    playlists = listOf(playlist, playlist.copy(id = UUID(0, 4), title = "Weekend", trackCount = 18)),
                    playlistArtwork = mapOf(playlist.id to null),
                    favoriteTracks = tracks,
                    onOpenFavorites = { favoritesOpened++ },
                    onOpenPlaylist = { opened = it },
                    onCreate = { created++ },
                    onSaveQueue = { savedQueue++ },
                )
            }
        }
        capture("playlists-light")
        compose.onNodeWithContentDescription("New playlist").performClick()
        compose.onNodeWithContentDescription("Save queue").performClick()
        compose.onNodeWithText("Save current queue?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, savedQueue) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, savedQueue) }
        compose.onNodeWithContentDescription("Save queue").performClick()
        compose.onNodeWithText("Save as playlist").performClick()
        compose.runOnIdle { assertEquals(1, savedQueue) }
        compose.onNodeWithText("Favorites", substring = false).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, favoritesOpened) }
        compose.onNodeWithTag("playlist-grid").performScrollToNode(hasText(playlist.title))
        compose.onNodeWithText(playlist.title).performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(playlist.id, opened) }
        compose.runOnIdle { assertEquals(1, created); assertEquals(1, savedQueue) }
    }

    @Test fun collectionCardsAreCompactAndHeaderRemainsVisibleWhileScrolling() {
        val many = (1..24).map { index ->
            playlist.copy(id = UUID(0, index.toLong()), title = "Playlist $index", trackCount = index)
        }
        compose.setContent {
            MeowzixTheme {
                PlaylistsSectionV3(
                    playlists = many,
                    playlistArtwork = emptyMap(),
                    favoriteTracks = emptyList(),
                    onOpenFavorites = {},
                    onOpenPlaylist = {},
                )
            }
        }
        val height = compose.onNodeWithTag("playlist-card:Playlist 1").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val adjacent = compose.onNodeWithTag("playlist-card:Playlist 2").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        compose.runOnIdle {
            assertEquals(height, adjacent)
            assertTrue("Card should not have a large unused bottom area", height <= 260.dp)
        }
        compose.onNodeWithTag("playlist-grid").performScrollToNode(hasText("Playlist 24"))
        compose.onNodeWithText("Your collections").assertIsDisplayed()
    }

    @Test fun emptyFavoritesDisableAllPlaybackModesInLargeText() {
        showDetail(songs = emptyList(), editable = false, favorites = true, dark = false, fontScale = 1.5f)
        compose.onNodeWithContentDescription("Delete playlist").assertDoesNotExist()
        listOf("Play", "Shuffle", "Smart").forEach { text ->
            scrollToText(text)
            compose.onNodeWithText(text).assertIsNotEnabled()
        }
        scrollToText("Favorite a track and it will appear here.")
        compose.onNodeWithText("Favorite a track and it will appear here.").assertIsDisplayed()
        capture("favorites-empty-large-text")
    }

    @Test fun darkDetailAndEditorRenderWithAllControls() {
        showDetail()
        // The playlist title legitimately appears in the sticky header and the card.
        compose.onAllNodesWithText(playlist.title)[0].assertIsDisplayed()
        capture("playlist-detail-dark")
        compose.onNodeWithContentDescription("Edit playlist").performClick()
        compose.onNodeWithText("Choose image").assertIsDisplayed()
        capture("playlist-editor-dark")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "playlist-previews").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun track(title: String, artist: String) = Track(
        id = UUID.randomUUID(), title = title, normalizedTitle = title.lowercase(), artist = artist,
        normalizedArtist = artist.lowercase(), album = "Album", durationMs = 210_000,
        trackNumber = null, year = 2026, artworkRef = null, favorite = false, hidden = false,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )
}
