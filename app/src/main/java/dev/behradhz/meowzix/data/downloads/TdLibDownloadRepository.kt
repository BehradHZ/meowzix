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
import kotlinx.coroutines.launch
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
) : DownloadRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<UUID, Job>()
    private val offlineDirectory = File(context.filesDir, "offline")

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
            )
        }
    }

    override fun pinOffline(trackId: UUID) {
        if (jobs[trackId]?.isActive == true) return
        jobs[trackId] = scope.launch {
            try {
                download(trackId)
            } catch (cancelled: CancellationException) {
                markCanceled(trackId)
                throw cancelled
            } catch (error: Throwable) {
                markFailed(trackId, error.message ?: "Download failed.")
            } finally {
                jobs.remove(trackId)
            }
        }
    }

    override fun retry(trackId: UUID) = pinOffline(trackId)

    override fun cancel(trackId: UUID) {
        jobs.remove(trackId)?.cancel()
        scope.launch {
            downloadDao.byTrackId(trackId.toString())?.tdFileId?.let { fileId ->
                runCatching { TdLibClientAdapter.activeOrNull()?.send(TdApi.CancelDownloadFile(fileId, false)) }
            }
            markCanceled(trackId)
        }
    }

    override suspend fun removeOfflineCopy(trackId: UUID) {
        jobs.remove(trackId)?.cancel()
        val record = downloadDao.byTrackId(trackId.toString()) ?: return
        record.localPath?.let(::deleteOwnedOfflineFile)
        database.withTransaction {
            libraryDao.deleteOfflineSource(offlineSourceId(trackId))
            downloadDao.deleteByTrackId(trackId.toString())
        }
    }

    override suspend fun storageBytes(): Long = downloadDao.all()
        .mapNotNull { it.localPath }
        .map(::File)
        .filter(File::isFile)
        .sumOf(File::length)

    override suspend fun clearTemporaryCache() {
        CacheEvictionPolicy.evictable(downloadDao.all()).forEach { record ->
            removeOfflineCopy(UUID.fromString(record.trackId))
        }
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
            downloadedBytes = 0L,
            totalBytes = candidate.fileSizeBytes ?: remoteSource.fileSizeBytes,
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
            client.send(TdApi.DownloadFile(fileId, PIN_PRIORITY, 0L, 0L, true))
        } finally {
            progressJob.cancel()
        }
        val tdPath = downloaded.local.path.takeIf { downloaded.local.isDownloadingCompleted && it.isNotBlank() }
            ?: error("Telegram download did not complete.")
        val sourceFile = File(tdPath).takeIf(File::isFile) ?: error("Downloaded file is missing.")

        val localCopy = copyIntoOfflineDirectory(trackId, sourceFile, candidate.fileName)
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
        val existingFile = existing?.localPath?.let(::File)?.takeIf(File::isFile)
        if (existing?.status == DownloadStatus.COMPLETED.name && existingFile != null) {
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
                    val file = source.localPath?.let(::File)?.takeIf(File::isFile) ?: continue
                    markExistingSourceCompleted(trackId, source, file, existing)
                    return true
                }
                source.type == TrackSourceType.TDLIB_LOCAL && !source.localPath.isNullOrBlank() -> {
                    val file = source.localPath?.let(::File)?.takeIf(File::isFile) ?: continue
                    val localCopy = copyIntoOfflineDirectory(trackId, file, file.name)
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
        if (destination.isFile && destination.length() == sourceFile.length()) {
            return LocalCopy(destination, sha256(destination))
        }
        val partial = File(offlineDirectory, destination.name + ".part")
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
        const val PIN_PRIORITY = 32
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
