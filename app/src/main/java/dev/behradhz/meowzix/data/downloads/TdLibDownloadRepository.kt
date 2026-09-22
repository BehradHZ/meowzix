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
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.telegram.TelegramAuthStep
import dev.behradhz.meowzix.domain.telegram.TelegramRepository
import java.io.File
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val recoveryStarted = AtomicBoolean(false)
    private val offlineDirectory = File(context.filesDir, "offline")

    init {
        // Construction itself never waits for Telegram/network. Once instantiated, recovery observes
        // auth state and resumes only after TDLib reaches Ready.
        scope.launch {
            telegramRepository.authState
                .map { it.step is TelegramAuthStep.Ready }
                .distinctUntilChanged()
                .collect { ready -> if (ready) resumeEligiblePersistedDownloads() }
        }
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

    override fun resumeInterruptedDownloads() {
        if (!recoveryStarted.compareAndSet(false, true)) return
        scope.launch {
            reconcilePersistedDownloadState()
            if (telegramRepository.authState.value.step is TelegramAuthStep.Ready) {
                resumeEligiblePersistedDownloads()
            }
        }
    }

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

    private suspend fun reconcilePersistedDownloadState() {
        offlineDirectory.mkdirs()
        offlineDirectory.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".part") }
            ?.forEach { runCatching { it.delete() } }

        val now = Instant.now().toEpochMilli()
        downloadDao.all().forEach { record ->
            val status = runCatching { DownloadStatus.valueOf(record.status) }.getOrNull() ?: return@forEach
            when (status) {
                DownloadStatus.DOWNLOADING -> {
                    // Process death cannot keep the in-memory job alive. Preserve progress metadata
                    // and move the durable record back to QUEUED for provider-ready recovery.
                    downloadDao.upsert(
                        record.copy(
                            status = DownloadStatus.QUEUED.name,
                            failureReason = null,
                            updatedAtEpochMs = now,
                        ),
                    )
                }
                DownloadStatus.COMPLETED -> {
                    val localFile = record.localPath?.let(::File)
                    if (localFile == null || !localFile.isFile) {
                        database.withTransaction {
                            libraryDao.deleteOfflineSource(offlineSourceId(UUID.fromString(record.trackId)))
                            downloadDao.upsert(
                                record.copy(
                                    status = DownloadStatus.FAILED.name,
                                    localPath = null,
                                    failureReason = "Offline copy is missing. Retry to download it again.",
                                    updatedAtEpochMs = now,
                                ),
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }

    private suspend fun resumeEligiblePersistedDownloads() {
        downloadDao.all()
            .asSequence()
            .filter { it.pinned }
            .filter {
                it.status == DownloadStatus.QUEUED.name || it.status == DownloadStatus.DOWNLOADING.name
            }
            .mapNotNull { runCatching { UUID.fromString(it.trackId) }.getOrNull() }
            .forEach(::pinOffline)
    }

    private suspend fun download(trackId: UUID) {
        val settings = settingsRepository.networkPlaybackSettings.first()
        networkPolicy.blockReason(settings, NetworkUse.USER_REQUEST)?.let(::error)
        val accountId = telegramRepository.musicSourceState.value.accountId
            ?: error("Connect Telegram before downloading.")
        val telegramSource = telegramDao.telegramSourceForTrack(accountId, trackId.toString())
            ?: error("This track no longer has an accessible Telegram source.")
        val client = TdLibClientAdapter.activeOrNull() ?: error("Telegram is not ready yet.")
        val remoteSource = requireNotNull(libraryDao.sourceById(telegramSource.trackSourceId))
        val existing = downloadDao.byTrackId(trackId.toString())

        ensureStorageCapacity(remoteSource.fileSizeBytes, copiesNeeded = 2)

        val now = Instant.now().toEpochMilli()
        val initial = DownloadRecordEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            trackId = trackId.toString(),
            trackSourceId = telegramSource.trackSourceId,
            tdFileId = telegramSource.tdFileId,
            status = DownloadStatus.DOWNLOADING.name,
            downloadedBytes = existing?.downloadedBytes ?: 0L,
            totalBytes = remoteSource.fileSizeBytes ?: existing?.totalBytes,
            localPath = existing?.localPath,
            pinned = true,
            failureReason = null,
            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )
        downloadDao.upsert(initial)

        val fileId = requireNotNull(telegramSource.tdFileId) { "Telegram file is unavailable." }
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
            // TDLib resumes its own partial file state when possible; the durable app record above
            // survives process death and is reconciled back into this request on next Ready state.
            client.send(TdApi.DownloadFile(fileId, PIN_PRIORITY, 0L, 0L, true))
        } finally {
            progressJob.cancel()
        }
        val tdPath = downloaded.local.path.takeIf { downloaded.local.isDownloadingCompleted && it.isNotBlank() }
            ?: error("Telegram download did not complete.")
        val sourceFile = File(tdPath).takeIf(File::isFile) ?: error("Downloaded file is missing.")

        ensureStorageCapacity(sourceFile.length(), copiesNeeded = 1)
        if (!offlineDirectory.exists() && !offlineDirectory.mkdirs()) error("Unable to create offline storage.")
        val extension = telegramSource.fileName?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
        val destination = File(offlineDirectory, trackId.toString() + extension?.let { ".$it" }.orEmpty())
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
                    localPath = destination.absolutePath,
                    mimeType = remoteSource.mimeType,
                    fileSizeBytes = destination.length(),
                    contentHashSha256 = hash,
                    trainingEligible = remoteSource.trainingEligible,
                    createdAtEpochMs = previousOfflineSource?.createdAtEpochMs ?: completedAt,
                    lastVerifiedAtEpochMs = completedAt,
                ),
            )
            downloadDao.upsert(
                initial.copy(
                    tdFileId = downloaded.id,
                    status = DownloadStatus.COMPLETED.name,
                    downloadedBytes = destination.length(),
                    totalBytes = destination.length(),
                    localPath = destination.absolutePath,
                    updatedAtEpochMs = completedAt,
                ),
            )
        }
    }

    private suspend fun ensureStorageCapacity(payloadBytes: Long?, copiesNeeded: Int) {
        val usable = context.filesDir.usableSpace
        if (StorageSafety.hasCapacity(usable, payloadBytes, copiesNeeded)) return
        // Only explicitly evictable/unpinned app-owned copies may be removed automatically.
        clearTemporaryCache()
        val refreshed = context.filesDir.usableSpace
        if (!StorageSafety.hasCapacity(refreshed, payloadBytes, copiesNeeded)) {
            error("Not enough storage to complete this download safely.")
        }
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

    private companion object {
        const val PIN_PRIORITY = 32
    }
}

internal object CacheEvictionPolicy {
    fun evictable(records: List<DownloadRecordEntity>): List<DownloadRecordEntity> =
        records.filterNot { it.pinned }
}

internal object StorageSafety {
    private const val MIN_FREE_BYTES = 64L * 1024L * 1024L

    fun hasCapacity(usableBytes: Long, payloadBytes: Long?, copiesNeeded: Int): Boolean =
        usableBytes >= requiredFreeBytes(payloadBytes, copiesNeeded)

    fun requiredFreeBytes(payloadBytes: Long?, copiesNeeded: Int): Long {
        val payload = payloadBytes?.coerceAtLeast(0L) ?: 0L
        val copies = copiesNeeded.coerceAtLeast(1).toLong()
        val payloadRequirement = if (payload == 0L) 0L else {
            val multiplied = if (payload > Long.MAX_VALUE / copies) Long.MAX_VALUE else payload * copies
            val overhead = payload / 10L
            if (multiplied > Long.MAX_VALUE - overhead) Long.MAX_VALUE else multiplied + overhead
        }
        return maxOf(MIN_FREE_BYTES, payloadRequirement)
    }
}
