package com.behradhz.meowzix.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object AudioPermission {
    fun requiredPermission(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun isGranted(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context,
        requiredPermission(),
    ) == PackageManager.PERMISSION_GRANTED
}
