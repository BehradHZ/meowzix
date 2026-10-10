package dev.behradhz.meowzix

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveSpectrumPermissionTest {
    @Test
    @Suppress("DEPRECATION")
    fun livePlaybackVisualizerPermissionIsDeclared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        assertTrue(
            "Visualizer needs RECORD_AUDIO to capture the playback session FFT",
            packageInfo.requestedPermissions?.contains(Manifest.permission.RECORD_AUDIO) == true,
        )
    }
}
