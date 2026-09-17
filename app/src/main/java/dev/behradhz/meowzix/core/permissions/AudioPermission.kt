package dev.behradhz.meowzix.core.permissions

import android.Manifest
import android.os.Build

object AudioPermission {
    fun requiredPermission(): String = requiredPermissionForSdk(Build.VERSION.SDK_INT)

    internal fun requiredPermissionForSdk(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}
