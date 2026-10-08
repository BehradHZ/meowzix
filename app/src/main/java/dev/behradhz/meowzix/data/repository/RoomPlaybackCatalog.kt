package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryBrowseDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.PlaylistDao
import dev.behradhz.meowzix.data.db.RecommendationDao
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TelegramTrackSourceEntity
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.domain.playback.BrowseAlbum
import dev.behradhz.meowzix.domain.playback.BrowseArtist
import dev.behradhz.meowzix.domain.playback.BrowsePlaylist
import dev.behradhz.meowzix.domain.playback.BrowseTrack
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Playback-facing projection of the library.
 *
 * Unlike LocalMusicLibraryRepository.availableTracks(), bounded queue resolution never reads every
 * TrackSource in the database just to play one requested UUID list. Queries are chunked below the
 * SQLite bind-variable limit and results are restored to the caller's logical queue order.
 *
 * The Android media-library projections intentionally live here as bounded views over the same
 * canonical library/source resolution used by phone playback. Android Auto does not get a parallel
 * repository or provider-specific identity model.
 */
@Singleton
class RoomPlaybackCatalog @Inject constructor(
    private val libraryDao: LibraryDao,
    private val libraryBrowseDao: LibraryBrowseDao,
    private val playlistDao: PlaylistDao,
    private val recommendationDao: RecommendationDao,
    private val telegramDao: TelegramDao,
) : PlaybackCatalog {
    override suspend fun availableLocalTracks(): List<PlayableTrack> = withContext(Dispatchers.Default) {
        libraryDao.availableLocalPlaybackRows()
            .distinctBy { it.id }
            .map { row ->
                PlayableTrack(
                    id = UUID.fromString(row.id),
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    durationMs = row.durationMs,
                    artworkRef = row.artworkRef,
                    contentUri = row.contentUri,
                )
            }
    }

    override suspend fun isTrackLocallyPlayable(trackId: UUID): Boolean = withContext(Dispatchers.Default) {
        val track = libraryDao.trackById(trackId.toString()) ?: return@withContext false
        if (track.hidden) return@withContext false
        libraryDao.sourcesForTrack(trackId.toString()).any { source ->
            source.availability == SourceAvailability.AVAILABLE_LOCAL && when {
                !source.contentUri.isNullOrBlank() -> true
                !source.localPath.isNullOrBlank() -> source.localPath?.let { File(it).isFile } == true
                else -> false
            }
        }
    }

    override suspend fun playableTrack(trackId: UUID): PlayableTrack? =
        availableTracks(listOf(trackId)).firstOrNull()

    override suspend fun availableTracks(trackIds: List<UUID>): List<PlayableTrack> = withContext(Dispatchers.Default) {
        val orderedIds = trackIds.distinct()
        if (orderedIds.isEmpty()) return@withContext emptyList()

        val resolved = HashMap<UUID, PlayableTrack>(orderedIds.size)
        orderedIds.chunked(QUERY_CHUNK_SIZE).forEach { chunk ->
            val ids = chunk.map(UUID::toString)
            val tracks = recommendationDao.tracksByIds(ids)
            val sourcesByTrack = recommendationDao.activeSourcesForTracks(ids).groupBy { it.trackId }
            val telegramBySource = telegramDao.selectedTelegramTrackSourcesForTrackIds(ids)
                .associateBy(TelegramTrackSourceEntity::trackSourceId)

            tracks.forEach { track ->
                resolvePlayableTrack(track, sourcesByTrack[track.id].orEmpty(), telegramBySource)
                    ?.let { resolved[it.id] = it }
            }
        }
        orderedIds.mapNotNull(resolved::get)
    }

    override suspend fun availableTracks(): List<PlayableTrack> = withContext(Dispatchers.Default) {
        val ids = libraryDao.availableTracks().map { UUID.fromString(it.id) }
        availableTracks(ids).sortedBy { it.title.lowercase() }
    }

    override suspend fun browseTracks(offset: Int, limit: Int): List<BrowseTrack> =
        withContext(Dispatchers.Default) {
            val window = browseWindow(offset, limit)
            toBrowseTracks(libraryBrowseDao.browseTracks(window.limit, window.offset))
        }

    override suspend fun browseFavorites(offset: Int, limit: Int): List<BrowseTrack> =
        withContext(Dispatchers.Default) {
            val window = browseWindow(offset, limit)
            toBrowseTracks(libraryBrowseDao.browseFavoriteTracks(window.limit, window.offset))
        }

    override suspend fun browseArtists(offset: Int, limit: Int): List<BrowseArtist> =
        withContext(Dispatchers.Default) {
            val window = browseWindow(offset, limit)
            libraryBrowseDao.browseArtistSummaries(window.limit, window.offset).map { row ->
                BrowseArtist(
                    name = row.name,
                    normalizedName = row.normalizedName,
                    trackCount = row.trackCount,
                    artworkRef = row.artworkRef,
                )
            }
        }

    override suspend fun browseAlbums(offset: Int, limit: Int): List<BrowseAlbum> =
        withContext(Dispatchers.Default) {
            val window = browseWindow(offset, limit)
            libraryBrowseDao.browseAlbumSummaries(window.limit, window.offset).map { row ->
                BrowseAlbum(
                    name = row.name,
                    artist = row.artist,
                    normalizedArtist = row.normalizedArtist,
                    trackCount = row.trackCount,
                    artworkRef = row.artworkRef,
                )
            }
        }

    override suspend fun browseTracksByArtist(
        normalizedArtist: String,
        offset: Int,
        limit: Int,
    ): List<BrowseTrack> = withContext(Dispatchers.Default) {
        val window = browseWindow(offset, limit)
        toBrowseTracks(
            libraryBrowseDao.browseTracksByArtist(
                normalizedArtist = normalizedArtist,
                limit = window.limit,
                offset = window.offset,
            ),
        )
    }

    override suspend fun browseTracksByAlbum(
        album: String,
        normalizedArtist: String,
        offset: Int,
        limit: Int,
    ): List<BrowseTrack> = withContext(Dispatchers.Default) {
        val window = browseWindow(offset, limit)
        toBrowseTracks(
            libraryBrowseDao.browseTracksByAlbum(
                album = album,
                normalizedArtist = normalizedArtist,
                limit = window.limit,
                offset = window.offset,
            ),
        )
    }

    override suspend fun browsePlaylists(offset: Int, limit: Int): List<BrowsePlaylist> =
        withContext(Dispatchers.Default) {
            val window = browseWindow(offset, limit)
            playlistDao.browsePlaylists(window.limit, window.offset).map { row ->
                BrowsePlaylist(
                    id = UUID.fromString(row.id),
                    title = row.title,
                    description = row.description,
                    artworkRef = row.artworkRef,
                    trackCount = row.trackCount,
                )
            }
        }

    override suspend fun browsePlaylist(playlistId: UUID): BrowsePlaylist? =
        withContext(Dispatchers.Default) {
            playlistDao.browsePlaylist(playlistId.toString())?.let { row ->
                BrowsePlaylist(
                    id = UUID.fromString(row.id),
                    title = row.title,
                    description = row.description,
                    artworkRef = row.artworkRef,
                    trackCount = row.trackCount,
                )
            }
        }

    override suspend fun browsePlaylistTracks(
        playlistId: UUID,
        offset: Int,
        limit: Int,
    ): List<BrowseTrack> = withContext(Dispatchers.Default) {
        val window = browseWindow(offset, limit)
        toBrowseTracks(
            playlistDao.browseTracks(
                playlistId = playlistId.toString(),
                limit = window.limit,
                offset = window.offset,
            ),
        )
    }

    override suspend fun browseTracksByIds(trackIds: List<UUID>): List<BrowseTrack> =
        withContext(Dispatchers.Default) {
            val orderedIds = trackIds.distinct().take(MAX_BROWSE_PAGE_SIZE)
            if (orderedIds.isEmpty()) return@withContext emptyList()
            val tracks = recommendationDao.tracksByIds(orderedIds.map(UUID::toString))
            val byId = toBrowseTracks(tracks).associateBy(BrowseTrack::id)
            orderedIds.mapNotNull(byId::get)
        }

    override suspend fun browseTrack(trackId: UUID): BrowseTrack? = withContext(Dispatchers.Default) {
        val track = libraryDao.trackById(trackId.toString())
            ?.takeUnless(TrackEntity::hidden)
            ?: return@withContext null
        toBrowseTracks(listOf(track)).firstOrNull()
    }

    private suspend fun toBrowseTracks(tracks: List<TrackEntity>): List<BrowseTrack> {
        // Hidden canonical tracks must not leak through playlist or personalized Mix browsing,
        // even when an external browser presents a previously known Track UUID.
        val visible = tracks.filterNot(TrackEntity::hidden)
        if (visible.isEmpty()) return emptyList()
        val ids = visible.map(TrackEntity::id)
        val sourcesByTrack = recommendationDao.activeSourcesForTracks(ids).groupBy(TrackSourceEntity::trackId)
        val telegramBySource = telegramDao.selectedTelegramTrackSourcesForTrackIds(ids)
            .associateBy(TelegramTrackSourceEntity::trackSourceId)

        return visible.map { track ->
            BrowseTrack(
                id = UUID.fromString(track.id),
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                artworkRef = track.artworkRef,
                favorite = track.favorite,
                isPlayable = resolvePlayableTrack(
                    track,
                    sourcesByTrack[track.id].orEmpty(),
                    telegramBySource,
                ) != null,
            )
        }
    }

    private fun resolvePlayableTrack(
        track: TrackEntity,
        sources: List<TrackSourceEntity>,
        telegramBySource: Map<String, TelegramTrackSourceEntity>,
    ): PlayableTrack? {
        if (track.hidden) return null
        val local = sources
            .asSequence()
            .filter {
                it.availability == SourceAvailability.AVAILABLE_LOCAL &&
                    (!it.contentUri.isNullOrBlank() || !it.localPath.isNullOrBlank())
            }
            .minByOrNull(::localSourcePriority)

        val contentUri = if (local != null) {
            local.contentUri ?: local.localPath?.let { "file://$it" }
        } else {
            sources.asSequence()
                .filter {
                    it.type == TrackSourceType.TELEGRAM_REMOTE &&
                        it.availability == SourceAvailability.REMOTE_ONLY
                }
                .mapNotNull { source -> telegramBySource[source.id] }
                .firstOrNull { it.tdFileId != null }
                ?.tdFileId
                ?.let { fileId -> "meowzix-tdlib://audio/$fileId?trackId=${track.id}" }
        } ?: return null

        return PlayableTrack(
            id = UUID.fromString(track.id),
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            artworkRef = track.artworkRef,
            contentUri = contentUri,
        )
    }

    private fun browseWindow(offset: Int, limit: Int): BrowseWindow = BrowseWindow(
        offset = offset.coerceAtLeast(0),
        limit = limit.coerceIn(1, MAX_BROWSE_PAGE_SIZE),
    )

    private companion object {
        const val QUERY_CHUNK_SIZE = 400
        const val MAX_BROWSE_PAGE_SIZE = 100
    }
}

private data class BrowseWindow(val offset: Int, val limit: Int)

private fun localSourcePriority(source: TrackSourceEntity): Int = when (source.type) {
    TrackSourceType.LOCAL_MEDIASTORE -> 0
    TrackSourceType.APP_OFFLINE_COPY -> 1
    TrackSourceType.TDLIB_LOCAL -> 2
    else -> 3
}
