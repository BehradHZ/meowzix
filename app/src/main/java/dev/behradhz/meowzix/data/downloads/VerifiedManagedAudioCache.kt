package dev.behradhz.meowzix.data.downloads

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Avoids rehashing an already verified, unchanged managed file on every playback.
 * The verifier stamp is bound to the persisted digest, file size, modification time,
 * and filesystem identity. A legacy or changed file is checked with a full SHA-256.
 */
internal class VerifiedManagedAudioCache(
    private val read: (String) -> String?,
    private val write: (String, String) -> Unit,
    private val remove: (String) -> Unit,
    private val digest: (File) -> String = ManagedAudioIntegrity::sha256,
) {
    fun isValidForAnyRemoteSize(
        file: File?,
        remoteSizes: Iterable<Long?>,
        storedBytes: Long? = null,
        expectedSha256: String? = null,
    ): Boolean {
        // Expected remote sizes are authoritative even when a prior digest is cached.
        if (!ManagedAudioIntegrity.isValidForAnyRemoteSize(file, remoteSizes, storedBytes)) return false
        if (expectedSha256.isNullOrBlank()) return true
        val expected = expectedSha256.lowercase(Locale.ROOT)
        if (!SHA256_PATTERN.matches(expected)) return false

        val before = snapshot(file)
            ?: return ManagedAudioIntegrity.isValidForAnyRemoteSize(file, remoteSizes, storedBytes, expected)
        val stamp = marker(expected, before)
        if (before.fileIdentity.isNotBlank() && read(before.path) == stamp) return true

        val actual = runCatching { digest(requireNotNull(file)) }.getOrNull()
        val after = snapshot(file)
        if (actual == null || !actual.equals(expected, ignoreCase = true) || after != before) {
            remove(before.path)
            return false
        }
        // No reliable file identity means the cache cannot safely skip hashing next time.
        if (before.fileIdentity.isNotBlank()) write(before.path, stamp)
        return true
    }

    /** Use only after completed-copy size and content have both been verified. */
    fun rememberVerified(file: File, sha256: String) {
        val expected = sha256.lowercase(Locale.ROOT)
        if (!SHA256_PATTERN.matches(expected)) return
        val state = snapshot(file) ?: return
        if (state.fileIdentity.isNotBlank()) write(state.path, marker(expected, state))
    }

    fun forget(file: File) {
        remove(file.absolutePath)
    }

    private data class Snapshot(
        val path: String,
        val length: Long,
        val modifiedNanos: Long,
        val fileIdentity: String,
    )

    private fun snapshot(file: File?): Snapshot? = runCatching {
        if (file == null || !file.isFile || !file.canRead()) return null
        val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        if (!attrs.isRegularFile || attrs.size() <= 0L) return null
        Snapshot(
            file.absolutePath,
            attrs.size(),
            attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS),
            attrs.fileKey()?.toString().orEmpty(),
        )
    }.getOrNull()

    private fun marker(sha256: String, state: Snapshot): String =
        "$sha256|${state.length}|${state.modifiedNanos}|${state.fileIdentity}"

    private companion object {
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
