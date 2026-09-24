package dev.behradhz.meowzix.core.permissions

import android.Manifest
import android.os.Build

object AudioPermission {
    /**
     * Device media access changed in Android 13 (API 33). Older Android versions
     * require the legacy external-storage permission, while Android 13+ uses the
     * granular audio permission.
     */
    fun requiredPermission(): String = requiredPermissionForSdk(Build.VERSION.SDK_INT)

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
