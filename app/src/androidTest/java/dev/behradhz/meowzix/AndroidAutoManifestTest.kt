package dev.behradhz.meowzix

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.playback.PlaybackService
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/** Guards Android Auto discovery, not a substitute for testing on a car host/DHU. */
@RunWith(AndroidJUnit4::class)
class AndroidAutoManifestTest {
    @Test
    @Suppress("DEPRECATION")
    fun androidAutoDiscoversTheSingleMediaLibraryService() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        val descriptorId = appInfo.metaData?.getInt("com.google.android.gms.car.application") ?: 0
        assertTrue("Android Auto app metadata is missing", descriptorId != 0)

        val parser = context.resources.getXml(descriptorId)
        var declaresMedia = false
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG &&
                    parser.name == "uses" &&
                    parser.getAttributeValue(null, "name") == "media"
                ) {
                    declaresMedia = true
                }
            }
        } finally {
            parser.close()
        }
        assertTrue("Android Auto descriptor does not declare media", declaresMedia)

        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, PlaybackService::class.java),
            PackageManager.GET_META_DATA,
        )
        assertTrue("PlaybackService must be exported for Android Auto browsing", serviceInfo.exported)
    }
}
