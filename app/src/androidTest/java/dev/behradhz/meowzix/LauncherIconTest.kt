package dev.behradhz.meowzix

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guard launcher resource references and the cat foreground from reverting to
 * an empty adaptive-icon layer. Android 13 also requires a real themed mask.
 */
@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test
    fun bothLauncherIconsResolveWithVisibleForeground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifestIcon = context.applicationInfo.icon
        assertTrue("The manifest must specify a launcher icon", manifestIcon != 0)

        val icons = listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_round)
        icons.forEach { icon ->
            val drawable = context.getDrawable(icon)
            assertNotNull("Launcher icon could not be loaded: $icon", drawable)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                assertTrue("Launcher must be adaptive on Android 8+", drawable is AdaptiveIconDrawable)
                val adaptive = drawable as AdaptiveIconDrawable
                assertTrue(
                    "Cat foreground should use a safe-area inset, not be transparent",
                    adaptive.foreground is InsetDrawable,
                )
                assertNotNull(adaptive.background)
            }
        }
    }

    @Test
    fun launcherBackgroundMatchesPeachBrandColor() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        org.junit.Assert.assertEquals(
            0xFFFFB38A.toInt(),
            context.getColor(R.color.ic_launcher_background),
        )
    }

    @Test
    fun monochromeCatDrawableCanBeInflated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(
            "Android 13 themed icon must use the Meowzix cat",
            context.getDrawable(R.drawable.ic_launcher_monochrome),
        )
    }
}
