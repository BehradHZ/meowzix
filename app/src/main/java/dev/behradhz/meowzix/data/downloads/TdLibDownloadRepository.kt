package dev.behradhz.meowzix.data.downloads

import android.content.Context
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.DownloadDao
import dev.behradhz.meowzix.data.db.DownloadRecordEntity
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.data.db.MeowzixDatabase
import dev.behradhz.meowzix.data.db.TelegramDao
import dev.behradhz.meowzix.data.db.TrackSourceEntity
import dev.behradhz.meowzix.data.network.NetworkPolicy
import dev.behradhz.meowzix.data.network.NetworkUse
import dev.behradhz.meowzix.data.telegram.TdLibClientAdapter
import dev.behradhz.meowzix.data.telegram.toAudioCandidate
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.drinkless.tdlib.TdApi

@Singleton
class TdLibDownloadRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MeowzixDatabase,
    private val downloadDao: DownloadDao,
    private val libraryDao: LibraryDao,
    private val telegramDao: TelegramDao,
    private val telegramRepository: TelegramRepository,
    private val settingsRepository: SettingsRepository,
    private val networkPolicy: NetworkPolicy,
    private val managedStorage: ManagedStorageManager,
) : DownloadRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<UUID, Job>()
    private val pauseRequests = ConcurrentHashMap.newKeySet<UUID>()
    private val offlineDirectory = File(context.filesDir, "offline")

    init {
        scope.launch {
            runCatching { reconcileCompletedDownloads() }
        }
    }

    /**
     * Repair legacy pinned copies on startup, but never delete the user's MediaStore music.
     * A disconnected Telegram account leaves the record FAILED until connectivity returns.
     */
    private suspend fun reconcileCompletedDownloads() {
        val pending = mutableListOf<UUID>()
        for (record in downloadDao.all()) {
            if (!record.pinned || record.status != DownloadStatus.COMPLETED.name ||
                record.localPath.isNullOrBlank()
            ) continue
            val trackId = runCatching { UUID.fromString(record.trackId) }.getOrNull() ?: continue
            val sources = libraryDao.sourcesForTrack(record.trackId)
            val managed = sources.firstOrNull {
                it.type == TrackSourceType.APP_OFFLINE_COPY && it.localPath == record.localPath
            }
            val expected = ManagedAudioIntegrity.expectedBytes(
                sources.firstOrNull { it.type == TrackSourceType.TELEGRAM_REMOTE }?.fileSizeBytes,
                record.totalBytes,
                managed?.fileSizeBytes,
            )
            if (ManagedAudioIntegrity.isValid(
                    File(record.localPath), expected, managed?.contentHashSha256,
                )
            ) continue
            invalidateManagedCopy(trackId, record.localPath)
            if (sources.any { it.type == TrackSourceType.TELEGRAM_REMOTE }) pending += trackId
        }
        if (pending.isEmpty()) return
        telegramRepository.musicSourceState.first { it.accountId != null }
        while (TdLibClientAdapter.activeOrNull() == null) delay(1_000L)
        pending.forEach(::pinOffline)
    }

    private suspend fun invalidateManagedCopy(trackId: UUID, path: String?) {
        val now = Instant.now().toEpochMilli()
        libraryDao.sourcesForTrack(trackId.toString())
            .filter {
                (it.type == TrackSourceType.APP_OFFLINE_COPY || it.type == TrackSourceType.TDLIB_LOCAL) &&
                    it.localPath == path
            }
            .forEach { libraryDao.updateAvailability(it.id, SourceAvailability.MISSING, now) }
        val record = downloadDao.byTrackId(trackId.toString())
        if (record != null && record.localPath == path) {
            downloadDao.upsert(
                record.copy(
                    status = DownloadStatus.FAILED.name,
                    downloadedBytes = 0L,
                    localPath = null,
                    failureReason = "Offline audio was incomplete or corrupted; re-download required.",
                    updatedAtEpochMs = now,
                ),
            )
        }
        if (path != null) runCatching { deleteOwnedManagedFile(path) }
    }

    override fun observeDownloads(): Flow<List<OfflineDownload>> = downloadDao.observeAll().map { rows ->
        rows.map { row ->
            OfflineDownload(
                trackId = UUID.fromString(row.trackId),
                status = DownloadStatus.valueOf(row.status),
                downloadedBytes = row.downloadedBytes,
                totalBytes = row.totalBytes,
                pinned = row.pinned,
                failureReason = row.failureReason,
                updatedAtEpochMs = row.updatedAtEpochMs,
                hasManagedCopy = !row.localPath.isNullOrBlank(),
            )
        }
    }

    override fun pinOffline(trackId: UUID) {
        if (jobs[trackId]?.isActive == true) return
        pauseRequests.remove(trackId)
        jobs[trackId] = scope.launch {
            try {
                download(trackId)
            } catch (cancelled: CancellationException) {
                if (pauseRequests.remove(trackId)) {
                    markPaused(trackId)
                } else {
                    markCanceled(trackId)
                }
                throw cancelled
            } catch (error: Throwable) {
                markFailed(trackId, error.message ?: "Download failed.")
            } finally {
                jobs.remove(trackId)
            }
        }
    }

    override fun pause(trackId: UUID) {
        pauseRequests.add(trackId)
        jobs.remove(trackId)?.cancel(CancellationException("Download paused"))
        scope.launch {
            downloadDao.byTrackId(trackId.toString())?.tdFileId?.let { fileId ->
                runCatching { TdLibClientAdapter.activeOrNull()?.send(TdApi.CancelDownloadFile(fileId, false)) }
            }
            markPaused(trackId)
        }
    }

    override fun retry(trackId: UUID) = pinOffline(trackId)

    override fun cancel(trackId: UUID) {
        pauseRequests.remove(trackId)
        jobs.remove(trackId)?.cancel()
        scope.launch {
            downloadDao.byTrackId(trackId.toString())?.tdFileId?.let { fileId ->
                runCatching { TdLibClientAdapter.activeOrNull()?.send(TdApi.CancelDownloadFile(fileId, false)) }
            }
            markCanceled(trackId)
        }
    }

    override suspend fun removeOfflineCopy(trackId: UUID) {
        pauseRequests.remove(trackId)
        jobs.remove(trackId)?.cancelAndJoin()
        val record = downloadDao.byTrackId(trackId.toString())
        val managedSources = libraryDao.sourcesForTrack(trackId.toString())
            .filter { it.type == TrackSourceType.APP_OFFLINE_COPY || it.type == TrackSourceType.TDLIB_LOCAL }
        val paths = (managedSources.mapNotNull(TrackSourceEntity::localPath) +
            listOfNotNull(record?.localPath)).distinct()
        // This only deletes copies in Meowzix storage, never device music or Telegram data.
        paths.forEach(::deleteOwnedManagedFile)
        database.withTransaction {
            managedSources.forEach { source ->
                if (source.type == TrackSourceType.APP_OFFLINE_COPY) {
                    libraryDao.deleteOfflineSource(source.id)
                } else {
                    libraryDao.updateAvailability(source.id, SourceAvailability.MISSING, Instant.now().toEpochMilli())
                }
            }
            downloadDao.deleteByTrackId(trackId.toString())
        }
    }

    private fun deleteOwnedManagedFile(path: String) {
        val file = File(path).canonicalFile
        val parent = file.parentFile
        val offlineRoot = offlineDirectory.canonicalFile
        val cacheRoot = File(context.filesDir, "playback-cache").canonicalFile
        if (parent != offlineRoot && parent != cacheRoot) return
        if (file.exists() && !file.delete()) error("Unable to remove downloaded audio: ${file.name}")
    }

    override suspend fun storageBytes(): Long = managedStorage.usage().totalManagedBytes

    override suspend fun clearTemporaryCache() {
        managedStorage.clearTemporaryCache()
    }

    private suspend fun download(trackId: UUID) {
        if (reuseExistingLocalCopy(trackId)) return

        val settings = settingsRepository.networkPlaybackSettings.first()
        networkPolicy.blockReason(settings, NetworkUse.USER_REQUEST)?.let(::error)
        val accountId = telegramRepository.musicSourceState.value.accountId
            ?: error("Connect Telegram before downloading.")
        val telegramSource = telegramDao.telegramSourceForTrack(accountId, trackId.toString())
            ?: return
        val client = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not ready yet.")
        val remoteSource = requireNotNull(libraryDao.sourceById(telegramSource.trackSourceId))

        val refreshedMessage = client.send(TdApi.GetMessage(telegramSource.chatId, telegramSource.messageId))
        val candidate = refreshedMessage.toAudioCandidate()
        if (candidate == null) {
            libraryDao.updateAvailability(
                telegramSource.trackSourceId,
                SourceAvailability.MISSING,
                Instant.now().toEpochMilli(),
            )
            error("Telegram file is no longer available.")
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

        val fileId = candidate.fileId
        val existing = downloadDao.byTrackId(trackId.toString())
        val now = Instant.now().toEpochMilli()
        val initial = DownloadRecordEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            trackId = trackId.toString(),
            trackSourceId = telegramSource.trackSourceId,
            tdFileId = fileId,
            status = DownloadStatus.DOWNLOADING.name,
            downloadedBytes = existing?.downloadedBytes ?: 0L,
            totalBytes = candidate.fileSizeBytes ?: remoteSource.fileSizeBytes ?: existing?.totalBytes,
            localPath = existing?.localPath,
            pinned = true,
            failureReason = null,
            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )
        downloadDao.upsert(initial)

        val progressJob = scope.launch {
            client.updates
                .filterIsInstance<TdApi.UpdateFile>()
                .filter { it.file.id == fileId }
                .collect { update ->
                    val current = downloadDao.byTrackId(trackId.toString()) ?: return@collect
                    downloadDao.upsert(
                        current.copy(
                            downloadedBytes = update.file.local.downloadedSize,
                            totalBytes = update.file.size.toLong().takeIf { it > 0L } ?: current.totalBytes,
                            updatedAtEpochMs = Instant.now().toEpochMilli(),
                        ),
                    )
                }
        }
        val downloaded = try {
            var result = client.send(TdApi.DownloadFile(fileId, PIN_PRIORITY, 0L, 0L, true))
            fun valid(file: TdApi.File): Boolean = file.local.isDownloadingCompleted &&
                ManagedAudioIntegrity.isValid(
                    file.local.path.takeIf(String::isNotBlank)?.let(::File),
                    ManagedAudioIntegrity.expectedBytes(
                        candidate.fileSizeBytes, file.size.toLong(), remoteSource.fileSizeBytes,
                    ),
                )
            if (!valid(result) && result.local.isDownloadingCompleted) {
                // Only discard a falsely completed cache. An active partial stream is useful
                // to Media3 and must not be deleted if a download merely stopped early.
                client.send(TdApi.DeleteFile(fileId))
                result = client.send(TdApi.DownloadFile(fileId, PIN_PRIORITY, 0L, 0L, true))
            }
            if (!valid(result)) error("Telegram audio is still incomplete after a fresh download.")
            result
        } finally {
            progressJob.cancel()
        }
        val sourceFile = File(downloaded.local.path)
        val expectedBytes = ManagedAudioIntegrity.expectedBytes(
            candidate.fileSizeBytes, downloaded.size.toLong(), remoteSource.fileSizeBytes,
        )
        val localCopy = copyIntoOfflineDirectory(trackId, sourceFile, candidate.fileName)
        if (!ManagedAudioIntegrity.isValid(localCopy.file, expectedBytes, localCopy.sha256)) {
            runCatching { deleteOwnedManagedFile(localCopy.file.absolutePath) }
            error("Offline copy failed post-copy integrity verification.")
        }
        val completedAt = Instant.now().toEpochMilli()
        database.withTransaction {
            val previousOfflineSource = libraryDao.sourceById(offlineSourceId(trackId))
            libraryDao.upsertSource(
                TrackSourceEntity(
                    id = offlineSourceId(trackId),
                    trackId = trackId.toString(),
                    type = TrackSourceType.APP_OFFLINE_COPY,
                    availability = SourceAvailability.AVAILABLE_LOCAL,
                    contentUri = null,
                    localPath = localCopy.file.absolutePath,
                    mimeType = candidate.mimeType ?: remoteSource.mimeType,
                    fileSizeBytes = localCopy.file.length(),
                    contentHashSha256 = localCopy.sha256,
                    trainingEligible = remoteSource.trainingEligible,
                    createdAtEpochMs = previousOfflineSource?.createdAtEpochMs ?: completedAt,
                    lastVerifiedAtEpochMs = completedAt,
                ),
            )
            downloadDao.upsert(
                initial.copy(
                    tdFileId = downloaded.id,
                    status = DownloadStatus.COMPLETED.name,
                    downloadedBytes = localCopy.file.length(),
                    totalBytes = localCopy.file.length(),
                    localPath = localCopy.file.absolutePath,
                    updatedAtEpochMs = completedAt,
                ),
            )
        }
    }

    private suspend fun reuseExistingLocalCopy(trackId: UUID): Boolean {
        val existing = downloadDao.byTrackId(trackId.toString())
        val allSources = libraryDao.sourcesForTrack(trackId.toString())
        val telegramBytes = allSources.firstOrNull {
            it.type == TrackSourceType.TELEGRAM_REMOTE
        }?.fileSizeBytes
        val existingFile = existing?.localPath?.let(::File)
        val persistedCopy = allSources.firstOrNull {
            it.type == TrackSourceType.APP_OFFLINE_COPY && it.localPath == existing?.localPath
        }
        if (existing?.status == DownloadStatus.COMPLETED.name && existingFile != null &&
            !ManagedAudioIntegrity.isValid(
                existingFile,
                ManagedAudioIntegrity.expectedBytes(telegramBytes, existing.totalBytes, persistedCopy?.fileSizeBytes),
                persistedCopy?.contentHashSha256,
            )
        ) {
            invalidateManagedCopy(trackId, existing.localPath)
        } else if (existing?.status == DownloadStatus.COMPLETED.name && existingFile != null) {
            if (!existing.pinned || existing.failureReason != null) {
                downloadDao.upsert(
                    existing.copy(
                        pinned = true,
                        failureReason = null,
                        downloadedBytes = existingFile.length(),
                        totalBytes = existingFile.length(),
                        updatedAtEpochMs = Instant.now().toEpochMilli(),
                    ),
                )
            }
            return true
        }

        val localSources = libraryDao.sourcesForTrack(trackId.toString())
            .filter { it.availability == SourceAvailability.AVAILABLE_LOCAL }
            .sortedBy(::localReusePriority)

        for (source in localSources) {
            when {
                source.type == TrackSourceType.APP_OFFLINE_COPY && !source.localPath.isNullOrBlank() -> {
                    val file = source.localPath?.let(::File)
                    if (!ManagedAudioIntegrity.isValid(
                        file, ManagedAudioIntegrity.expectedBytes(telegramBytes, source.fileSizeBytes),
                        source.contentHashSha256,
                    )) {
                        invalidateManagedCopy(trackId, source.localPath)
                        continue
                    }
                    markExistingSourceCompleted(trackId, source, requireNotNull(file), existing)
                    return true
                }
                source.type == TrackSourceType.TDLIB_LOCAL && !source.localPath.isNullOrBlank() -> {
                    val file = source.localPath?.let(::File)
                    if (!ManagedAudioIntegrity.isValid(
                        file, ManagedAudioIntegrity.expectedBytes(telegramBytes, source.fileSizeBytes),
                        source.contentHashSha256,
                    )) {
                        invalidateManagedCopy(trackId, source.localPath)
                        continue
                    }
                    val localCopy = copyIntoOfflineDirectory(trackId, requireNotNull(file), file.name)
                    val now = Instant.now().toEpochMilli()
                    val previousOfflineSource = libraryDao.sourceById(offlineSourceId(trackId))
                    database.withTransaction {
                        libraryDao.upsertSource(
                            TrackSourceEntity(
                                id = offlineSourceId(trackId),
                                trackId = trackId.toString(),
                                type = TrackSourceType.APP_OFFLINE_COPY,
                                availability = SourceAvailability.AVAILABLE_LOCAL,
                                contentUri = null,
                                localPath = localCopy.file.absolutePath,
                                mimeType = source.mimeType,
                                fileSizeBytes = localCopy.file.length(),
                                contentHashSha256 = localCopy.sha256,
                                trainingEligible = source.trainingEligible,
                                createdAtEpochMs = previousOfflineSource?.createdAtEpochMs ?: now,
                                lastVerifiedAtEpochMs = now,
                            ),
                        )
                        downloadDao.upsert(
                            completedRecord(trackId, source.id, localCopy.file, existing, now),
                        )
                    }
                    return true
                }
                source.type == TrackSourceType.LOCAL_MEDIASTORE && !source.contentUri.isNullOrBlank() -> {
                    val now = Instant.now().toEpochMilli()
                    downloadDao.upsert(
                        DownloadRecordEntity(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            trackId = trackId.toString(),
                            trackSourceId = source.id,
                            tdFileId = existing?.tdFileId,
                            status = DownloadStatus.COMPLETED.name,
                            downloadedBytes = source.fileSizeBytes ?: 0L,
                            totalBytes = source.fileSizeBytes,
                            localPath = null,
                            pinned = true,
                            failureReason = null,
                            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                            updatedAtEpochMs = now,
                        ),
                    )
                    return true
                }
            }
        }
        return false
    }

    private suspend fun markExistingSourceCompleted(
        trackId: UUID,
        source: TrackSourceEntity,
        file: File,
        existing: DownloadRecordEntity?,
    ) {
        val now = Instant.now().toEpochMilli()
        if (source.contentHashSha256.isNullOrBlank()) {
            libraryDao.updateContentHash(source.id, ManagedAudioIntegrity.sha256(file))
        }
        downloadDao.upsert(completedRecord(trackId, source.id, file, existing, now))
    }

    private fun completedRecord(
        trackId: UUID,
        trackSourceId: String,
        file: File,
        existing: DownloadRecordEntity?,
        now: Long,
    ) = DownloadRecordEntity(
        id = existing?.id ?: UUID.randomUUID().toString(),
        trackId = trackId.toString(),
        trackSourceId = trackSourceId,
        tdFileId = existing?.tdFileId,
        status = DownloadStatus.COMPLETED.name,
        downloadedBytes = file.length(),
        totalBytes = file.length(),
        localPath = file.absolutePath,
        pinned = true,
        failureReason = null,
        createdAtEpochMs = existing?.createdAtEpochMs ?: now,
        updatedAtEpochMs = now,
    )

    private fun copyIntoOfflineDirectory(trackId: UUID, sourceFile: File, fileName: String?): LocalCopy {
        offlineDirectory.mkdirs()
        val extension = fileName?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
        val destination = File(offlineDirectory, trackId.toString() + extension?.let { ".$it" }.orEmpty())
        if (destination.isFile && destination.length() == sourceFile.length() &&
            sha256(destination) == sha256(sourceFile)) {
            return LocalCopy(destination, sha256(destination))
        }
        val partial = File(offlineDirectory, destination.name + ".part")
        val lease = managedStorage.acquireLease(partial.absolutePath, "offline-copy")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            sourceFile.inputStream().buffered().use { input ->
                partial.outputStream().buffered().use { output ->
                    DigestOutputStream(output, digest).use(input::copyTo)
                }
            }
            if (destination.exists() && !destination.delete()) error("Unable to replace offline copy.")
            if (!partial.renameTo(destination)) error("Unable to finalize offline copy.")
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            return LocalCopy(destination, hash)
        } finally {
            lease.close()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun markPaused(trackId: UUID) = updateStatus(trackId, DownloadStatus.PAUSED, null)

    private suspend fun markCanceled(trackId: UUID) = updateStatus(trackId, DownloadStatus.CANCELED, null)

    private suspend fun markFailed(trackId: UUID, reason: String) = updateStatus(trackId, DownloadStatus.FAILED, reason)

    private suspend fun updateStatus(trackId: UUID, status: DownloadStatus, reason: String?) {
        val current = downloadDao.byTrackId(trackId.toString()) ?: return
        downloadDao.upsert(
            current.copy(
                status = status.name,
                failureReason = reason,
                updatedAtEpochMs = Instant.now().toEpochMilli(),
            ),
        )
    }

    private fun deleteOwnedOfflineFile(path: String) {
        val root = offlineDirectory.canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == root && file.isFile) file.delete()
    }

    private fun offlineSourceId(trackId: UUID): String = UUID.nameUUIDFromBytes(
        "app-offline:$trackId".toByteArray(),
    ).toString()

    private data class LocalCopy(val file: File, val sha256: String)

    private companion object {
        // Leave 31/32 for current playback and seek so explicit/bulk pinning cannot starve audio.
        const val PIN_PRIORITY = 30
    }
}

private fun localReusePriority(source: TrackSourceEntity): Int = when (source.type) {
    TrackSourceType.APP_OFFLINE_COPY -> 0
    TrackSourceType.TDLIB_LOCAL -> 1
    TrackSourceType.LOCAL_MEDIASTORE -> 2
    else -> 3
}

internal object CacheEvictionPolicy {
    fun evictable(records: List<DownloadRecordEntity>): List<DownloadRecordEntity> =
        records.filterNot { it.pinned }
}
