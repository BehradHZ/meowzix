package dev.behradhz.meowzix.playback

import android.net.Uri
import android.util.Base64
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.behradhz.meowzix.domain.playback.BrowseAlbum
import dev.behradhz.meowzix.domain.playback.BrowseArtist
import dev.behradhz.meowzix.domain.playback.BrowsePlaylist
import dev.behradhz.meowzix.domain.playback.BrowseTrack
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Stable, privacy-bounded Media3 browse tree shared by Android Auto and other MediaBrowser clients.
 *
 * The tree exposes the Meowzix music library only. Telegram chats, authentication, provider
 * internals, backup and metadata-management surfaces intentionally have no media-library IDs.
 */
@Singleton
class MeowzixMediaLibrary @Inject constructor(
    private val catalog: PlaybackCatalog,
    private val mixProvider: MediaBrowseMixProvider = MediaBrowseMixProvider.Empty,
) {
    fun root(): MediaItem = browsable(ROOT_ID, "Meowzix")

    suspend fun rootChildren(): List<MediaItem> = buildList {
        add(browsable(TRACKS_ID, "Tracks"))
        add(browsable(ARTISTS_ID, "Artists"))
        add(browsable(ALBUMS_ID, "Albums"))
        add(browsable(PLAYLISTS_ID, "Playlists"))
        add(browsable(FAVORITES_ID, "Favorites"))
        if (safeMixes().isNotEmpty()) add(browsable(MIXES_ID, "Mixes"))
    }

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem>? {
        val window = PageWindow.of(page, pageSize)
        return when {
            parentId == ROOT_ID -> if (page == 0) rootChildren().take(window.limit) else emptyList()
            parentId == MIXES_ID -> {
                val mixes = safeMixes()
                mixes.drop(window.offset).take(window.limit).map(::mixItem)
            }
            parentId.startsWith(MIX_PREFIX) -> {
                val mixId = parentId.removePrefix(MIX_PREFIX)
                val mix = safeMixes().firstOrNull { it.id == mixId } ?: return null
                val ids = mix.trackIds.drop(window.offset).take(window.limit)
                catalog.browseTracksByIds(ids).map(::trackItem)
            }
            parentId == TRACKS_ID -> catalog.browseTracks(window.offset, window.limit).map(::trackItem)
            parentId == FAVORITES_ID -> catalog.browseFavorites(window.offset, window.limit).map(::trackItem)
            parentId == ARTISTS_ID -> catalog.browseArtists(window.offset, window.limit).map(::artistItem)
            parentId == ALBUMS_ID -> catalog.browseAlbums(window.offset, window.limit).map(::albumItem)
            parentId == PLAYLISTS_ID -> catalog.browsePlaylists(window.offset, window.limit).map(::playlistItem)
            parentId.startsWith(ARTIST_PREFIX) -> {
                val normalizedArtist = decode(parentId.removePrefix(ARTIST_PREFIX)) ?: return null
                catalog.browseTracksByArtist(normalizedArtist, window.offset, window.limit).map(::trackItem)
            }
            parentId.startsWith(ALBUM_PREFIX) -> {
                val payload = decode(parentId.removePrefix(ALBUM_PREFIX)) ?: return null
                val split = payload.indexOf(ALBUM_SEPARATOR)
                if (split <= 0 || split >= payload.lastIndex) return null
                val normalizedArtist = payload.substring(0, split)
                val album = payload.substring(split + 1)
                catalog.browseTracksByAlbum(album, normalizedArtist, window.offset, window.limit).map(::trackItem)
            }
            parentId.startsWith(PLAYLIST_PREFIX) -> {
                val playlistId = parentId.removePrefix(PLAYLIST_PREFIX).toUuidOrNull() ?: return null
                catalog.browsePlaylistTracks(playlistId, window.offset, window.limit).map(::trackItem)
            }
            else -> null
        }
    }

    suspend fun item(mediaId: String): MediaItem? = when {
        mediaId == ROOT_ID -> root()
        mediaId == TRACKS_ID -> browsable(TRACKS_ID, "Tracks")
        mediaId == ARTISTS_ID -> browsable(ARTISTS_ID, "Artists")
        mediaId == ALBUMS_ID -> browsable(ALBUMS_ID, "Albums")
        mediaId == PLAYLISTS_ID -> browsable(PLAYLISTS_ID, "Playlists")
        mediaId == FAVORITES_ID -> browsable(FAVORITES_ID, "Favorites")
        mediaId == MIXES_ID -> browsable(MIXES_ID, "Mixes")
        mediaId.startsWith(MIX_PREFIX) -> {
            val mixId = mediaId.removePrefix(MIX_PREFIX)
            mixProvider.mixes().firstOrNull { it.id == mixId }?.let(::mixItem)
        }
        mediaId.startsWith(TRACK_PREFIX) -> {
            val trackId = parseTrackId(mediaId) ?: return null
            catalog.browseTrack(trackId)?.let(::trackItem)
        }
        mediaId.startsWith(ARTIST_PREFIX) -> {
            val normalized = decode(mediaId.removePrefix(ARTIST_PREFIX)) ?: return null
            browsable(mediaId, normalized.ifBlank { "Unknown artist" })
        }
        mediaId.startsWith(ALBUM_PREFIX) -> {
            val payload = decode(mediaId.removePrefix(ALBUM_PREFIX)) ?: return null
            val split = payload.indexOf(ALBUM_SEPARATOR)
            if (split <= 0 || split >= payload.lastIndex) return null
            browsable(mediaId, payload.substring(split + 1))
        }
        mediaId.startsWith(PLAYLIST_PREFIX) -> {
            val playlistId = mediaId.removePrefix(PLAYLIST_PREFIX).toUuidOrNull() ?: return null
            val playlist = catalog.browsePlaylist(playlistId) ?: return null
            playlistItem(playlist)
        }
        else -> null
    }

    private suspend fun safeMixes(): List<MediaBrowseMix> = try {
        mixProvider.mixes()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        emptyList()
    }

    fun parseTrackId(mediaId: String): UUID? = when {
        mediaId.startsWith(TRACK_PREFIX) -> mediaId.removePrefix(TRACK_PREFIX).toUuidOrNull()
        else -> mediaId.toUuidOrNull()
    }

    fun trackMediaId(trackId: UUID): String = TRACK_PREFIX + trackId

    private fun artistItem(artist: BrowseArtist): MediaItem = browsable(
        mediaId = ARTIST_PREFIX + encode(artist.normalizedName),
        title = artist.name,
        subtitle = "${artist.trackCount} track${if (artist.trackCount == 1) "" else "s"}",
        artworkRef = artist.artworkRef,
    )

    private fun albumItem(album: BrowseAlbum): MediaItem = browsable(
        mediaId = ALBUM_PREFIX + encode(album.normalizedArtist + ALBUM_SEPARATOR + album.name),
        title = album.name,
        subtitle = "${album.artist} · ${album.trackCount} track${if (album.trackCount == 1) "" else "s"}",
        artworkRef = album.artworkRef,
    )

    private fun mixItem(mix: MediaBrowseMix): MediaItem = browsable(
        mediaId = MIX_PREFIX + mix.id,
        title = mix.title,
        subtitle = "${mix.trackIds.size} track${if (mix.trackIds.size == 1) "" else "s"}",
    )

    private fun playlistItem(playlist: BrowsePlaylist): MediaItem = browsable(
        mediaId = PLAYLIST_PREFIX + playlist.id,
        title = playlist.title,
        subtitle = playlist.description ?: "${playlist.trackCount} track${if (playlist.trackCount == 1) "" else "s"}",
        artworkRef = playlist.artworkRef,
    )

    private fun trackItem(track: BrowseTrack): MediaItem = MediaItem.Builder()
        .setMediaId(trackMediaId(track.id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setDurationMs(track.durationMs)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(track.artworkRef?.let(::safeArtworkUri))
                .setIsBrowsable(false)
                .setIsPlayable(track.isPlayable)
                .build(),
        )
        .build()

    private fun browsable(
        mediaId: String,
        title: String,
        subtitle: String? = null,
        artworkRef: String? = null,
    ): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artworkRef?.let(::safeArtworkUri))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build(),
        )
        .build()

    private fun safeArtworkUri(ref: String): Uri? = runCatching { Uri.parse(ref) }
        .getOrNull()
        ?.takeIf { it.scheme == "content" || it.scheme == "android.resource" }

    private data class PageWindow(val offset: Int, val limit: Int) {
        companion object {
            fun of(page: Int, pageSize: Int): PageWindow {
                val safePage = page.coerceAtLeast(0)
                val safeLimit = pageSize.coerceIn(1, MAX_PAGE_SIZE)
                val offset = (safePage.toLong() * safeLimit.toLong())
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                return PageWindow(offset, safeLimit)
            }
        }
    }

    companion object {
        const val ROOT_ID = "meowzix:root"
        const val TRACKS_ID = "meowzix:tracks"
        const val ARTISTS_ID = "meowzix:artists"
        const val ALBUMS_ID = "meowzix:albums"
        const val PLAYLISTS_ID = "meowzix:playlists"
        const val FAVORITES_ID = "meowzix:favorites"
        const val MIXES_ID = "meowzix:mixes"
        const val MIX_PREFIX = "meowzix:mix:"
        const val TRACK_PREFIX = "meowzix:track:"
        const val ARTIST_PREFIX = "meowzix:artist:"
        const val ALBUM_PREFIX = "meowzix:album:"
        const val PLAYLIST_PREFIX = "meowzix:playlist:"
        const val MAX_PAGE_SIZE = 100
        private const val ALBUM_SEPARATOR = '\u001f'

        private fun encode(value: String): String = Base64.encodeToString(
            value.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )

        private fun decode(value: String): String? = runCatching {
            String(
                Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                StandardCharsets.UTF_8,
            )
        }.getOrNull()
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching(UUID::fromString).getOrNull()
