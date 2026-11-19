package dev.behradhz.meowzix.ui.theme

import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.domain.settings.LauncherIconColorSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherIconSelectionTest {
    private val custom = 0xFF6750A4.toInt()
    private val wallpaper = 0xFF187CB9.toInt()

    @Test fun everyPresetHasAUniqueLightAndDarkLauncherAlias() {
        val themes = listOf(
            ColorThemePreference.MEOWZIX, ColorThemePreference.CORAL,
            ColorThemePreference.AMBER, ColorThemePreference.OCEAN,
            ColorThemePreference.MINT, ColorThemePreference.VIOLET,
            ColorThemePreference.ROSE,
        )
        val variants = themes.flatMap { theme ->
            listOf(false, true).map { dark -> selectLauncherIconVariant(theme, custom, dark, wallpaper) }
        }
        assertEquals(14, variants.map { it.aliasName }.distinct().size)
        assertEquals("LauncherIconMeowzixLight", variants.first().aliasName)
        assertEquals("LauncherIconMeowzixDark", variants[1].aliasName)
        assertEquals("LauncherIconRoseDark", variants.last().aliasName)
    }

    @Test fun customUsesNearestBundledHueButWallpaperUsesDynamicIcon() {
        assertEquals(
            LauncherIconPalette.VIOLET,
            selectLauncherIconVariant(ColorThemePreference.CUSTOM, custom, false, wallpaper).palette,
        )
        assertEquals(
            LauncherIconPalette.WALLPAPER,
            selectLauncherIconVariant(ColorThemePreference.WALLPAPER, custom, true, wallpaper).palette,
        )
        assertEquals(
            LauncherIconPalette.AMBER,
            selectLauncherIconVariant(ColorThemePreference.CUSTOM, 0xFFFFFF00.toInt(), false, wallpaper).palette,
        )
    }

    @Test fun launcherColorSourceCanFollowWallpaperWithoutChangingAppPalette() {
        val appVariant = selectLauncherIconForSource(
            LauncherIconColorSource.APP_THEME,
            ColorThemePreference.ROSE, custom, false, wallpaper,
        )
        val wallpaperVariant = selectLauncherIconForSource(
            LauncherIconColorSource.WALLPAPER,
            ColorThemePreference.ROSE, custom, false, wallpaper,
        )
        assertEquals(LauncherIconPalette.ROSE, appVariant.palette)
        assertEquals(LauncherIconPalette.WALLPAPER, wallpaperVariant.palette)
        assertEquals(false, wallpaperVariant.dark)
    }

    @Test fun grayCustomColorUsesStableDefaultRatherThanArbitraryHue() {
        assertEquals(
            LauncherIconPalette.MEOWZIX,
            selectLauncherIconVariant(ColorThemePreference.CUSTOM, 0xFF777777.toInt(), true, wallpaper).palette,
        )
    }
}
