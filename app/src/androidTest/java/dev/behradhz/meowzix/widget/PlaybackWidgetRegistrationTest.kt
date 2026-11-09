package dev.behradhz.meowzix.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class PlaybackWidgetRegistrationTest {
    @Test
    fun launcherCanReconfigureBothWidgetKindsIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activity = context.packageManager.getActivityInfo(
            ComponentName(context, WidgetConfigurationActivity::class.java), 0,
        )
        assertTrue(activity.exported)

        val androidNamespace = "http://schemas.android.com/apk/res/android"
        listOf(R.xml.meowzix_playback_widget_info, R.xml.meowzix_vinyl_widget_info).forEach { id ->
            val xml = context.resources.getXml(id)
            try {
                while (xml.eventType != org.xmlpull.v1.XmlPullParser.START_TAG &&
                    xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    xml.next()
                }
                assertEquals(
                    "dev.behradhz.meowzix.widget.WidgetConfigurationActivity",
                    xml.getAttributeValue(androidNamespace, "configure"),
                )
                val features = xml.getAttributeIntValue(androidNamespace, "widgetFeatures", 0)
                assertTrue(
                    features and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0,
                )
                assertTrue(
                    features and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0,
                )
            } finally {
                xml.close()
            }
        }
    }

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
        org.junit.Assert.assertTrue(queue.exported)
        assertEquals("android.permission.BIND_REMOTEVIEWS", queue.permission)
        assertFalse(runCatching {
            packageManager.getActivityInfo(
                ComponentName(context.packageName, "${context.packageName}.widget.VinylGestureActivity"),
                0,
            )
        }.isSuccess)
    }

}
