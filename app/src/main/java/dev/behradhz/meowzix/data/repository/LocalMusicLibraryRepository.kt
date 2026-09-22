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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    override fun observeTracks(): Flow<List<Track>> = dao.observeAvailableTracks().map { rows ->
        rows.map(TrackEntity::toDomain)
    }

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
        }

    override suspend fun refreshLocalMusic(): LocalLibraryRefreshResult {
        val scanned = scanner.scan()
        val now = Instant.now().toEpochMilli()

        val plan = database.withTransaction {
            val existingSources = dao.allLocalSources()
            val existingSourcesById = existingSources.associateBy { it.id }
            val existingTracksById = dao.allTracksWithLocalSources().associateBy { it.id }
            val snapshots = existingSources.mapNotNull { source ->
                source.contentUri?.let { uri ->
                    ExistingLocalSourceSnapshot(source.id, source.trackId, uri)
                }
            }
            val reconciliation = LocalLibraryReconciler.plan(scanned, snapshots)

            for (item in reconciliation.toCreate) {
                persistScannedItem(item, null, null, now)
            }
            for (match in reconciliation.toUpdate) {
                val existingSource = existingSourcesById[match.existing.sourceId] ?: continue
                persistScannedItem(
                    match.scanned,
                    existingSource,
                    existingTracksById[match.existing.trackId],
                    now,
                )
            }
            for (sourceId in reconciliation.missingSourceIds) {
                dao.updateAvailability(sourceId, SourceAvailability.MISSING, now)
            }
            reconciliation
        }

        return LocalLibraryRefreshResult(
            plan.discovered,
            plan.toCreate.size,
            plan.toUpdate.size,
            plan.missingSourceIds.size,
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

                    // Transient failures may retry when a row/player becomes visible again.
                    // Successful fetches and tracks with no Telegram artwork stay deduplicated.
                    if (!terminal) artworkAttempts.remove(trackId)
                }
            }
        }
    }

    /**
     * Only the real Telegram album-cover thumbnail file is persisted. The tiny minithumbnail is
     * intentionally ignored so list and player artwork never settle on the low-resolution preview.
     */
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
            client.send(TdApi.DownloadFile(artworkFileId, ARTWORK_PRIORITY, 0L, 0L, true))
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

    override suspend fun availableLocalTracks(): List<PlayableTrack> =
        dao.availableLocalPlaybackRows()
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

    private suspend fun persistScannedItem(
        item: ScannedLocalTrack,
        existingSource: TrackSourceEntity?,
        existingTrack: TrackEntity?,
        now: Long,
    ) {
        val trackId = existingTrack?.id ?: existingSource?.trackId ?: findMatchingTrack(
            normalizedTitle = TextNormalizer.normalize(item.title) ?: "unknown track",
            normalizedArtist = TextNormalizer.normalize(item.artist),
            durationMs = item.durationMs,
            contentHashSha256 = existingSource?.contentHashSha256,
        ) ?: UUID.randomUUID().toString()
        val sourceId = existingSource?.id ?: UUID.randomUUID().toString()
        val canonicalTrack = existingTrack ?: dao.trackById(trackId)

        dao.upsertTrack(
            TrackEntity(
                id = trackId,
                title = item.title,
                normalizedTitle = TextNormalizer.normalize(item.title) ?: "unknown track",
                artist = item.artist,
                normalizedArtist = TextNormalizer.normalize(item.artist),
                album = item.album,
                durationMs = item.durationMs,
                trackNumber = item.trackNumber,
                year = item.year,
                artworkRef = item.artworkRef ?: canonicalTrack?.artworkRef,
                favorite = canonicalTrack?.favorite ?: false,
                hidden = canonicalTrack?.hidden ?: false,
                createdAtEpochMs = canonicalTrack?.createdAtEpochMs ?: now,
                updatedAtEpochMs = now,
            ),
        )
        dao.upsertSource(
            TrackSourceEntity(
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
            ),
        )
        dao.upsertLocalMediaSource(
            LocalMediaSourceEntity(
                trackSourceId = sourceId,
                mediaStoreId = item.mediaStoreId,
                contentUri = item.contentUri,
                relativePath = item.relativePath,
                displayName = item.displayName,
                dateModifiedSeconds = item.dateModifiedSeconds,
            ),
        )
    }

    private suspend fun findMatchingTrack(
        normalizedTitle: String,
        normalizedArtist: String?,
        durationMs: Long,
        contentHashSha256: String?,
    ): String? {
        val sourcesByTrack = dao.allSources().groupBy { it.trackId }
        val candidates = dao.allTracks().map { track ->
            TrackMatchCandidate(
                trackId = track.id,
                normalizedTitle = track.normalizedTitle,
                normalizedArtist = track.normalizedArtist,
                durationMs = track.durationMs,
                contentHashes = sourcesByTrack[track.id].orEmpty()
                    .mapNotNullTo(mutableSetOf()) { it.contentHashSha256 },
            )
        }
        return UnifiedTrackMatcher.match(
            IncomingTrackIdentity(
                normalizedTitle,
                normalizedArtist,
                durationMs,
                contentHashSha256,
            ),
            candidates,
        )
    }
}

private const val ARTWORK_PRIORITY = 3

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
