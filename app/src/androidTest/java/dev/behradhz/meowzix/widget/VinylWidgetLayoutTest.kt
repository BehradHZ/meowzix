package dev.behradhz.meowzix.widget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VinylWidgetLayoutTest {
    @Test fun recordIsOnlyVisibleContentWithNoSquareBackground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = LayoutInflater.from(context).inflate(
            R.layout.widget_vinyl_disc_only, FrameLayout(context), false,
        )
        assertNull(view.background)
        assertEquals(1, imageCount(view))
        assertNotNull(view.findViewById<ImageView>(R.id.vinyl_widget_disc).drawable)
        val zones = intArrayOf(
            R.id.vinyl_widget_center,
            R.id.vinyl_widget_left,
            R.id.vinyl_widget_right,
            R.id.vinyl_widget_top,
            R.id.vinyl_widget_bottom,
            R.id.vinyl_widget_open_top_left,
            R.id.vinyl_widget_open_top_right,
            R.id.vinyl_widget_open_bottom_left,
            R.id.vinyl_widget_open_bottom_right,
        )
        zones.forEach { id ->
            val target = view.findViewById<TextView>(id)
            assertNotNull(target)
            assertTrue(target.text.isNullOrEmpty())
            assertNull(target.background)
        }
    }

    private fun imageCount(view: View): Int =
        (if (view is ImageView) 1 else 0) +
            (if (view is ViewGroup) {
                (0 until view.childCount).sumOf { index -> imageCount(view.getChildAt(index)) }
            } else 0)
}
