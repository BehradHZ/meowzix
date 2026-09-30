package dev.behradhz.meowzix.data.downloads

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.behradhz.meowzix.data.db.DownloadDao
import dev.behradhz.meowzix.domain.downloads.DownloadRepository
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps user-requested offline downloads alive across transient network failures and process death.
 *
 * TdLibDownloadRepository remains the component that performs the actual TDLib transfer. This
 * wrapper adds durable WorkManager recovery around it. A pinned download is retried until it
 * completes, is explicitly canceled/removed, or fails for a known permanent reason.
 */
@Singleton
class ResilientDownloadRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val delegate: TdLibDownloadRepository,
    private val downloadDao: DownloadDao,
    private val settingsRepository: SettingsRepository,
) : DownloadRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private val workManager by lazy { WorkManager.getInstance(context) }

    fun initialize() {
        if (!initialized.compareAndSet(false, true)) return

        // Recover work that was left QUEUED/DOWNLOADING (or transiently FAILED) if the process died.
        scope.launch {
            downloadDao.all()
                .filter(::shouldRecover)
                .forEach { record ->
                    val trackId = runCatching { UUID.fromString(record.trackId) }.getOrNull()
                        ?: return@forEach
                    if (record.status == DownloadStatus.DOWNLOADING.name) {
                        markQueued(trackId, record.failureReason)
                    }
                    enqueueRecovery(trackId)
                }
        }

        // Convert transient failures back into a durable waiting state and make sure recovery work
        // exists. Permanent failures remain FAILED so the UI can surface them normally.
        scope.launch {
            delegate.observeDownloads().collectLatest { downloads ->
                downloads
                    .filter {
                        it.pinned &&
                            it.status == DownloadStatus.FAILED &&
                            isRetryableFailure(it.failureReason)
                    }
                    .forEach { download ->
                        markQueued(download.trackId, download.failureReason)
                        enqueueRecovery(download.trackId)
                    }
            }
        }
    }

    override fun observeDownloads(): Flow<List<OfflineDownload>> = delegate.observeDownloads()

    override fun pinOffline(trackId: UUID) {
        delegate.pinOffline(trackId)
        scope.launch { enqueueRecovery(trackId) }
    }

    override fun retry(trackId: UUID) {
        scope.launch { markQueued(trackId, null) }
        delegate.retry(trackId)
        scope.launch { enqueueRecovery(trackId) }
    }

    override fun cancel(trackId: UUID) {
        workManager.cancelUniqueWork(workName(trackId))
        delegate.cancel(trackId)
    }

    override suspend fun removeOfflineCopy(trackId: UUID) {
        workManager.cancelUniqueWork(workName(trackId))
        delegate.removeOfflineCopy(trackId)
    }

    override suspend fun storageBytes(): Long = delegate.storageBytes()

    override suspend fun clearTemporaryCache() = delegate.clearTemporaryCache()

    internal suspend fun runPersistentAttempt(trackId: UUID): PersistentAttemptResult {
        val before = downloadDao.byTrackId(trackId.toString())
        when {
            before?.status == DownloadStatus.COMPLETED.name -> return PersistentAttemptResult.SUCCESS
            before?.status == DownloadStatus.CANCELED.name -> return PersistentAttemptResult.SUCCESS
            before != null && !before.pinned -> return PersistentAttemptResult.SUCCESS
            before?.status == DownloadStatus.FAILED.name && !isRetryableFailure(before.failureReason) -> {
                return PersistentAttemptResult.FAILURE
            }
        }

        // retry() is safe even if the foreground attempt is still running: the TDLib repository
        // deduplicates active jobs per track. Keeping this Worker alive also keeps the process alive
        // while the transfer is active.
        delegate.retry(trackId)

        val startedAt = System.currentTimeMillis()
        var sawDownloading = before?.status == DownloadStatus.DOWNLOADING.name
        while (currentCoroutineContext().isActive) {
            delay(POLL_INTERVAL_MS)
            val current = downloadDao.byTrackId(trackId.toString())
            when (current?.status) {
                DownloadStatus.COMPLETED.name,
                DownloadStatus.CANCELED.name,
                -> return PersistentAttemptResult.SUCCESS

                DownloadStatus.DOWNLOADING.name -> sawDownloading = true

                DownloadStatus.FAILED.name -> {
                    return if (isRetryableFailure(current.failureReason)) {
                        markQueued(trackId, current.failureReason)
                        PersistentAttemptResult.RETRY
                    } else {
                        PersistentAttemptResult.FAILURE
                    }
                }

                DownloadStatus.QUEUED.name -> {
                    // A transient failure observer moves FAILED -> QUEUED. Once an attempt has had
                    // enough time to start, QUEUED means "wait for the next constrained retry".
                    if (System.currentTimeMillis() - startedAt >= QUEUED_SETTLE_MS) {
                        return PersistentAttemptResult.RETRY
                    }
                }

                null -> {
                    if (System.currentTimeMillis() - startedAt >= RECORD_SETTLE_MS) {
                        return PersistentAttemptResult.RETRY
                    }
                }
            }

            // Do not occupy a normal Worker indefinitely for a very slow/stalled transfer. Returning
            // retry preserves the durable request; an already-active TDLib job is deduplicated.
            if (System.currentTimeMillis() - startedAt >= WORKER_WATCHDOG_MS) {
                return PersistentAttemptResult.RETRY
            }

            // Keep the variable meaningful for future status extensions and document that a worker
            // which has observed active transfer progress must not treat a short QUEUED race as fatal.
            if (sawDownloading) Unit
        }
        return PersistentAttemptResult.RETRY
    }

    private suspend fun enqueueRecovery(trackId: UUID) {
        val settings = settingsRepository.networkPlaybackSettings.first()
        val networkType = if (settings.wifiOnlyDownloads) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    .build(),
            )
            .setInputData(workDataOf(OfflineDownloadWorker.KEY_TRACK_ID to trackId.toString()))
            .setInitialDelay(750, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                androidx.work.WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS,
            )
            .build()

        workManager.enqueueUniqueWork(
            workName(trackId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private suspend fun markQueued(trackId: UUID, reason: String?) {
        val current = downloadDao.byTrackId(trackId.toString()) ?: return
        if (current.status == DownloadStatus.COMPLETED.name || current.status == DownloadStatus.CANCELED.name) {
            return
        }
        downloadDao.upsert(
            current.copy(
                status = DownloadStatus.QUEUED.name,
                failureReason = reason,
                updatedAtEpochMs = Instant.now().toEpochMilli(),
            ),
        )
    }

    private fun shouldRecover(record: dev.behradhz.meowzix.data.db.DownloadRecordEntity): Boolean {
        if (!record.pinned) return false
        return when (record.status) {
            DownloadStatus.QUEUED.name,
            DownloadStatus.DOWNLOADING.name,
            -> true
            DownloadStatus.FAILED.name -> isRetryableFailure(record.failureReason)
            else -> false
        }
    }

    private fun isRetryableFailure(reason: String?): Boolean {
        if (reason.isNullOrBlank()) return true
        return !PERMANENT_FAILURE_MARKERS.any { marker -> reason.contains(marker, ignoreCase = true) }
    }

    private fun workName(trackId: UUID): String = "offline-download-$trackId"

    private companion object {
        const val POLL_INTERVAL_MS = 750L
        const val QUEUED_SETTLE_MS = 10_000L
        const val RECORD_SETTLE_MS = 15_000L
        const val WORKER_WATCHDOG_MS = 8 * 60 * 1_000L
        val PERMANENT_FAILURE_MARKERS = listOf(
            "file is no longer available",
            "source is no longer available",
        )
    }
}

internal enum class PersistentAttemptResult { SUCCESS, RETRY, FAILURE }

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DownloadWorkerEntryPoint {
    fun resilientDownloadRepository(): ResilientDownloadRepository
}

class OfflineDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val trackId = inputData.getString(KEY_TRACK_ID)
            ?.let { runCatching(UUID::fromString).getOrNull() }
            ?: return Result.failure()
        val repository = EntryPointAccessors.fromApplication(
            applicationContext,
            DownloadWorkerEntryPoint::class.java,
        ).resilientDownloadRepository()

        return when (repository.runPersistentAttempt(trackId)) {
            PersistentAttemptResult.SUCCESS -> Result.success()
            PersistentAttemptResult.RETRY -> Result.retry()
            PersistentAttemptResult.FAILURE -> Result.failure()
        }
    }

    companion object {
        const val KEY_TRACK_ID = "track_id"
    }
}
