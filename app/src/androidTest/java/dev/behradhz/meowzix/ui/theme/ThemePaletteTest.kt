package dev.behradhz.meowzix.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePaletteTest {
    @Test
    fun grayscaleSeedProducesNeutralRatherThanArbitraryRed() {
        val color = seededColorScheme(0xFFFFFFFF.toInt(), darkTheme = false).primary.toArgb()
        assertTrue(kotlin.math.abs(android.graphics.Color.red(color) - android.graphics.Color.green(color)) < 12)
        assertTrue(kotlin.math.abs(android.graphics.Color.green(color) - android.graphics.Color.blue(color)) < 12)
    }

    @Test
    fun lightAndDarkSchemesPreserveContrastForPresetsAndExtremeHexColors() {
        val seeds = listOf(
            0xFFFFFF00.toInt(), // high-luminance yellow
            0xFF000000.toInt(),
            0xFFFFFFFF.toInt(),
            0xFFFF0000.toInt(),
            0xFF0000FF.toInt(),
            0xFF777777.toInt(),
        ) + listOf(
            ColorThemePreference.CORAL, ColorThemePreference.AMBER,
            ColorThemePreference.OCEAN, ColorThemePreference.MINT,
            ColorThemePreference.VIOLET, ColorThemePreference.ROSE,
        ).map { it.seedArgb(0xFF6750A4.toInt()) }
        for (seed in seeds) for (dark in listOf(false, true)) {
            val scheme = seededColorScheme(seed, dark)
            fun contrast(fg: Color, bg: Color) =
                ColorUtils.calculateContrast(fg.toArgb(), bg.toArgb())
            assertTrue("primary contrast: seed=${seed.toUInt().toString(16)}, dark=$dark",
                contrast(scheme.onPrimary, scheme.primary) >= 4.5)
            assertTrue("secondary contrast", contrast(scheme.onSecondary, scheme.secondary) >= 4.5)
            assertTrue("tertiary contrast", contrast(scheme.onTertiary, scheme.tertiary) >= 4.5)
            assertTrue("primary container contrast", contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5)
            assertTrue("surface contrast", contrast(scheme.onSurface, scheme.surface) >= 7.0)
            assertTrue("background contrast", contrast(scheme.onBackground, scheme.background) >= 7.0)
            assertTrue("muted surface contrast", contrast(scheme.onSurfaceVariant, scheme.surfaceVariant) >= 4.5)
        }
    }
}
