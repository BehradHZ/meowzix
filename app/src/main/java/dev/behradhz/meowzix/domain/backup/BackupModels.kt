package dev.behradhz.meowzix.domain.backup

import java.util.UUID

data class PortableTrackRef(
    val originalTrackId: UUID?,
    val normalizedTitle: String,
    val normalizedArtist: String?,
    val durationMs: Long,
    val contentHashSha256: String?,
)

data class BackupOptions(val includeHistory: Boolean = false)

data class BackupPreview(
    val backupId: String,
    val version: Int,
    val createdAtEpochMs: Long,
    val includeHistory: Boolean,
    val recordCounts: Map<String, Int>,
    val unresolvedTrackReferences: Int = 0,
    val conflicts: Int = 0,
)

data class BackupRestoreResult(
    val restoredRecords: Int,
    val unresolvedReferences: Int,
    val skippedRecords: Int,
)

interface LocalBackupRepository {
    suspend fun export(options: BackupOptions = BackupOptions()): ByteArray
    suspend fun preview(bytes: ByteArray): BackupPreview
    suspend fun restore(bytes: ByteArray): BackupRestoreResult
}
