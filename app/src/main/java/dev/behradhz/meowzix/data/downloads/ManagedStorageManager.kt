package dev.behradhz.meowzix.data.downloads

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.DownloadDao
import dev.behradhz.meowzix.data.db.LibraryDao
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.ManagedFileLease
import dev.behradhz.meowzix.domain.downloads.ManagedStorageRepository
import dev.behradhz.meowzix.domain.downloads.ManagedStorageUsage
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class ManagedStorageManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val libraryDao: LibraryDao,
    private val settingsRepository: SettingsRepository,
    private val leases: ManagedFileLeaseRegistry,
    private val verifiedAudio: ManagedAudioIntegrityCache,
) : ManagedStorageRepository {
    private val playbackRoot = File(context.filesDir, "playback-cache")
    private val offlineRoot = File(context.filesDir, "offline")
    private val artworkRoot = File(context.filesDir, "artwork")

    override suspend fun usage(): ManagedStorageUsage = withContext(Dispatchers.IO) {
        calculateUsage()
    }

    // Legacy API name: completed audio is permanent, and no size budget may evict it.
    override suspend fun reconcileAndEnforceBudget(): ManagedStorageUsage = withContext(Dispatchers.IO) {
        reconcileSources()
        calculateUsage()
    }

    // Explicit cleanup only: never delete a completed audio file, even one from an older version.
    override suspend fun clearTemporaryCache(): ManagedStorageUsage = withContext(Dispatchers.IO) {
        listOf(playbackRoot, offlineRoot)
            .flatMap(::managedFiles)
            .filter { ManualStorageCleanupPolicy.canRemove(it.name, leases.isProtected(it.absolutePath)) }
            .forEach { file -> if (file.delete()) verifiedAudio.forget(file) }
        calculateUsage()
    }

    override fun acquireLease(path: String, owner: String): ManagedFileLease = leases.acquire(path, owner)

    private suspend fun calculateUsage(): ManagedStorageUsage {
        val seen = mutableSetOf<String>()
        fun uniqueBytes(files: List<File>): Long = files.sumOf { file ->
            val key = canonical(file)
            if (seen.add(key)) file.length().coerceAtLeast(0L) else 0L
        }

        // Any managed audio is retained audio, whether initiated by Play or Save. Count
        // legacy playback-cache copies and in-progress .part bytes as saved storage.
        // Only app-owned paths are counted; MediaStore audio is not managed storage.
        val pinnedPaths = downloadDao.all()
            .filter { it.pinned }
            .mapNotNull { it.localPath }
            .map(::File)
        val savedBytes = savedManagedAudioBytes(offlineRoot, playbackRoot, pinnedPaths)
        val otherBytes = uniqueBytes(managedFiles(artworkRoot))
        val protectedBytes = leases.protectedPaths().sumOf { path ->
            File(path).takeIf(File::isFile)?.length()?.coerceAtLeast(0L) ?: 0L
        }
        val budget = settingsRepository.storagePolicySettings.first().temporaryCacheBudgetBytes

        return ManagedStorageUsage(
            temporaryPlaybackBytes = 0L, // Legacy field: no audio is classified as temporary.
            pinnedOfflineBytes = savedBytes,
            otherManagedBytes = otherBytes,
            protectedBytes = protectedBytes,
            temporaryBudgetBytes = budget,
            lowSpace = isLowSpace(),
        )
    }

    private suspend fun reconcileSources() {
        val now = Instant.now().toEpochMilli()
        val sources = libraryDao.allSources()
        val remoteSizes = sources
            .filter { it.type == TrackSourceType.TELEGRAM_REMOTE }
            .groupBy({ it.trackId }, { it.fileSizeBytes })
        sources
            .filter { it.type == TrackSourceType.TDLIB_LOCAL || it.type == TrackSourceType.APP_OFFLINE_COPY }
            .filter { it.availability == SourceAvailability.AVAILABLE_LOCAL }
            .sortedBy { if (it.type == TrackSourceType.APP_OFFLINE_COPY) 0 else 1 }
            .forEach { managed ->
                val file = managed.localPath?.let(::File)
                if (verifiedAudio.isValidForAnyRemoteSize(
                        file, remoteSizes[managed.trackId].orEmpty(),
                        managed.fileSizeBytes, managed.contentHashSha256,
                    )
                ) {
                    // Automatically adopt completed audio from older app versions.
                    if (file != null && (isOwnedBy(file, playbackRoot) || isOwnedBy(file, offlineRoot))) {
                        val existing = downloadDao.byTrackId(managed.trackId)
                        val alreadyRetained = existing?.pinned == true &&
                            existing.status == DownloadStatus.COMPLETED.name &&
                            existing.localPath?.let(::File)?.isFile == true
                        if (!alreadyRetained) {
                            downloadDao.upsert(
                                PermanentAudioRecordFactory.completed(
                                    trackId = managed.trackId,
                                    sourceId = managed.id,
                                    tdFileId = existing?.tdFileId,
                                    file = file,
                                    timestampMs = now,
                                    existing = existing,
                                ),
                            )
                        }
                    }
                    return@forEach
                }
                // Unavailable copies must not be used for playback, but physical files are never auto-deleted.
                file?.let(verifiedAudio::forget)
                libraryDao.updateAvailability(managed.id, SourceAvailability.MISSING, now)
            }
    }

    private fun managedFiles(root: File): List<File> = root.listFiles()
        ?.asSequence()
        ?.filter(File::isFile)
        ?.toList()
        .orEmpty()

    private fun isLowSpace(): Boolean {
        val stats = StatFs(context.filesDir.absolutePath)
        val threshold = maxOf(LOW_SPACE_MIN_BYTES, (stats.totalBytes * LOW_SPACE_PERCENT) / 100L)
        return stats.availableBytes < threshold
    }

    private fun isOwnedBy(file: File, root: File): Boolean = runCatching {
        file.canonicalFile.parentFile == root.canonicalFile
    }.getOrDefault(false)

    private fun canonical(file: File): String = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }

    private companion object {
        const val LOW_SPACE_PERCENT = 5L
        const val LOW_SPACE_MIN_BYTES = 256L * 1024L * 1024L
    }
}

/**
 * Retain and account for any app-managed audio (complete or in progress) as Saved.
 * Dedupe the same physical file across directories and records; exclude MediaStore paths.
 */
internal fun savedManagedAudioBytes(
    offlineRoot: File,
    playbackRoot: File,
    trackedPaths: List<File>,
): Long {
    val roots = listOf(offlineRoot.canonicalFile, playbackRoot.canonicalFile)
    return (roots.flatMap { root -> root.listFiles()?.filter(File::isFile).orEmpty() } + trackedPaths)
        .asSequence()
        .mapNotNull { file -> runCatching { file.canonicalFile }.getOrNull() }
        .filter { file -> file.isFile && file.parentFile in roots }
        .distinctBy(File::getPath)
        .sumOf { file -> file.length().coerceAtLeast(0L) }
}
