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
        val width = (360f * density).roundToInt()
        val height = (64f * density).roundToInt()
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
        assertEquals(
            "Cover art must remain square in the one-row widget",
            root.findViewById<View>(R.id.widget_artwork).width,
            root.findViewById<View>(R.id.widget_artwork).height,
        )
        val minTouchWidth = (44 * density).roundToInt()
        listOf(R.id.widget_previous, R.id.widget_play_pause, R.id.widget_next, R.id.widget_more)
            .forEach { id ->
                assertTrue("Transport control must provide a 44dp click target",
                    root.findViewById<View>(id).width >= minTouchWidth)
            }
    }

    @Test fun mainTransportKeepsItsHeightAcrossWidgetResizes() {
        val density = context.resources.displayMetrics.density
        val width = (380f * density).roundToInt()
        val expected = (56f * density).roundToInt()
        for (heightDp in listOf(68f, 130f, 260f)) {
            val root = LayoutInflater.from(context).inflate(
                R.layout.widget_playback, FrameLayout(context), false,
            )
            root.findViewById<View>(R.id.widget_queue_section).visibility =
                if (heightDp >= 130f) View.VISIBLE else View.GONE
            val height = (heightDp * density).roundToInt()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, width, height)
            assertEquals(
                "The primary transport row must not shrink as queue height grows",
                expected,
                root.findViewById<View>(R.id.widget_primary_row).height,
            )
        }
    }

    @Test fun everyUpcomingSongCardOccupiesExactlyOneIconHeight() {
        val parent = FrameLayout(context)
        val row = LayoutInflater.from(context).inflate(
            R.layout.widget_playback_queue_row, parent, false,
        )
        parent.addView(row)
        val density = context.resources.displayMetrics.density
        parent.measure(
            View.MeasureSpec.makeMeasureSpec((340f * density).roundToInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((300f * density).roundToInt(), View.MeasureSpec.AT_MOST),
        )
        // As in ListView, the parent must honor the row's 56dp LayoutParams.
        assertEquals((56f * density).roundToInt(), row.measuredHeight)
        assertEquals(View.GONE, row.findViewById<View>(R.id.widget_queue_position).visibility)
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
