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
    fun bothWidgetsAndCollectionProviderAreRegistered() {
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

        val vinyl = packageManager.getReceiverInfo(
            ComponentName(context, VinylWidgetProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        assertEquals(
            R.xml.meowzix_vinyl_widget_info,
            vinyl.metaData.getInt("android.appwidget.provider"),
        )
        val actions = packageManager.getReceiverInfo(
            ComponentName(context, PlaybackWidgetActionReceiver::class.java), 0,
        )
        assertFalse(actions.exported)
        val queue = packageManager.getServiceInfo(
            ComponentName(context, PlaybackWidgetQueueService::class.java), 0,
        )
        assertFalse(queue.exported)
        assertEquals("android.permission.BIND_REMOTEVIEWS", queue.permission)
        assertFalse(runCatching {
            packageManager.getActivityInfo(
                ComponentName(context.packageName, "${context.packageName}.widget.VinylGestureActivity"),
                0,
            )
        }.isSuccess)
    }

}
