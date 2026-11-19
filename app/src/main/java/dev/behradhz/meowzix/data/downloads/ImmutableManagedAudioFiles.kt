package dev.behradhz.meowzix.data.downloads

import java.io.File
import java.util.UUID

/**
 * Content-addressed destinations keep completed music immutable. A new version never
 * replaces (or unlinks) a previous downloaded audio file without explicit user action.
 */
internal object ImmutableManagedAudioFiles {
    fun preferredDestination(
        directory: File,
        trackId: UUID,
        originalName: String?,
        sha256: String,
    ): File {
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Expected a lowercase SHA-256 digest"
        }
        val extension = originalName?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
            ?.let { ".$it" }
            .orEmpty()
        return File(directory, "$trackId-$sha256$extension")
    }

    /** A corrupt or conflicting destination must be preserved, not silently deleted. */
    fun alternateDestination(occupied: File): File {
        val extension = occupied.extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()
        val stem = occupied.name.removeSuffix(extension)
        return File(occupied.parentFile, "$stem-${UUID.randomUUID()}$extension")
    }
}
