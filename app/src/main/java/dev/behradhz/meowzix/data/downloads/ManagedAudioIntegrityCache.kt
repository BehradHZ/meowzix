package dev.behradhz.meowzix.data.downloads

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Persists file verification fingerprints across app restarts. */
@Singleton
internal class ManagedAudioIntegrityCache @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("verified_managed_audio_v1", Context.MODE_PRIVATE)
    private val verifier = VerifiedManagedAudioCache(
        read = { path -> preferences.getString(path, null) },
        write = { path, stamp -> preferences.edit().putString(path, stamp).apply() },
        remove = { path -> preferences.edit().remove(path).apply() },
    )

    fun isValidForAnyRemoteSize(
        file: File?,
        remoteSizes: Iterable<Long?>,
        storedBytes: Long? = null,
        expectedSha256: String? = null,
    ): Boolean = verifier.isValidForAnyRemoteSize(file, remoteSizes, storedBytes, expectedSha256)

    fun rememberVerified(file: File, sha256: String) = verifier.rememberVerified(file, sha256)

    fun forget(file: File) = verifier.forget(file)
}
