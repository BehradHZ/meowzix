package dev.behradhz.meowzix.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class PlaybackWidgetRegistrationTest {
    @Test
    fun compactPlaybackWidgetRemainsAndVinylIsUnregistered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val playback = packageManager.getReceiverInfo(
            ComponentName(context, PlaybackWidgetProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        assertEquals(
            R.xml.meowzix_playback_widget_info,
            playback.metaData.getInt("android.appwidget.provider"),
        )

        // These class names must no longer be exposed to Android after app upgrade.
        assertFalse(runCatching {
            packageManager.getReceiverInfo(
                ComponentName(context.packageName, "${context.packageName}.widget.VinylWidgetProvider"),
                PackageManager.GET_META_DATA,
            )
        }.isSuccess)
        assertFalse(runCatching {
            packageManager.getActivityInfo(
                ComponentName(context.packageName, "${context.packageName}.widget.VinylGestureActivity"),
                0,
            )
        }.isSuccess)
    }

}
