package dev.behradhz.meowzix.core.permissions

import android.Manifest
import android.os.Build

object AudioPermission {
    /**
     * Telegram-only mode does not read device media, so the Library route uses an
     * already-granted normal permission and never prompts for local audio access.
     */
    fun requiredPermission(): String = Manifest.permission.INTERNET

    internal fun requiredPermissionForSdk(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}

enum class AudioPermissionStatus {
    GRANTED,
    REQUIRED,
    DENIED,
}

fun audioPermissionStatus(
    granted: Boolean,
    requestAttempted: Boolean,
): AudioPermissionStatus = when {
    granted -> AudioPermissionStatus.GRANTED
    requestAttempted -> AudioPermissionStatus.DENIED
    else -> AudioPermissionStatus.REQUIRED
}
