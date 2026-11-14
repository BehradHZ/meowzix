package dev.behradhz.meowzix.data.telegram

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TelegramTrackSourceEntity
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.downloads.ManagedAudioIntegrityCache
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.data.network.NetworkPolicy
import dev.behradhz.meowzix.data.network.NetworkUse
import dev.behradhz.meowzix.data.repository.isReadableForPlayback
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.RemoteTrackPlaybackResolver
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
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
    private val downloads: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val networkPolicy: NetworkPolicy,
    private val verifiedAudio: ManagedAudioIntegrityCache,
) : RemoteTrackPlaybackResolver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    private var prefetchedFileId: Int? = null
    private val artworkDirectory = File(context.filesDir, "artwork")

    override suspend fun prepareForPlayback(trackId: UUID): PlayableTrack? =
        prepare(trackId, bypassLocalCache = false)

    override suspend fun prepareRemoteForPlayback(trackId: UUID): PlayableTrack? =
        prepare(trackId, bypassLocalCache = true)

    private suspend fun prepare(trackId: UUID, bypassLocalCache: Boolean): PlayableTrack? {
        if (!bypassLocalCache) cachedLocalTrack(trackId)?.let { return it }

        val settings = settingsRepository.networkPlaybackSettings.first()
        networkPolicy.blockReason(settings, NetworkUse.USER_REQUEST)?.let(::error)
        val source = resolveSource(trackId) ?: return null
        val client = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not ready yet.")

        // Audio is the only critical path. This request merely accelerates the transfer; Media3's
        // DataSource/LoadControl determines when enough playable duration exists. No fixed prefix
        // must complete before a MediaItem can be prepared.
        runCatching {
            client.send(
                TdApi.DownloadFile(
                    source.candidate.fileId,
                    TdDownloadPriority.CURRENT_TRACK_AUDIO,
                    0L,
                    0L,
                    false,
                ),
            )
        }.getOrNull()?.let(client.fileStates::publish)

        // Playback and an explicit Save use the same durable download pipeline. A stream
        // starts a retained download that continues after the player moves on or pauses.
        // Only the user's explicit Pause/Cancel/Remove action can stop that download.
        // WorkManager restores unfinished transfers after process death.
        downloads.pinOffline(trackId)
        upgradeArtworkAsync(trackId, source)

        val track = libraryDao.trackById(trackId.toString()) ?: return null
        return PlayableTrack(
            id = trackId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            artworkRef = track.artworkRef,
            contentUri = telegramStreamUri(
                source.candidate.fileId,
                trackId.toString(),
                source.candidate.persistentFileId,
            ) + if (bypassLocalCache) "&ignoreCache=1" else "",
        )
    }

    /**
     * A source read error is not evidence that a song was deleted. Re-query the
     * original message/profile, and only purge the transfer if it is truly gone
     * and there is no playable local copy to retain.
     */
    override suspend fun confirmDeletedSource(trackId: UUID): Boolean {
        if (cachedLocalTrack(trackId) != null) return false
        val accountId = telegramRepository.musicSourceState.value.accountId ?: return false
        val source = telegramDao.telegramSourceForTrack(accountId, trackId.toString()) ?: return false
        val client = TdLibClientAdapter.activeOrNull() ?: return false

        val missing = if (source.messageId < 0L) {
            // A completed profile-audio lookup can confirm removal; network/errors
            // throw and must never be confused with deletion.
            runCatching {
                findTelegramProfileAudio(
                    client, source.chatId, source.messageId, source.tdPersistentFileId,
                ) == null
            }.getOrDefault(false)
        } else {
            try {
                val message = client.send(TdApi.GetMessage(source.chatId, source.messageId))
                message.toAudioCandidate() == null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: TdLibException) {
                isConfirmedMissingTelegramMessage(failure.errorCode)
            } catch (_: Exception) {
                false
            }
        }
        if (!missing) return false

        libraryDao.updateAvailability(
            source.trackSourceId, SourceAvailability.MISSING, Instant.now().toEpochMilli(),
        )
        downloads.discardUnavailableSource(trackId)
        return true
    }

    override fun prefetch(trackId: UUID) {
        cancelPrefetch()
        prefetchJob = scope.launch {
            try {
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
                            TdDownloadPriority.NEXT_TRACK_PRELOAD,
                            0L,
                            PREFETCH_BYTES,
                            true,
                        ),
                    ).also(client.fileStates::publish)
                    upgradeArtworkAsync(trackId, source)
                }
            } finally {
                prefetchedFileId = null
            }
        }
    }

    override fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        // Keep already-fetched TDLib bytes. Queue advance often turns this item into current
        // playback immediately, and discarding useful bytes would create an avoidable rebuffer.
        prefetchedFileId = null
    }

    private suspend fun cachedLocalTrack(trackId: UUID): PlayableTrack? {
        val track = libraryDao.trackById(trackId.toString()) ?: return null
        if (track.hidden) return null
        val now = Instant.now().toEpochMilli()
        val allSources = libraryDao.sourcesForTrack(trackId.toString())
        val sources = allSources
            .filter { it.availability == SourceAvailability.AVAILABLE_LOCAL }
            .sortedBy(::localSourcePriority)
        val telegramSizes = allSources
            .filter { it.type == TrackSourceType.TELEGRAM_REMOTE }
            .map { it.fileSizeBytes }

        for (source in sources) {
            val managed = source.type == TrackSourceType.APP_OFFLINE_COPY ||
                source.type == TrackSourceType.TDLIB_LOCAL
            val verified = if (managed) {
                verifiedAudio.isValidForAnyRemoteSize(
                    source.localPath?.let(::File), telegramSizes,
                    source.fileSizeBytes, source.contentHashSha256,
                )
            } else {
                source.isReadableForPlayback()
            }
            if (!verified) {
                if (managed) source.localPath?.let { verifiedAudio.forget(File(it)) }
                if (source.type == TrackSourceType.APP_OFFLINE_COPY || source.type == TrackSourceType.TDLIB_LOCAL) {
                    libraryDao.updateAvailability(source.id, SourceAvailability.MISSING, now)
                }
                continue
            }
            val contentUri = source.contentUri ?: source.localPath?.let { "file://$it" } ?: continue

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

    /**
     * Prefer persisted Telegram source metadata. GetMessage is only needed if the file id has not
     * been indexed yet or TDLib rejects the persisted id, removing a metadata round-trip from the
     * normal play path.
     */
    private suspend fun resolveSource(trackId: UUID): ResolvedTelegramSource? {
        val accountId = telegramRepository.musicSourceState.value.accountId ?: return null
        val telegramSource = telegramDao.telegramSourceForTrack(accountId, trackId.toString()) ?: return null
        val remoteSource = libraryDao.sourceById(telegramSource.trackSourceId) ?: return null
        if (remoteSource.availability == SourceAvailability.MISSING) return null
        val client = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not ready yet.")

        telegramSource.tdFileId?.let { persistedFileId ->
            val persisted = runCatching { client.seedFileState(persistedFileId) }.getOrNull()
            // Numeric TDLib IDs can be reused after reconnecting. Never bind a
            // playable song to an ID without verifying its stable file identity.
            if (persisted != null && matchesTelegramFileIdentity(
                    telegramSource.tdPersistentFileId,
                    persisted.remote.uniqueId,
                )
            ) {
                return ResolvedTelegramSource(
                    telegramTrackSourceId = telegramSource.trackSourceId,
                    remoteSource = remoteSource,
                    candidate = persistedCandidate(trackId, telegramSource, remoteSource, persistedFileId),
                )
            }
        }

        return if (telegramSource.messageId < 0L) {
            refreshSourceFromProfile(trackId, telegramSource, remoteSource, client)
        } else {
            refreshSourceFromMessage(trackId, telegramSource, remoteSource, client)
        }
    }

    private suspend fun refreshSourceFromProfile(
        trackId: UUID,
        telegramSource: TelegramTrackSourceEntity,
        remoteSource: TrackSourceEntity,
        client: TdLibClientAdapter,
    ): ResolvedTelegramSource? {
        val audio = runCatching {
            findTelegramProfileAudio(
                client, telegramSource.chatId, telegramSource.messageId, telegramSource.tdPersistentFileId,
            )
        }.getOrNull() ?: return null
        val candidate = audio.toAudioCandidate(telegramSource.messageId)
        telegramDao.upsertTelegramTrackSource(
            telegramSource.copy(
                tdFileId = candidate.fileId,
                tdPersistentFileId = candidate.persistentFileId ?: telegramSource.tdPersistentFileId,
            ),
        )
        runCatching { client.seedFileState(candidate.fileId) }
        return ResolvedTelegramSource(telegramSource.trackSourceId, remoteSource, candidate)
    }

    private suspend fun refreshSourceFromMessage(
        trackId: UUID,
        telegramSource: TelegramTrackSourceEntity,
        remoteSource: TrackSourceEntity,
        client: TdLibClientAdapter,
    ): ResolvedTelegramSource? {
        val message = runCatching {
            client.send(TdApi.GetMessage(telegramSource.chatId, telegramSource.messageId))
        }.getOrNull() ?: return null
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
        runCatching { client.seedFileState(candidate.fileId) }
        return ResolvedTelegramSource(telegramSource.trackSourceId, remoteSource, candidate)
    }

    private suspend fun persistedCandidate(
        trackId: UUID,
        telegramSource: TelegramTrackSourceEntity,
        remoteSource: TrackSourceEntity,
        fileId: Int,
    ): TelegramAudioCandidate {
        val track = libraryDao.trackById(trackId.toString())
        return TelegramAudioCandidate(
            messageId = telegramSource.messageId,
            title = telegramSource.telegramTitle ?: track?.title ?: telegramSource.fileName ?: "Unknown Track",
            artist = telegramSource.telegramPerformer ?: track?.artist,
            durationMs = track?.durationMs ?: 0L,
            fileName = telegramSource.fileName,
            mimeType = remoteSource.mimeType,
            fileId = fileId,
            persistentFileId = telegramSource.tdPersistentFileId,
            fileSizeBytes = remoteSource.fileSizeBytes,
            artworkMinithumbnail = null,
            artworkFileId = null,
        )
    }

    private fun upgradeArtworkAsync(trackId: UUID, source: ResolvedTelegramSource) {
        scope.launch {
            runCatching {
                val current = libraryDao.trackById(trackId.toString())?.artworkRef
                if (!current.isNullOrBlank() && !current.contains("/artwork-preview/")) return@runCatching
                val client = TdLibClientAdapter.activeOrNull() ?: return@runCatching
                val candidate = if (source.candidate.artworkFileId != null) {
                    source.candidate
                } else {
                    val accountId = telegramRepository.musicSourceState.value.accountId ?: return@runCatching
                    val telegramSource = telegramDao.telegramSourceForTrack(accountId, trackId.toString())
                        ?: return@runCatching
                    val refreshed = if (telegramSource.messageId < 0L) {
                        refreshSourceFromProfile(trackId, telegramSource, source.remoteSource, client)
                    } else {
                        refreshSourceFromMessage(trackId, telegramSource, source.remoteSource, client)
                    } ?: return@runCatching
                    refreshed.candidate
                }
                ensureHighQualityArtwork(client, trackId, candidate)
            }
        }
    }

    private suspend fun ensureHighQualityArtwork(
        client: TdLibClientAdapter,
        trackId: UUID,
        candidate: TelegramAudioCandidate,
    ): String? {
        val trackIdString = trackId.toString()
        val current = libraryDao.trackById(trackIdString)?.artworkRef
        if (!current.isNullOrBlank() && !current.contains("/artwork-preview/")) return current

        val artworkFileId = candidate.artworkFileId ?: return current
        val downloaded = runCatching {
            client.send(
                TdApi.DownloadFile(
                    artworkFileId,
                    TdDownloadPriority.VISIBLE_ARTWORK,
                    0L,
                    0L,
                    true,
                ),
            )
        }.getOrNull() ?: return current
        val sourcePath = downloaded.local.path.takeIf {
            downloaded.local.isDownloadingCompleted && it.isNotBlank()
        } ?: return current
        val sourceFile = File(sourcePath).takeIf(File::isFile) ?: return current

        artworkDirectory.mkdirs()
        val artworkFile = File(artworkDirectory, "$trackId.artwork")
        val partial = File(artworkDirectory, "$trackId.artwork.part")
        return runCatching {
            sourceFile.inputStream().buffered().use { input ->
                partial.outputStream().buffered().use(input::copyTo)
            }
            if (artworkFile.exists() && !artworkFile.delete()) {
                error("Unable to replace cached artwork")
            }
            if (!partial.renameTo(artworkFile)) {
                error("Unable to finalize cached artwork")
            }
            val uri = Uri.fromFile(artworkFile).toString()
            libraryDao.setArtworkRef(trackIdString, uri, Instant.now().toEpochMilli())
            uri
        }.getOrElse {
            partial.delete()
            current
        }
    }

    private data class ResolvedTelegramSource(
        val telegramTrackSourceId: String,
        val remoteSource: TrackSourceEntity,
        val candidate: TelegramAudioCandidate,
    )

    private companion object {
        const val PREFETCH_BYTES = 2L * 1024L * 1024L
    }
}

private fun localSourcePriority(source: TrackSourceEntity): Int = when (source.type) {
    TrackSourceType.APP_OFFLINE_COPY -> 0
    TrackSourceType.TDLIB_LOCAL -> 1
    TrackSourceType.LOCAL_MEDIASTORE -> 2
    else -> 3
}
/** 404 is a missing/inaccessible original message, not a transport timeout or flood limit. */
internal fun isConfirmedMissingTelegramMessage(errorCode: Int): Boolean = errorCode == 404
