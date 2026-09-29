package dev.behradhz.meowzix.data.repository

import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.RecommendationDao
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.db.TelegramTrackSourceEntity
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
 */
@Singleton
class RoomPlaybackCatalog @Inject constructor(
    private val libraryDao: LibraryDao,
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

    private companion object {
        const val QUERY_CHUNK_SIZE = 400
    }
}

private fun localSourcePriority(source: TrackSourceEntity): Int = when (source.type) {
    TrackSourceType.LOCAL_MEDIASTORE -> 0
    TrackSourceType.APP_OFFLINE_COPY -> 1
    TrackSourceType.TDLIB_LOCAL -> 2
    else -> 3
}
