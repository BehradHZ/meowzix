package dev.behradhz.meowzix.playback

import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.playback.BrowseAlbum
import dev.behradhz.meowzix.domain.playback.BrowseArtist
import dev.behradhz.meowzix.domain.playback.BrowsePlaylist
import dev.behradhz.meowzix.domain.playback.BrowseTrack
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeowzixMediaLibraryTest {
    private val playableId = UUID(0L, 1L)
    private val unavailableId = UUID(0L, 2L)
    private val playlistId = UUID(0L, 10L)
    private val catalog = FakeBrowseCatalog(playableId, unavailableId, playlistId)
    private val library = MeowzixMediaLibrary(catalog)

    @Test
    fun rootAndChildrenExposeOnlyMusicLibraryNodes() = runBlocking {
        val root = library.root()
        assertEquals(MeowzixMediaLibrary.ROOT_ID, root.mediaId)
        assertEquals(true, root.mediaMetadata.isBrowsable)
        assertEquals(false, root.mediaMetadata.isPlayable)

        val children = library.children(MeowzixMediaLibrary.ROOT_ID, page = 0, pageSize = 100)
        assertNotNull(children)
        assertEquals(
            listOf(
                MeowzixMediaLibrary.TRACKS_ID,
                MeowzixMediaLibrary.ARTISTS_ID,
                MeowzixMediaLibrary.ALBUMS_ID,
                MeowzixMediaLibrary.PLAYLISTS_ID,
                MeowzixMediaLibrary.FAVORITES_ID,
            ),
            children!!.map { it.mediaId },
        )
        val serialized = children.joinToString("|") { it.mediaId.lowercase() }
        assertFalse(serialized.contains("telegram"))
        assertFalse(serialized.contains("auth"))
        assertFalse(serialized.contains("chat"))
        assertFalse(serialized.contains("backup"))
    }

    @Test
    fun trackAndFavoriteBrowsingUseStableCanonicalIdsAndTruthfulFlags() = runBlocking {
        val tracks = library.children(MeowzixMediaLibrary.TRACKS_ID, page = 0, pageSize = 50)!!
        assertEquals(2, tracks.size)
        assertEquals("meowzix:track:$playableId", tracks[0].mediaId)
        assertEquals(true, tracks[0].mediaMetadata.isPlayable)
        assertEquals(false, tracks[0].mediaMetadata.isBrowsable)
        assertEquals("Playable", tracks[0].mediaMetadata.title)
        assertEquals("Artist", tracks[0].mediaMetadata.artist)

        assertEquals("meowzix:track:$unavailableId", tracks[1].mediaId)
        assertEquals(false, tracks[1].mediaMetadata.isPlayable)
        assertEquals(unavailableId, library.parseTrackId(tracks[1].mediaId))

        val favorites = library.children(MeowzixMediaLibrary.FAVORITES_ID, 0, 10)!!
        assertEquals(listOf("meowzix:track:$playableId"), favorites.map { it.mediaId })
    }

    @Test
    fun artistsAlbumsAndPlaylistsAreBrowsableAndLeadToTracks() = runBlocking {
        val artist = library.children(MeowzixMediaLibrary.ARTISTS_ID, 0, 10)!!.single()
        assertEquals(true, artist.mediaMetadata.isBrowsable)
        assertEquals(false, artist.mediaMetadata.isPlayable)
        assertEquals(listOf("meowzix:track:$playableId"), library.children(artist.mediaId, 0, 10)!!.map { it.mediaId })

        val album = library.children(MeowzixMediaLibrary.ALBUMS_ID, 0, 10)!!.single()
        assertEquals(listOf("meowzix:track:$playableId"), library.children(album.mediaId, 0, 10)!!.map { it.mediaId })

        val playlist = library.children(MeowzixMediaLibrary.PLAYLISTS_ID, 0, 10)!!.single()
        assertEquals("meowzix:playlist:$playlistId", playlist.mediaId)
        assertEquals(listOf("meowzix:track:$playableId"), library.children(playlist.mediaId, 0, 10)!!.map { it.mediaId })
    }


    @Test
    fun personalizedMixesAppearOnlyWhenProviderHasRealItems() = runBlocking {
        val mixProvider = object : MediaBrowseMixProvider {
            override suspend fun mixes(): List<MediaBrowseMix> = listOf(
                MediaBrowseMix(
                    id = "FOR_YOU_NOW",
                    title = "For You Now",
                    trackIds = listOf(playableId),
                ),
            )
        }
        val mixedLibrary = MeowzixMediaLibrary(catalog, mixProvider)

        val rootChildren = mixedLibrary.children(MeowzixMediaLibrary.ROOT_ID, 0, 100)!!
        assertTrue(rootChildren.any { it.mediaId == MeowzixMediaLibrary.MIXES_ID })

        val mixNodes = mixedLibrary.children(MeowzixMediaLibrary.MIXES_ID, 0, 10)!!
        assertEquals(listOf("meowzix:mix:FOR_YOU_NOW"), mixNodes.map { it.mediaId })
        assertEquals(true, mixNodes.single().mediaMetadata.isBrowsable)

        val mixTracks = mixedLibrary.children("meowzix:mix:FOR_YOU_NOW", 0, 10)!!
        assertEquals(listOf("meowzix:track:$playableId"), mixTracks.map { it.mediaId })
    }

    @Test
    fun browsePagesAreBoundedAndUnknownIdsFailClosed() = runBlocking {
        library.children(MeowzixMediaLibrary.TRACKS_ID, page = 3, pageSize = 10_000)
        assertEquals(300, catalog.lastTrackOffset)
        assertEquals(MeowzixMediaLibrary.MAX_PAGE_SIZE, catalog.lastTrackLimit)

        assertNull(library.children("meowzix:telegram:private-chat", 0, 10))
        assertNull(library.item("meowzix:track:not-a-uuid"))
    }

    @Test
    fun getItemReturnsStableTrackMetadata() = runBlocking {
        val item = library.item("meowzix:track:$playableId")
        assertNotNull(item)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, item!!.mediaMetadata.mediaType)
        assertEquals(true, item.mediaMetadata.isPlayable)
        assertEquals("Playable", item.mediaMetadata.title)
    }

    private class FakeBrowseCatalog(
        private val playableId: UUID,
        private val unavailableId: UUID,
        private val playlistId: UUID,
    ) : PlaybackCatalog {
        var lastTrackOffset: Int = -1
        var lastTrackLimit: Int = -1

        private val playable = BrowseTrack(
            id = playableId,
            title = "Playable",
            artist = "Artist",
            album = "Album",
            durationMs = 180_000,
            artworkRef = null,
            favorite = true,
            isPlayable = true,
        )
        private val unavailable = BrowseTrack(
            id = unavailableId,
            title = "Unavailable",
            artist = "Artist",
            album = "Album",
            durationMs = 200_000,
            artworkRef = null,
            favorite = false,
            isPlayable = false,
        )

        override suspend fun availableLocalTracks(): List<PlayableTrack> = emptyList()

        override suspend fun browseTracks(offset: Int, limit: Int): List<BrowseTrack> {
            lastTrackOffset = offset
            lastTrackLimit = limit
            return if (offset == 0) listOf(playable, unavailable).take(limit) else emptyList()
        }

        override suspend fun browseFavorites(offset: Int, limit: Int): List<BrowseTrack> =
            if (offset == 0) listOf(playable).take(limit) else emptyList()

        override suspend fun browseArtists(offset: Int, limit: Int): List<BrowseArtist> =
            if (offset == 0) listOf(BrowseArtist("Artist", "artist", 1, null)).take(limit) else emptyList()

        override suspend fun browseAlbums(offset: Int, limit: Int): List<BrowseAlbum> =
            if (offset == 0) listOf(BrowseAlbum("Album", "Artist", "artist", 1, null)).take(limit) else emptyList()

        override suspend fun browseTracksByArtist(
            normalizedArtist: String,
            offset: Int,
            limit: Int,
        ): List<BrowseTrack> = if (normalizedArtist == "artist" && offset == 0) listOf(playable).take(limit) else emptyList()

        override suspend fun browseTracksByAlbum(
            album: String,
            normalizedArtist: String,
            offset: Int,
            limit: Int,
        ): List<BrowseTrack> =
            if (album == "Album" && normalizedArtist == "artist" && offset == 0) listOf(playable).take(limit) else emptyList()

        override suspend fun browsePlaylists(offset: Int, limit: Int): List<BrowsePlaylist> =
            if (offset == 0) listOf(BrowsePlaylist(playlistId, "Playlist", null, null, 1)).take(limit) else emptyList()

        override suspend fun browsePlaylistTracks(
            playlistId: UUID,
            offset: Int,
            limit: Int,
        ): List<BrowseTrack> = if (playlistId == this.playlistId && offset == 0) listOf(playable).take(limit) else emptyList()

        override suspend fun browseTrack(trackId: UUID): BrowseTrack? = when (trackId) {
            playableId -> playable
            unavailableId -> unavailable
            else -> null
        }
    }
}
