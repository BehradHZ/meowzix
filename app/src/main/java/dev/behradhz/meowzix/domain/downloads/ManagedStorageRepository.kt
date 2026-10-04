package dev.behradhz.meowzix.domain.downloads

import java.io.Closeable

/** App-owned bytes only. User-owned MediaStore audio is intentionally excluded. */
data class ManagedStorageUsage(
    val temporaryPlaybackBytes: Long,
    val pinnedOfflineBytes: Long,
    val otherManagedBytes: Long,
    val protectedBytes: Long,
    val temporaryBudgetBytes: Long,
    val lowSpace: Boolean,
) {
    val totalManagedBytes: Long
        get() = temporaryPlaybackBytes + pinnedOfflineBytes + otherManagedBytes
}

interface ManagedStorageRepository {
    suspend fun usage(): ManagedStorageUsage
    suspend fun reconcileAndEnforceBudget(): ManagedStorageUsage
    suspend fun clearTemporaryCache(): ManagedStorageUsage
    fun acquireLease(path: String, owner: String): ManagedFileLease
}

interface ManagedFileLease : Closeable {
    val path: String
    val owner: String
}
