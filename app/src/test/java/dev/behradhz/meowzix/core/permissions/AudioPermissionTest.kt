package dev.behradhz.meowzix.core.permissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPermissionTest {
    @Test
    fun `Android 13 and newer use granular audio permission`() {
        assertEquals(
            Manifest.permission.READ_MEDIA_AUDIO,
            AudioPermission.requiredPermissionForSdk(33),
        )
        assertEquals(
            Manifest.permission.READ_MEDIA_AUDIO,
            AudioPermission.requiredPermissionForSdk(36),
        )
    }

    @Test
    fun `Android 12 and older use legacy external storage permission`() {
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            AudioPermission.requiredPermissionForSdk(32),
        )
        assertEquals(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            AudioPermission.requiredPermissionForSdk(26),
        )
    }
}
