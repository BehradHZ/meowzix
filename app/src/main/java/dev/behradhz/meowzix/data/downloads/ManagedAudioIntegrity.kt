package dev.behradhz.meowzix.data.downloads

import java.io.File
import java.security.MessageDigest

/**
 * Trust managed audio only after validating its physical bytes against independent metadata.
 * A digest computed from the same truncated download is NOT proof of completeness: callers
 * must also supply the Telegram-declared size when it is available.
 */
internal object ManagedAudioIntegrity {
    fun isValid(file: File?, expectedBytes: Long? = null, expectedSha256: String? = null): Boolean {
        if (file == null || !file.isFile || !file.canRead()) return false
        val length = file.length()
        if (length <= 0L) return false
        if (expectedBytes != null && expectedBytes > 0L && length != expectedBytes) return false
        if (!expectedSha256.isNullOrBlank()) {
            if (!expectedSha256.matches(Regex("[0-9a-fA-F]{64}"))) return false
            val actualHash = runCatching { sha256(file) }.getOrNull() ?: return false
            if (!actualHash.equals(expectedSha256, ignoreCase = true)) return false
        }
        return true
    }

    /**
     * A logical track can have several Telegram sources with different encodings/sizes.
     * A managed copy is acceptable if its length matches any known remote source.
     */
    fun isValidForAnyRemoteSize(
        file: File?,
        remoteSizes: Iterable<Long?>,
        storedBytes: Long? = null,
        expectedSha256: String? = null,
    ): Boolean {
        val knownSizes = remoteSizes.mapNotNull { it?.takeIf { size -> size > 0L } }.toSet()
        if (knownSizes.isNotEmpty() && file?.length() !in knownSizes) return false
        return isValid(file, if (knownSizes.isEmpty()) storedBytes else null, expectedSha256)
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Fresh Telegram metadata takes precedence over old cached lengths. */
    fun expectedBytes(vararg sizes: Long?): Long? = sizes.firstOrNull { it != null && it > 0L }
}
