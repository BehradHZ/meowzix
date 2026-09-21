package dev.behradhz.meowzix.data.telegram

import android.net.Uri
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.network.NetworkPolicy
import dev.behradhz.meowzix.data.network.NetworkUse
import dev.behradhz.meowzix.domain.playback.PlayableTrack
import dev.behradhz.meowzix.domain.playback.RemoteTrackPlaybackResolver
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import dev.behradhz.meowzix.domain.settings.SettingsRepository
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
    private val telegramRepository: TelegramRepository,
    private val telegramDao: TelegramDao,
    private val libraryDao: LibraryDao,
    private val settingsRepository: SettingsRepository,
    private val networkPolicy: NetworkPolicy,
) : RemoteTrackPlaybackResolver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null
    private var prefetchedFileId: Int? = null

    override suspend fun prepareForPlayback(trackId: UUID): PlayableTrack? {
        cancelPrefetch()
        val settings = settingsRepository.networkPlaybackSettings.first()
        networkPolicy.blockReason(settings, NetworkUse.USER_REQUEST)?.let(::error)
        return downloadAndPersist(trackId, DOWNLOAD_PRIORITY)
    }

    override fun prefetch(trackId: UUID) {
        cancelPrefetch()
        prefetchJob = scope.launch {
            runCatching {
                val settings = settingsRepository.networkPlaybackSettings.first()
                if (!settings.prefetchEnabled) return@runCatching
                if (networkPolicy.blockReason(settings, NetworkUse.PREFETCH) != null) return@runCatching
                downloadAndPersist(trackId, PREFETCH_PRIORITY)
            }
        }
    }

    override fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
        prefetchedFileId?.let { fileId ->
            scope.launch {
                runCatching { TdLibClientAdapter.activeOrNull()?.send(TdApi.CancelDownloadFile(fileId, false)) }
            }
        }
        prefetchedFileId = null
    }

    private suspend fun downloadAndPersist(trackId: UUID, priority: Int): PlayableTrack? {
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
        if (priority == PREFETCH_PRIORITY) prefetchedFileId = candidate.fileId

        val downloaded = client.send(
            TdApi.DownloadFile(
                candidate.fileId,
                priority,
                0L,
                0L,
                true,
            ),
        )
        val path = downloaded.local.path.takeIf {
            downloaded.local.isDownloadingCompleted && it.isNotBlank()
        } ?: error("Telegram finished without a playable local file.")
        if (priority == PREFETCH_PRIORITY) prefetchedFileId = null
        val file = File(path)
        if (!file.isFile) error("Telegram's downloaded file is no longer available.")

        val now = Instant.now().toEpochMilli()
        val localSourceId = UUID.nameUUIDFromBytes(
            "tdlib-local:${telegramSource.trackSourceId}".toByteArray(),
        ).toString()
        val previousLocalSource = libraryDao.sourceById(localSourceId)
        libraryDao.upsertSource(
            TrackSourceEntity(
                id = localSourceId,
                trackId = trackId.toString(),
                type = TrackSourceType.TDLIB_LOCAL,
                availability = SourceAvailability.AVAILABLE_LOCAL,
                contentUri = null,
                localPath = file.absolutePath,
                mimeType = candidate.mimeType ?: remoteSource.mimeType,
                fileSizeBytes = downloaded.size.toLong().takeIf { it > 0L }
                    ?: remoteSource.fileSizeBytes,
                contentHashSha256 = previousLocalSource?.contentHashSha256,
                trainingEligible = remoteSource.trainingEligible,
                createdAtEpochMs = previousLocalSource?.createdAtEpochMs ?: now,
                lastVerifiedAtEpochMs = now,
            ),
        )
        telegramDao.upsertTelegramTrackSource(
            telegramSource.copy(
                tdFileId = downloaded.id,
                tdPersistentFileId = downloaded.remote.uniqueId.takeIf(String::isNotBlank)
                    ?: telegramSource.tdPersistentFileId,
                fileName = candidate.fileName ?: telegramSource.fileName,
                telegramTitle = candidate.title,
                telegramPerformer = candidate.artist,
            ),
        )

        val track = libraryDao.trackById(trackId.toString()) ?: return null
        return PlayableTrack(
            id = trackId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            artworkRef = track.artworkRef,
            contentUri = Uri.fromFile(file).toString(),
        )
    }

    private companion object {
        const val DOWNLOAD_PRIORITY = 32
        const val PREFETCH_PRIORITY = 1
    }
}
