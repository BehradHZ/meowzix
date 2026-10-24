package dev.behradhz.meowzix.widget

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class VinylWidgetProviderTest {
    @Test
    fun vinylIsASeparateHomeScreenWidgetAndDoesNotReplaceCompactPlayback() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val compact = context.packageManager.getReceiverInfo(
            ComponentName(context, PlaybackWidgetProvider::class.java), PackageManager.GET_META_DATA,
        )
        val vinyl = context.packageManager.getReceiverInfo(
            ComponentName(context, VinylWidgetProvider::class.java), PackageManager.GET_META_DATA,
        )
        val key = "android.appwidget.provider"
        assertEquals(R.xml.meowzix_playback_widget_info, compact.metaData.getInt(key))
        assertEquals(R.xml.meowzix_vinyl_widget_info, vinyl.metaData.getInt(key))
        assertNotEquals(compact.name, vinyl.name)
        assertTrue(vinyl.exported)
    }
}
