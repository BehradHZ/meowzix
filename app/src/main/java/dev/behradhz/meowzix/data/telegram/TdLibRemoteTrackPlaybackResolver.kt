package dev.behradhz.meowzix.data.telegram

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.network.NetworkPolicy
import dev.behradhz.meowzix.data.network.NetworkUse
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.RemoteTrackPlaybackResolver
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@Singleton
class TdLibRemoteTrackPlaybackResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramRepository: TelegramRepository,
    private val telegramDao: TelegramDao,
    private val libraryDao: LibraryDao,
    private val settingsRepository: SettingsRepository,
    private val networkPolicy: NetworkPolicy,
) : RemoteTrackPlaybackResolver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    private var prefetchedFileId: Int? = null
    private val fullDownloadJobs = ConcurrentHashMap<UUID, Job>()
    private val activeFullFileIds = ConcurrentHashMap.newKeySet<Int>()
    private val playbackDirectory = File(context.filesDir, "playback-cache")
    private val artworkDirectory = File(context.filesDir, "artwork")

    override suspend fun prepareForPlayback(trackId: UUID): PlayableTrack? {
        cachedLocalTrack(trackId)?.let { return it }

        val settings = settingsRepository.networkPlaybackSettings.first()
        networkPolicy.blockReason(settings, NetworkUse.USER_REQUEST)?.let(::error)
        val source = resolveSource(trackId) ?: return null
        val client = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not ready yet.")

        client.send(
            TdApi.DownloadFile(
                source.candidate.fileId,
                PLAYBACK_PRIORITY,
                0L,
                INITIAL_PLAYBACK_BYTES,
                true,
            ),
        )

        startPermanentDownload(trackId, source)
        val track = libraryDao.trackById(trackId.toString()) ?: return null
        return PlayableTrack(
            id = trackId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            artworkRef = track.artworkRef,
            contentUri = streamUri(source.candidate.fileId, trackId),
        )
    }

    override fun prefetch(trackId: UUID) {
        cancelPrefetch()
        prefetchJob = scope.launch {
            runCatching {
                if (cachedLocalTrack(trackId) != null) return@runCatching
                val settings = settingsRepository.networkPlaybackSettings.first()
                if (!settings.prefetchEnabled) return@runCatching
                if (networkPolicy.blockReason(settings, NetworkUse.PREFETCH) != null) return@runCatching
                val source = resolveSource(trackId) ?: return@runCatching
                val client = TdLibClientAdapter.activeOrNull() ?: return@runCatching
                prefetchedFileId = source.candidate.fileId
                client.send(
                    TdApi.DownloadFile(
                        source.candidate.fileId,
                        PREFETCH_PRIORITY,
                        0L,
                        PREFETCH_BYTES,
                        true,
                    ),
                )
                prefetchedFileId = null
            }
        }
    }

    override fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        val fileId = prefetchedFileId
        prefetchedFileId = null
        if (fileId != null && fileId !in activeFullFileIds) {
            scope.launch {
                runCatching { TdLibClientAdapter.activeOrNull()?.send(TdApi.CancelDownloadFile(fileId, false)) }
            }
        }
    }

    private suspend fun cachedLocalTrack(trackId: UUID): PlayableTrack? {
        val track = libraryDao.trackById(trackId.toString()) ?: return null
        if (track.hidden) return null
        val now = Instant.now().toEpochMilli()
        val sources = libraryDao.sourcesForTrack(trackId.toString())
            .filter { it.availability == SourceAvailability.AVAILABLE_LOCAL }
            .sortedBy(::localSourcePriority)

        for (source in sources) {
            val contentUri = when {
                !source.contentUri.isNullOrBlank() -> source.contentUri
                !source.localPath.isNullOrBlank() -> {
                    val path = source.localPath ?: continue
                    if (File(path).isFile) {
                        "file://$path"
                    } else {
                        if (source.type == TrackSourceType.APP_OFFLINE_COPY || source.type == TrackSourceType.TDLIB_LOCAL) {
                            libraryDao.updateAvailability(source.id, SourceAvailability.MISSING, now)
                        }
                        null
                    }
                }
                else -> null
            } ?: continue

            return PlayableTrack(
                id = trackId,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                artworkRef = track.artworkRef,
                contentUri = contentUri,
            )
        }
        return null
    }

    private suspend fun resolveSource(trackId: UUID): ResolvedTelegramSource? {
        val accountId = telegramRepository.musicSourceState.value.accountId ?: return null
        val telegramSource = telegramDao.telegramSourceForTrack(accountId, trackId.toString()) ?: return null
        val remoteSource = libraryDao.sourceById(telegramSource.trackSourceId) ?: return null
        if (remoteSource.availability == SourceAvailability.MISSING) return null
        val client = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not ready yet.")
        val message = client.send(TdApi.GetMessage(telegramSource.chatId, telegramSource.messageId))
        val candidate = message.toAudioCandidate()
        if (candidate == null) {
            libraryDao.updateAvailability(
                telegramSource.trackSourceId,
                SourceAvailability.MISSING,
                Instant.now().toEpochMilli(),
            )
            return null
        }
        telegramDao.upsertTelegramTrackSource(
            telegramSource.copy(
                tdFileId = candidate.fileId,
                tdPersistentFileId = candidate.persistentFileId ?: telegramSource.tdPersistentFileId,
                fileName = candidate.fileName ?: telegramSource.fileName,
                telegramTitle = candidate.title,
                telegramPerformer = candidate.artist,
            ),
        )
        return ResolvedTelegramSource(telegramSource.trackSourceId, remoteSource, candidate)
    }

    private fun startPermanentDownload(trackId: UUID, source: ResolvedTelegramSource) {
        if (fullDownloadJobs[trackId]?.isActive == true) return
        fullDownloadJobs[trackId] = scope.launch {
            val fileId = source.candidate.fileId
            activeFullFileIds += fileId
            try {
                try {
                    val client = TdLibClientAdapter.activeOrNull() ?: return@launch
                    val downloaded = client.send(
                        TdApi.DownloadFile(fileId, BACKGROUND_PRIORITY, 0L, 0L, true),
                    )
                    val tdPath = downloaded.local.path.takeIf {
                        downloaded.local.isDownloadingCompleted && it.isNotBlank()
                    } ?: return@launch
                    val sourceFile = File(tdPath).takeIf(File::isFile) ?: return@launch
                    val permanent = copyToPermanentCache(trackId, source.candidate.fileName, sourceFile)
                    val now = Instant.now().toEpochMilli()
                    val localSourceId = UUID.nameUUIDFromBytes(
                        "tdlib-local:$trackId".toByteArray(),
                    ).toString()
                    val previousLocalSource = libraryDao.sourceById(localSourceId)
                    libraryDao.upsertSource(
                        TrackSourceEntity(
                            id = localSourceId,
                            trackId = trackId.toString(),
                            type = TrackSourceType.TDLIB_LOCAL,
                            availability = SourceAvailability.AVAILABLE_LOCAL,
                            contentUri = null,
                            localPath = permanent.absolutePath,
                            mimeType = source.candidate.mimeType ?: source.remoteSource.mimeType,
                            fileSizeBytes = permanent.length(),
                            contentHashSha256 = previousLocalSource?.contentHashSha256,
                            trainingEligible = source.remoteSource.trainingEligible,
                            createdAtEpochMs = previousLocalSource?.createdAtEpochMs ?: now,
                            lastVerifiedAtEpochMs = now,
                        ),
                    )
                    extractFullArtwork(trackId, permanent)?.let { artworkUri ->
                        libraryDao.setArtworkRef(trackId.toString(), artworkUri, Instant.now().toEpochMilli())
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                }
            } finally {
                activeFullFileIds -= fileId
                fullDownloadJobs.remove(trackId)
            }
        }
    }

    private fun copyToPermanentCache(trackId: UUID, fileName: String?, source: File): File {
        playbackDirectory.mkdirs()
        val extension = fileName?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
        val destination = File(
            playbackDirectory,
            trackId.toString() + extension?.let { ".$it" }.orEmpty(),
        )
        if (destination.isFile && destination.length() == source.length()) return destination
        val partial = File(playbackDirectory, destination.name + ".part")
        source.inputStream().buffered().use { input ->
            partial.outputStream().buffered().use(input::copyTo)
        }
        if (destination.exists() && !destination.delete()) error("Unable to replace playback cache")
        if (!partial.renameTo(destination)) error("Unable to finalize playback cache")
        return destination
    }

    private fun extractFullArtwork(trackId: UUID, audioFile: File): String? {
        val bytes = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(audioFile.absolutePath)
                retriever.embeddedPicture
            } finally {
                retriever.release()
            }
        }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        artworkDirectory.mkdirs()
        val artworkFile = File(artworkDirectory, "$trackId.jpg")
        artworkFile.writeBytes(bytes)
        return Uri.fromFile(artworkFile).toString()
    }

    private data class ResolvedTelegramSource(
        val telegramTrackSourceId: String,
        val remoteSource: TrackSourceEntity,
        val candidate: TelegramAudioCandidate,
    )

    private companion object {
        const val PLAYBACK_PRIORITY = 32
        const val BACKGROUND_PRIORITY = 20
        const val PREFETCH_PRIORITY = 4
        const val INITIAL_PLAYBACK_BYTES = 1536L * 1024L
        const val PREFETCH_BYTES = 768L * 1024L

        fun streamUri(fileId: Int, trackId: UUID): String =
            "meowzix-tdlib://audio/$fileId?trackId=$trackId"
    }
}

private fun localSourcePriority(source: TrackSourceEntity): Int = when (source.type) {
    TrackSourceType.APP_OFFLINE_COPY -> 0
    TrackSourceType.TDLIB_LOCAL -> 1
    TrackSourceType.LOCAL_MEDIASTORE -> 2
    else -> 3
}
