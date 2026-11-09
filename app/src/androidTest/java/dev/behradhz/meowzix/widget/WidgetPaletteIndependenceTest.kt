package dev.behradhz.meowzix.widget

import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPaletteIndependenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun wallpaperSurfaceUsesPlatformMaterialYouTonalRoleNotHardcodedBlue() {
        if (Build.VERSION.SDK_INT < 31) return
        val palette = deriveWidgetPalette(
            context,
            AppearanceSettings(theme = ThemePreference.LIGHT, colorTheme = ColorThemePreference.OCEAN),
            WidgetThemeMode.WALLPAPER,
        )
        assertEquals(dynamicLightColorScheme(context).primaryContainer.toArgb(), palette.background)
    }

    @Test fun eachWidgetCanUseDifferentThemeAndStyleWithoutMutatingGlobalTheme() {
        val base = AppearanceSettings(theme = ThemePreference.LIGHT, colorTheme = ColorThemePreference.CORAL)
        val app = deriveWidgetPalette(context, base, WidgetThemeMode.APP, WidgetVisualStyle.CLASSIC)
        val nothing = deriveWidgetPalette(context, base, WidgetThemeMode.NOTHING, WidgetVisualStyle.MINIMAL)
        assertNotEquals(app.background, nothing.background)
        assertEquals(Color.WHITE, nothing.accent)
        assertEquals(WidgetVisualStyle.CLASSIC, app.style)
        assertEquals(WidgetVisualStyle.MINIMAL, nothing.style)
    }

    @Test fun widgetOpacityChangesOnlySurfaceAlphaNotForegroundColors() {
        val base = deriveWidgetPalette(
            context,
            AppearanceSettings(theme = ThemePreference.DARK),
            WidgetThemeMode.NOTHING,
        )
        assertEquals(255, Color.alpha(base.copy(opacityPercent = 100).surfaceColor))
        assertEquals(128, Color.alpha(base.copy(opacityPercent = 50).surfaceColor))
        assertEquals(0, Color.alpha(base.copy(opacityPercent = 0).surfaceColor))
        assertEquals(base.primary, base.copy(opacityPercent = 0).primary)
        assertEquals(base.accent, base.copy(opacityPercent = 0).accent)
        assertEquals(0, Color.alpha(base.copy(opacityPercent = 0).rowSurfaceColor))
    }

    @Test fun twoWidgetIdsReadTheirOwnPersistedSettings() {
        val first = 91453
        val second = 91454
        val prefs = context.getSharedPreferences("meowzix_widget_instance_appearance", Context.MODE_PRIVATE)
        try {
            prefs.edit().putString("theme_$first", WidgetThemeMode.NOTHING.name)
                .putString("theme_$second", WidgetThemeMode.WALLPAPER.name)
                .putString("style_$first", WidgetVisualStyle.MINIMAL.name)
                .putString("style_$second", WidgetVisualStyle.CLASSIC.name)
                .putInt("opacity_$first", 30)
                .putInt("opacity_$second", 85)
                .commit()
            assertEquals(WidgetThemeMode.NOTHING, WidgetInstancePreferences.theme(context, first))
            assertEquals(WidgetThemeMode.WALLPAPER, WidgetInstancePreferences.theme(context, second))
            assertEquals(WidgetVisualStyle.MINIMAL, WidgetInstancePreferences.style(context, first))
            assertEquals(WidgetVisualStyle.CLASSIC, WidgetInstancePreferences.style(context, second))
            assertEquals(30, WidgetInstancePreferences.opacity(context, first))
            assertEquals(85, WidgetInstancePreferences.opacity(context, second))
        } finally {
            WidgetInstancePreferences.remove(context, intArrayOf(first, second))
        }
    }
}
