package dev.behradhz.meowzix.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class WidgetCompactLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun fullPlaybackControlsAndMetadataFitOneCellTallRow() {
        val root = LayoutInflater.from(context).inflate(
            R.layout.widget_playback, FrameLayout(context), false,
        )
        val density = context.resources.displayMetrics.density
        val width = (260f * density).roundToInt()
        val height = (48f * density).roundToInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, width, height)
        assertTrue(root.findViewById<View>(R.id.widget_primary_row).bottom <= height)
        listOf(
            R.id.widget_artwork, R.id.widget_title, R.id.widget_artist,
            R.id.widget_previous, R.id.widget_play_pause, R.id.widget_next, R.id.widget_more,
        ).forEach { id ->
            assertNotNull("The 1-row widget must expose every core control", root.findViewById<View>(id))
        }
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_queue_section).visibility)
    }

    @Test fun eachCompactWidthHasAnActualPlayControl() {
        listOf(R.layout.widget_playback_narrow, R.layout.widget_playback_medium).forEach { layout ->
            val root = LayoutInflater.from(context).inflate(layout, FrameLayout(context), false)
            assertNotNull(root.findViewById<View>(R.id.widget_artwork))
            assertNotNull(root.findViewById<View>(R.id.widget_play_pause))
        }
    }

    @Test fun themedBackgroundRemainsApplicableThroughRemoteViews() {
        val remote = RemoteViews(context.packageName, R.layout.widget_playback).apply {
            setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_playback_background_flat)
            if (Build.VERSION.SDK_INT >= 31) {
                setColorStateList(R.id.widget_root, "setBackgroundTintList", ColorStateList.valueOf(Color.MAGENTA))
            }
        }
        val inflated = remote.apply(context, FrameLayout(context))
        assertNotNull(inflated.background)
    }
}
