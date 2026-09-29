package dev.behradhz.meowzix.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.common.TextNormalizer
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.LocalMediaSourceEntity
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TrackEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.localmedia.LocalMediaScanner
import dev.behradhz.meowzix.data.localmedia.ScannedLocalTrack
import dev.behradhz.meowzix.data.telegram.TdDownloadPriority
import dev.behradhz.meowzix.data.telegram.TdLibClientAdapter
import dev.behradhz.meowzix.data.telegram.toAudioCandidate
import dev.behradhz.meowzix.domain.library.LibraryTrack
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.LocalLibraryRefreshResult
import dev.behradhz.meowzix.domain.library.MusicLibraryRepository
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.PlaybackCatalog
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

@Singleton
class LocalMusicLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MeowzixDatabase,
    private val dao: LibraryDao,
    private val telegramDao: TelegramDao,
    private val scanner: LocalMediaScanner,
) : MusicLibraryRepository, PlaybackCatalog {
    private val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkAttempts = ConcurrentHashMap.newKeySet<String>()
    private val artworkMutex = Mutex()
    private val artworkDirectory = File(context.filesDir, "artwork")
    private var lastSuccessfulRefreshAtEpochMs: Long? = null

    override fun observeTracks(): Flow<List<Track>> = dao.observeAvailableTracks()
        .map { rows -> rows.map(TrackEntity::toDomain) }
        .flowOn(Dispatchers.Default)

    override fun observeLibraryTracks(): Flow<List<LibraryTrack>> =
        combine(dao.observeAvailableTracks(), dao.observeActiveSources()) { tracks, sources ->
            val sourcesByTrack = sources.groupBy { it.trackId }
            tracks.map { entity ->
                val trackSources = sourcesByTrack[entity.id].orEmpty()
                val availability = when {
                    trackSources.any { it.availability == SourceAvailability.AVAILABLE_LOCAL } ->
                        LibraryTrackAvailability.OFFLINE
                    trackSources.any {
                        it.type == TrackSourceType.TELEGRAM_REMOTE &&
                            it.availability == SourceAvailability.REMOTE_ONLY
                    } -> LibraryTrackAvailability.CLOUD
                    else -> LibraryTrackAvailability.UNAVAILABLE
                }
                LibraryTrack(entity.toDomain(), availability)
            }
        }.flowOn(Dispatchers.Default)

    override suspend fun shouldRefreshLocalMusic(): Boolean {
        val dbVerifiedAt = dao.latestLocalVerificationEpochMs() ?: 0L
        val lastVerifiedAt = maxOf(dbVerifiedAt, lastSuccessfulRefreshAtEpochMs ?: 0L)
        if (lastVerifiedAt == 0L) return true
        return Instant.now().toEpochMilli() - lastVerifiedAt >= AUTO_REFRESH_INTERVAL_MS
    }

    override suspend fun refreshLocalMusic(): LocalLibraryRefreshResult {
        val scanned = scanner.scan()
        val now = Instant.now().toEpochMilli()
        var updatedCount = 0

        val plan = database.withTransaction {
            // These snapshots are loaded once per refresh. Crucially, candidate matching for a new
            // song no longer re-loads every Track and TrackSource for every scanned row.
            val existingSources = dao.allLocalSources()
            val existingSourcesById = existingSources.associateBy { it.id }
            val existingTracksById = dao.allTracksWithLocalSources().associateBy { it.id }
            val existingLocalMediaBySourceId = dao.allLocalMediaSources().associateBy { it.trackSourceId }
            val snapshots = existingSources.mapNotNull { source ->
                source.contentUri?.let { uri ->
                    ExistingLocalSourceSnapshot(source.id, source.trackId, uri)
                }
            }
            val reconciliation = LocalLibraryReconciler.plan(scanned, snapshots)
            val pendingCandidates = PendingCandidateIndex()
            val mutations = ArrayList<PreparedLocalMutation>(
                reconciliation.toCreate.size + reconciliation.toUpdate.size,
            )

            reconciliation.toCreate.forEach { item ->
                mutations += prepareScannedItem(
                    item = item,
                    existingSource = null,
                    existingTrack = null,
                    now = now,
                    pendingCandidates = pendingCandidates,
                )
            }

            val unchangedSourceIds = mutableListOf<String>()
            reconciliation.toUpdate.forEach { match ->
                val existingSource = existingSourcesById[match.existing.sourceId] ?: return@forEach
                val existingTrack = existingTracksById[match.existing.trackId]
                val existingLocalMedia = existingLocalMediaBySourceId[existingSource.id]
                if (isUnchanged(match.scanned, existingSource, existingTrack, existingLocalMedia)) {
                    unchangedSourceIds += existingSource.id
                } else {
                    mutations += prepareScannedItem(
                        item = match.scanned,
                        existingSource = existingSource,
                        existingTrack = existingTrack,
                        now = now,
                        pendingCandidates = pendingCandidates,
                    )
                    updatedCount++
                }
            }

            persistMutationsInBatches(mutations)
            updateAvailabilityInChunks(
                unchangedSourceIds,
                SourceAvailability.AVAILABLE_LOCAL,
                now,
            )
            updateAvailabilityInChunks(
                reconciliation.missingSourceIds,
                SourceAvailability.MISSING,
                now,
            )
            reconciliation
        }

        lastSuccessfulRefreshAtEpochMs = now
        return LocalLibraryRefreshResult(
            discovered = plan.discovered,
            created = plan.toCreate.size,
            updated = updatedCount,
            markedMissing = plan.missingSourceIds.size,
        )
    }

    override suspend fun unmergeSource(sourceId: UUID): UUID = database.withTransaction {
        val source = requireNotNull(dao.sourceById(sourceId.toString())) {
            "Unknown track source."
        }
        val sourceTrack = requireNotNull(dao.trackById(source.trackId)) {
            "Track source has no Track."
        }
        val siblings = dao.sourcesForTrack(source.trackId)
        if (siblings.size == 1) return@withTransaction UUID.fromString(source.trackId)

        val now = Instant.now().toEpochMilli()
        val newTrackId = UUID.randomUUID().toString()
        dao.upsertTrack(
            sourceTrack.copy(
                id = newTrackId,
                favorite = false,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        dao.moveSource(source.id, newTrackId)
        UUID.fromString(newTrackId)
    }

    override suspend fun setFavorite(trackId: UUID, favorite: Boolean) {
        dao.setFavorite(trackId.toString(), favorite, Instant.now().toEpochMilli())
    }

    override fun prefetchArtwork(trackIds: List<UUID>) {
        val requested = trackIds
            .asSequence()
            .map(UUID::toString)
            .distinct()
            .filter(artworkAttempts::add)
            .toList()
        if (requested.isEmpty()) return

        artworkScope.launch {
            artworkMutex.withLock {
                val client = TdLibClientAdapter.activeOrNull()
                if (client == null) {
                    requested.forEach(artworkAttempts::remove)
                    return@withLock
                }
                artworkDirectory.mkdirs()

                requested.forEach { trackId ->
                    val terminal = runCatching {
                        fetchHighQualityArtwork(client, trackId)
                    }.getOrDefault(false)

                    if (!terminal) artworkAttempts.remove(trackId)
                }
            }
        }
    }

    private suspend fun fetchHighQualityArtwork(
        client: TdLibClientAdapter,
        trackId: String,
    ): Boolean {
        val track = dao.trackById(trackId) ?: return true
        val existingArtwork = track.artworkRef
        val legacyPreview = existingArtwork?.contains("/artwork-preview/") == true

        if (!existingArtwork.isNullOrBlank() && !legacyPreview) return true

        if (legacyPreview) {
            deleteLegacyArtworkPreview(existingArtwork)
            dao.setArtworkRef(
                trackId = trackId,
                artworkRef = null,
                updatedAt = Instant.now().toEpochMilli(),
            )
        }

        val telegram = telegramDao.telegramSourceForAnyAccountTrack(trackId) ?: return true
        val message = runCatching {
            client.send(TdApi.GetMessage(telegram.chatId, telegram.messageId))
        }.getOrNull() ?: return false
        val candidate = message.toAudioCandidate() ?: return true
        val artworkFileId = candidate.artworkFileId ?: return true

        val downloaded = runCatching {
            client.send(
                TdApi.DownloadFile(
                    artworkFileId,
                    TdDownloadPriority.NEAR_VISIBLE_ARTWORK,
                    0L,
                    0L,
                    true,
                ),
            )
        }.getOrNull() ?: return false
        val downloadedPath = downloaded.local.path.takeIf {
            downloaded.local.isDownloadingCompleted && it.isNotBlank()
        } ?: return false
        val sourceFile = File(downloadedPath).takeIf(File::isFile) ?: return false
        val finalArtwork = File(artworkDirectory, "$trackId.artwork")

        return runCatching {
            sourceFile.inputStream().buffered().use { input ->
                finalArtwork.outputStream().buffered().use(input::copyTo)
            }
            dao.setArtworkRef(
                trackId = trackId,
                artworkRef = Uri.fromFile(finalArtwork).toString(),
                updatedAt = Instant.now().toEpochMilli(),
            )
        }.isSuccess
    }

    private fun deleteLegacyArtworkPreview(ref: String) {
        runCatching {
            val file = Uri.parse(ref).path?.let(::File) ?: return@runCatching
            val previewRoot = File(context.cacheDir, "artwork-preview").canonicalFile
            val candidate = file.canonicalFile
            if (candidate.path.startsWith(previewRoot.path)) candidate.delete()
        }
    }

    override suspend fun availableLocalTracks(): List<PlayableTrack> {
        val rows = dao.availableLocalPlaybackRows()
        return withContext(Dispatchers.Default) {
            rows.distinctBy { it.id }
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
    }

    override suspend fun isTrackLocallyPlayable(trackId: UUID): Boolean {
        val track = dao.trackById(trackId.toString()) ?: return false
        if (track.hidden) return false
        return dao.sourcesForTrack(trackId.toString()).any { source ->
            source.availability == SourceAvailability.AVAILABLE_LOCAL && when {
                !source.contentUri.isNullOrBlank() -> true
                !source.localPath.isNullOrBlank() -> source.localPath?.let { File(it).isFile } == true
                else -> false
            }
        }
    }

    override suspend fun availableTracks(): List<PlayableTrack> {
        val tracks = dao.availableTracks()
        val sourcesByTrack = dao.allSources()
            .filter { it.availability != SourceAvailability.MISSING }
            .groupBy { it.trackId }
        val telegramBySource = telegramDao.allSelectedTelegramTrackSources().associateBy { it.trackSourceId }

        return tracks.mapNotNull { track ->
            val sources = sourcesByTrack[track.id].orEmpty()
            val local = sources
                .filter {
                    it.availability == SourceAvailability.AVAILABLE_LOCAL &&
                        (it.contentUri != null || it.localPath != null)
                }
                .minByOrNull(::localSourcePriority)

            val contentUri = if (local != null) {
                local.contentUri ?: local.localPath?.let { "file://$it" }
            } else {
                val remote = sources
                    .asSequence()
                    .filter {
                        it.type == TrackSourceType.TELEGRAM_REMOTE &&
                            it.availability == SourceAvailability.REMOTE_ONLY
                    }
                    .mapNotNull { source -> telegramBySource[source.id] }
                    .firstOrNull { it.tdFileId != null }
                remote?.tdFileId?.let { fileId ->
                    "meowzix-tdlib://audio/$fileId?trackId=${track.id}"
                }
            } ?: return@mapNotNull null

            PlayableTrack(
                id = UUID.fromString(track.id),
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                artworkRef = track.artworkRef,
                contentUri = contentUri,
            )
        }.sortedBy { it.title.lowercase() }
    }

    private suspend fun updateAvailabilityInChunks(
        sourceIds: List<String>,
        availability: SourceAvailability,
        verifiedAt: Long,
    ) {
        sourceIds.chunked(AVAILABILITY_UPDATE_CHUNK_SIZE).forEach { chunk ->
            dao.updateAvailability(chunk, availability, verifiedAt)
        }
    }

    private suspend fun prepareScannedItem(
        item: ScannedLocalTrack,
        existingSource: TrackSourceEntity?,
        existingTrack: TrackEntity?,
        now: Long,
        pendingCandidates: PendingCandidateIndex,
    ): PreparedLocalMutation {
        val normalizedTitle = TextNormalizer.normalize(item.title) ?: "unknown track"
        val normalizedArtist = TextNormalizer.normalize(item.artist)
        val incomingIdentity = IncomingTrackIdentity(
            normalizedTitle = normalizedTitle,
            normalizedArtist = normalizedArtist,
            durationMs = item.durationMs,
            contentHashSha256 = existingSource?.contentHashSha256,
        )

        val trackId = existingTrack?.id
            ?: existingSource?.trackId
            ?: pendingCandidates.match(incomingIdentity)
            ?: findMatchingTrack(incomingIdentity)
            ?: UUID.randomUUID().toString()
        val sourceId = existingSource?.id ?: UUID.randomUUID().toString()
        val canonicalTrack = existingTrack
            ?: pendingCandidates.track(trackId)
            ?: dao.trackById(trackId)

        val track = TrackEntity(
            id = trackId,
            title = item.title,
            normalizedTitle = normalizedTitle,
            artist = item.artist,
            normalizedArtist = normalizedArtist,
            album = item.album,
            durationMs = item.durationMs,
            trackNumber = item.trackNumber,
            year = item.year,
            artworkRef = item.artworkRef ?: canonicalTrack?.artworkRef,
            favorite = canonicalTrack?.favorite ?: false,
            hidden = canonicalTrack?.hidden ?: false,
            createdAtEpochMs = canonicalTrack?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )
        val source = TrackSourceEntity(
            id = sourceId,
            trackId = trackId,
            type = TrackSourceType.LOCAL_MEDIASTORE,
            availability = SourceAvailability.AVAILABLE_LOCAL,
            contentUri = item.contentUri,
            localPath = null,
            mimeType = item.mimeType,
            fileSizeBytes = item.fileSizeBytes,
            contentHashSha256 = existingSource?.contentHashSha256,
            trainingEligible = existingSource?.trainingEligible ?: true,
            createdAtEpochMs = existingSource?.createdAtEpochMs ?: now,
            lastVerifiedAtEpochMs = now,
        )
        val localMedia = LocalMediaSourceEntity(
            trackSourceId = sourceId,
            mediaStoreId = item.mediaStoreId,
            contentUri = item.contentUri,
            relativePath = item.relativePath,
            displayName = item.displayName,
            dateModifiedSeconds = item.dateModifiedSeconds,
        )
        pendingCandidates.put(track, source.contentHashSha256)
        return PreparedLocalMutation(track, source, localMedia)
    }

    private suspend fun persistMutationsInBatches(mutations: List<PreparedLocalMutation>) {
        if (mutations.isEmpty()) return

        // Multiple physical sources can resolve to one canonical Track. Keep the last metadata
        // refinement for that Track, then write each entity class in bounded batches to stay below
        // SQLite bind-variable limits and reduce invalidation/transaction overhead.
        val tracksById = LinkedHashMap<String, TrackEntity>()
        mutations.forEach { mutation -> tracksById[mutation.track.id] = mutation.track }
        tracksById.values.toList().chunked(DB_WRITE_CHUNK_SIZE).forEach(dao::upsertTracks)
        mutations.map(PreparedLocalMutation::source)
            .chunked(DB_WRITE_CHUNK_SIZE)
            .forEach(dao::upsertSources)
        mutations.map(PreparedLocalMutation::localMedia)
            .chunked(DB_WRITE_CHUNK_SIZE)
            .forEach(dao::upsertLocalMediaSources)
    }

    private fun isUnchanged(
        item: ScannedLocalTrack,
        source: TrackSourceEntity,
        track: TrackEntity?,
        localMedia: LocalMediaSourceEntity?,
    ): Boolean {
        if (track == null || localMedia == null) return false
        val artworkMatches = item.artworkRef == null || track.artworkRef == item.artworkRef
        return source.availability == SourceAvailability.AVAILABLE_LOCAL &&
            source.contentUri == item.contentUri &&
            source.mimeType == item.mimeType &&
            source.fileSizeBytes == item.fileSizeBytes &&
            localMedia.mediaStoreId == item.mediaStoreId &&
            localMedia.contentUri == item.contentUri &&
            localMedia.relativePath == item.relativePath &&
            localMedia.displayName == item.displayName &&
            localMedia.dateModifiedSeconds == item.dateModifiedSeconds &&
            track.title == item.title &&
            track.artist == item.artist &&
            track.album == item.album &&
            track.durationMs == item.durationMs &&
            track.trackNumber == item.trackNumber &&
            track.year == item.year &&
            artworkMatches
    }

    private suspend fun findMatchingTrack(incoming: IncomingTrackIdentity): String? {
        incoming.contentHashSha256?.let { hash ->
            val hashCandidates = dao.matchingTracksByContentHash(hash).map { row ->
                TrackMatchCandidate(
                    trackId = row.trackId,
                    normalizedTitle = row.normalizedTitle,
                    normalizedArtist = row.normalizedArtist,
                    durationMs = row.durationMs,
                    contentHashes = setOf(row.contentHashSha256),
                )
            }
            UnifiedTrackMatcher.match(incoming, hashCandidates)?.let { return it }
        }

        val artist = incoming.normalizedArtist ?: return null
        val tolerance = max(2_500L, incoming.durationMs / 40L)
        val candidates = dao.matchingTrackCandidates(
            normalizedTitle = incoming.normalizedTitle,
            normalizedArtist = artist,
            minDurationMs = (incoming.durationMs - tolerance).coerceAtLeast(0L),
            maxDurationMs = incoming.durationMs + tolerance,
        ).map { row ->
            TrackMatchCandidate(
                trackId = row.id,
                normalizedTitle = row.normalizedTitle,
                normalizedArtist = row.normalizedArtist,
                durationMs = row.durationMs,
            )
        }
        return UnifiedTrackMatcher.match(incoming, candidates)
    }

    private companion object {
        const val AUTO_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1_000L
        const val AVAILABILITY_UPDATE_CHUNK_SIZE = 500
        const val DB_WRITE_CHUNK_SIZE = 250
    }
}

private data class PreparedLocalMutation(
    val track: TrackEntity,
    val source: TrackSourceEntity,
    val localMedia: LocalMediaSourceEntity,
)

/** Indexes not-yet-written candidates so batching does not reintroduce O(N²) in memory. */
private class PendingCandidateIndex {
    private val tracksById = HashMap<String, TrackEntity>()
    private val byMetadata = HashMap<Pair<String, String?>, MutableList<TrackMatchCandidate>>()
    private val byHash = HashMap<String, MutableList<TrackMatchCandidate>>()

    fun track(trackId: String): TrackEntity? = tracksById[trackId]

    fun match(incoming: IncomingTrackIdentity): String? {
        incoming.contentHashSha256?.let { hash ->
            UnifiedTrackMatcher.match(incoming, byHash[hash].orEmpty())?.let { return it }
        }
        return UnifiedTrackMatcher.match(
            incoming,
            byMetadata[incoming.normalizedTitle to incoming.normalizedArtist].orEmpty(),
        )
    }

    fun put(track: TrackEntity, contentHashSha256: String?) {
        tracksById[track.id] = track
        val candidate = TrackMatchCandidate(
            trackId = track.id,
            normalizedTitle = track.normalizedTitle,
            normalizedArtist = track.normalizedArtist,
            durationMs = track.durationMs,
            contentHashes = contentHashSha256?.let(::setOf).orEmpty(),
        )
        val key = track.normalizedTitle to track.normalizedArtist
        byMetadata.getOrPut(key) { mutableListOf() }.apply {
            removeAll { it.trackId == track.id }
            add(candidate)
        }
        if (contentHashSha256 != null) {
            byHash.getOrPut(contentHashSha256) { mutableListOf() }.apply {
                removeAll { it.trackId == track.id }
                add(candidate)
            }
        }
    }
}

private fun localSourcePriority(source: TrackSourceEntity): Int = when (source.type) {
    TrackSourceType.LOCAL_MEDIASTORE -> 0
    TrackSourceType.APP_OFFLINE_COPY -> 1
    TrackSourceType.TDLIB_LOCAL -> 2
    else -> 3
}

private fun TrackEntity.toDomain() = Track(
    id = UUID.fromString(id),
    title = title,
    normalizedTitle = normalizedTitle,
    artist = artist,
    normalizedArtist = normalizedArtist,
    album = album,
    durationMs = durationMs,
    trackNumber = trackNumber,
    year = year,
    artworkRef = artworkRef,
    favorite = favorite,
    hidden = hidden,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)
