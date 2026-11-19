package dev.behradhz.meowzix.widget

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetAppearanceTest {
    @Test fun nothingThemeIsAlwaysPureMonochromeRegardlessOfAppAccent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nothing = deriveWidgetPalette(context, AppearanceSettings(widgetTheme = WidgetThemeMode.NOTHING))
        assertEquals(Color.WHITE, nothing.primary)
        assertEquals(Color.WHITE, nothing.accent)
        assertEquals(nothing, deriveWidgetPalette(context, AppearanceSettings(
            widgetTheme = WidgetThemeMode.NOTHING, customColorSeed = Color.GREEN,
        )))
    }

    @Test fun appPaletteFollowsTheConfiguredCustomSeed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val default = deriveWidgetPalette(context, AppearanceSettings(
            colorTheme = dev.behradhz.meowzix.domain.settings.ColorThemePreference.CUSTOM,
            customColorSeed = Color.BLUE,
        ))
        val customized = deriveWidgetPalette(context, AppearanceSettings(
            colorTheme = dev.behradhz.meowzix.domain.settings.ColorThemePreference.CUSTOM,
            customColorSeed = Color.MAGENTA,
        ))
        assertNotEquals(default.accent, customized.accent)
    }
}
