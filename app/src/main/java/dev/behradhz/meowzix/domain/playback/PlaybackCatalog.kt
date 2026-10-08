package dev.behradhz.meowzix.domain.playback

import java.util.UUID

data class PlayableTrack(
    val id: UUID,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkRef: String?,
    val contentUri: String,
)

data class BrowseTrack(
    val id: UUID,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artworkRef: String?,
    val favorite: Boolean,
    val isPlayable: Boolean,
)

data class BrowseArtist(
    val name: String,
    val normalizedName: String,
    val trackCount: Int,
    val artworkRef: String?,
)

data class BrowseAlbum(
    val name: String,
    val artist: String,
    val normalizedArtist: String,
    val trackCount: Int,
    val artworkRef: String?,
)

data class BrowsePlaylist(
    val id: UUID,
    val title: String,
    val description: String?,
    val artworkRef: String?,
    val trackCount: Int,
)

interface PlaybackCatalog {
    /** Returns only sources that are already readable from local storage. */
    suspend fun availableLocalTracks(): List<PlayableTrack>

    /**
     * Fast-path local availability check for one canonical Track. Implementations should avoid
     * loading/scanning the complete local playback catalog for this query.
     */
    suspend fun isTrackLocallyPlayable(trackId: UUID): Boolean =
        availableLocalTracks().any { it.id == trackId }

    /** Resolve one canonical track without materializing the complete playback catalog. */
    suspend fun playableTrack(trackId: UUID): PlayableTrack? =
        availableTracks(listOf(trackId)).firstOrNull()

    /**
     * Resolve only the requested logical queue. Implementations should keep the caller's UUID order
     * and avoid loading unrelated Track/TrackSource rows.
     */
    suspend fun availableTracks(trackIds: List<UUID>): List<PlayableTrack> {
        if (trackIds.isEmpty()) return emptyList()
        val requested = trackIds.toHashSet()
        val byId = availableTracks().associateBy(PlayableTrack::id)
        return trackIds.distinct().mapNotNull { id -> if (id in requested) byId[id] else null }
    }

    /**
     * Returns every currently playable library track. Remote Telegram rows are represented by a
     * meowzix-tdlib:// URI and are resolved lazily by the playback data source when they become
     * current. Whole-library callers should prefer the bounded browse methods below.
     */
    suspend fun availableTracks(): List<PlayableTrack> = availableLocalTracks()

    /**
     * Android Auto / external library browsing projections. These methods are deliberately part of
     * the playback-facing catalog rather than a car-specific repository so every client shares the
     * same canonical Track identity and source-resolution policy. Implementations must keep them
     * bounded by [offset] and [limit].
     */
    suspend fun browseTracks(offset: Int, limit: Int): List<BrowseTrack> = emptyList()
    suspend fun browseFavorites(offset: Int, limit: Int): List<BrowseTrack> = emptyList()
    suspend fun browseArtists(offset: Int, limit: Int): List<BrowseArtist> = emptyList()
    suspend fun browseAlbums(offset: Int, limit: Int): List<BrowseAlbum> = emptyList()
    suspend fun browseTracksByArtist(
        normalizedArtist: String,
        offset: Int,
        limit: Int,
    ): List<BrowseTrack> = emptyList()

    suspend fun browseTracksByAlbum(
        album: String,
        normalizedArtist: String,
        offset: Int,
        limit: Int,
    ): List<BrowseTrack> = emptyList()

    suspend fun browsePlaylists(offset: Int, limit: Int): List<BrowsePlaylist> = emptyList()
    suspend fun browsePlaylist(playlistId: UUID): BrowsePlaylist? = null
    suspend fun browsePlaylistTracks(
        playlistId: UUID,
        offset: Int,
        limit: Int,
    ): List<BrowseTrack> = emptyList()

    suspend fun browseTracksByIds(trackIds: List<UUID>): List<BrowseTrack> =
        trackIds.distinct().mapNotNull { browseTrack(it) }

    suspend fun browseTrack(trackId: UUID): BrowseTrack? =
        playableTrack(trackId)?.let { track ->
            BrowseTrack(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                artworkRef = track.artworkRef,
                favorite = false,
                isPlayable = true,
            )
        }
}
