package dev.behradhz.meowzix.data.downloads

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.behradhz.meowzix.core.model.SourceAvailability
import dev.behradhz.meowzix.core.model.TrackSourceType
import dev.behradhz.meowzix.data.db.DownloadDao
import dev.behradhz.meowzix.data.db.LibraryDao
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
) : ManagedStorageRepository {
    private val playbackRoot = File(context.filesDir, "playback-cache")
    private val offlineRoot = File(context.filesDir, "offline")
    private val artworkRoot = File(context.filesDir, "artwork")

    override suspend fun usage(): ManagedStorageUsage = withContext(Dispatchers.IO) {
        calculateUsage()
    }

    override suspend fun reconcileAndEnforceBudget(): ManagedStorageUsage = withContext(Dispatchers.IO) {
        reconcileSources()
        cleanInterruptedPartials()
        evictTemporaryCacheToBudget()
        calculateUsage()
    }

    override suspend fun clearTemporaryCache(): ManagedStorageUsage = withContext(Dispatchers.IO) {
        playbackFiles()
            .filterNot { leases.isProtected(it.absolutePath) }
            .forEach(::deleteTemporaryFile)
        calculateUsage()
    }

    override fun acquireLease(path: String, owner: String): ManagedFileLease = leases.acquire(path, owner)

    private suspend fun calculateUsage(): ManagedStorageUsage {
        val seen = mutableSetOf<String>()
        fun uniqueBytes(files: List<File>): Long = files.sumOf { file ->
            val key = canonical(file)
            if (seen.add(key)) file.length().coerceAtLeast(0L) else 0L
        }

        // Pinned user-requested copies own their category even if a future implementation hardlinks
        // them into another managed directory; the seen set prevents physical double counting.
        val pinnedPaths = downloadDao.all()
            .filter { it.pinned }
            .mapNotNull { it.localPath }
            .map(::File)
            .filter(File::isFile)
        val pinnedBytes = uniqueBytes(pinnedPaths)
        val temporaryBytes = uniqueBytes(playbackFiles())
        val otherBytes = uniqueBytes(managedFiles(artworkRoot))
        val protectedBytes = leases.protectedPaths().sumOf { path ->
            File(path).takeIf(File::isFile)?.length()?.coerceAtLeast(0L) ?: 0L
        }
        val budget = settingsRepository.storagePolicySettings.first().temporaryCacheBudgetBytes

        return ManagedStorageUsage(
            temporaryPlaybackBytes = temporaryBytes,
            pinnedOfflineBytes = pinnedBytes,
            otherManagedBytes = otherBytes,
            protectedBytes = protectedBytes,
            temporaryBudgetBytes = budget,
            lowSpace = isLowSpace(),
        )
    }

    private suspend fun reconcileSources() {
        val now = Instant.now().toEpochMilli()
        libraryDao.allSources()
            .filter { source ->
                source.type == TrackSourceType.TDLIB_LOCAL || source.type == TrackSourceType.APP_OFFLINE_COPY
            }
            .filter { source -> !source.localPath.isNullOrBlank() }
            .filter { source -> !File(requireNotNull(source.localPath)).isFile }
            .forEach { missing ->
                libraryDao.updateAvailability(missing.id, SourceAvailability.MISSING, now)
            }
    }

    private suspend fun evictTemporaryCacheToBudget() {
        val budget = settingsRepository.storagePolicySettings.first().temporaryCacheBudgetBytes
            .coerceAtLeast(MIN_CACHE_BUDGET_BYTES)
        var current = playbackFiles().sumOf(File::length)
        if (current <= budget) return

        playbackFiles()
            .filterNot { leases.isProtected(it.absolutePath) }
            .sortedBy(File::lastModified)
            .forEach { file ->
                if (current <= budget) return@forEach
                val length = file.length()
                if (deleteTemporaryFile(file)) current = (current - length).coerceAtLeast(0L)
            }
    }

    private fun deleteTemporaryFile(file: File): Boolean {
        if (!isOwnedBy(file, playbackRoot) || leases.isProtected(file.absolutePath)) return false
        return file.delete()
    }

    private fun cleanInterruptedPartials() {
        val cutoff = System.currentTimeMillis() - STALE_PARTIAL_AGE_MS
        listOf(playbackRoot, offlineRoot, artworkRoot)
            .flatMap(::managedFiles)
            .filter { it.name.endsWith(".part") && it.lastModified() < cutoff }
            .filterNot { leases.isProtected(it.absolutePath) }
            .forEach(File::delete)
    }

    private fun playbackFiles(): List<File> = managedFiles(playbackRoot)
        .filterNot { it.name.endsWith(".part") }

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
        const val MIN_CACHE_BUDGET_BYTES = 64L * 1024L * 1024L
        const val STALE_PARTIAL_AGE_MS = 24L * 60L * 60L * 1_000L
    }
}
