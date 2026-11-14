package dev.behradhz.meowzix.widget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VinylArtworkAppearanceTest {
    private fun palette(mode: WidgetThemeMode, accent: Int, redPercent: Int = 100) = WidgetPalette(
        background = Color.BLACK,
        primary = Color.WHITE,
        secondary = Color.LTGRAY,
        accent = accent,
        vinylColorMode = mode,
        vinylRedPercent = redPercent,
    )

    @Test fun fullColorLeavesOriginalRecordPixelsUnchanged() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.rgb(233, 185, 60)) }
        val rendered = VinylRecordArtwork.themedFrame(
            source, palette(WidgetThemeMode.FULL_COLOR, Color.BLUE),
        )
        assertSame(source, rendered)
        assertEquals(source.getPixel(32, 32), rendered.getPixel(32, 32))
    }

    @Test fun monoAccentUsesChosenAppOrWallpaperHue() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.rgb(233, 185, 60)) }
        val app = VinylRecordArtwork.themedFrame(
            source, palette(WidgetThemeMode.APP, Color.rgb(45, 90, 225)),
        )
        val wallpaper = VinylRecordArtwork.themedFrame(
            source, palette(WidgetThemeMode.WALLPAPER, Color.rgb(225, 80, 65)),
        )
        val appPixel = app.getPixel(32, 32)
        val wallpaperPixel = wallpaper.getPixel(32, 32)
        assertNotEquals(source.getPixel(32, 32), appPixel)
        assertNotEquals(appPixel, wallpaperPixel)
        assertTrue(Color.blue(appPixel) > Color.red(appPixel))
        assertTrue(Color.red(wallpaperPixel) > Color.blue(wallpaperPixel))
    }
    @Test fun nothingPausedUsesScreenshotBlackEvenWithMaximumRedSetting() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.BLACK) }
        val pixel = VinylRecordArtwork.themedFrame(
            source, palette(WidgetThemeMode.NOTHING, Color.WHITE), isPlaying = false,
        ).getPixel(32, 32)
        assertEquals(Color.rgb(31, 27, 24), pixel)
    }

    @Test fun nothingPlayingAtMaximumUsesScreenshotRedAndKeepsWhiteHighlights() {
        val black = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.BLACK) }
        val white = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.WHITE) }
        val mode = palette(WidgetThemeMode.NOTHING, Color.WHITE, redPercent = 100)
        assertEquals(Color.rgb(215, 26, 33), VinylRecordArtwork.themedFrame(
            black, mode, isPlaying = true,
        ).getPixel(32, 32))
        assertEquals(Color.WHITE, VinylRecordArtwork.themedFrame(
            white, mode, isPlaying = true,
        ).getPixel(32, 32))
    }

    @Test fun nothingPlayingAtZeroRedStaysBlackAndHalfRedIsIntermediate() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.BLACK) }
        val black = VinylRecordArtwork.themedFrame(
            source, palette(WidgetThemeMode.NOTHING, Color.WHITE, 0), true,
        ).getPixel(32, 32)
        val half = VinylRecordArtwork.themedFrame(
            source, palette(WidgetThemeMode.NOTHING, Color.WHITE, 50), true,
        ).getPixel(32, 32)
        val red = VinylRecordArtwork.themedFrame(
            source, palette(WidgetThemeMode.NOTHING, Color.WHITE, 100), true,
        ).getPixel(32, 32)
        assertEquals(Color.rgb(31, 27, 24), black)
        assertTrue(Color.red(black) < Color.red(half))
        assertTrue(Color.red(half) < Color.red(red))
        assertEquals(Color.rgb(215, 26, 33), red)
    }

}
