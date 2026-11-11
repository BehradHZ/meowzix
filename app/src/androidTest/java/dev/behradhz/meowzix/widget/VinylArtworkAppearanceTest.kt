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
    private fun palette(mode: WidgetThemeMode, accent: Int) = WidgetPalette(
        background = Color.BLACK,
        primary = Color.WHITE,
        secondary = Color.LTGRAY,
        accent = accent,
        vinylColorMode = mode,
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
    @Test fun blackAndWhiteModeDiscardsCoverArtHue() {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(Color.rgb(228, 52, 105)) }
        val blackAndWhite = VinylRecordArtwork.themedFrame(
            source, palette(WidgetThemeMode.NOTHING, Color.WHITE),
        )
        val pixel = blackAndWhite.getPixel(32, 32)
        assertEquals(Color.red(pixel), Color.green(pixel))
        assertEquals(Color.green(pixel), Color.blue(pixel))
        assertNotEquals(source.getPixel(32, 32), pixel)
    }

}
