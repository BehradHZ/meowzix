package dev.behradhz.meowzix.ui.theme

import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.domain.settings.LauncherIconColorSource
import kotlin.math.abs

/** Each entry has a light and dark full-color launcher icon in the manifest. */
internal enum class LauncherIconPalette(val aliasSuffix: String, val seedArgb: Int) {
    MEOWZIX("Meowzix", 0xFFE85D04.toInt()),
    CORAL("Coral", 0xFFE45A5C.toInt()),
    AMBER("Amber", 0xFFE2A000.toInt()),
    OCEAN("Ocean", 0xFF187CB9.toInt()),
    MINT("Mint", 0xFF16856F.toInt()),
    VIOLET("Violet", 0xFF8257D4.toInt()),
    ROSE("Rose", 0xFFBB4D8D.toInt()),
}

internal data class LauncherIconVariant(
    val palette: LauncherIconPalette,
    val dark: Boolean,
) {
    val aliasName: String
        get() = "LauncherIcon" + palette.aliasSuffix + if (dark) "Dark" else "Light"
}

/**
 * Launcher resources are packaged at build time. For CUSTOM and WALLPAPER, use the
 * closest bundled hue; arbitrary runtime colors cannot be supplied to a launcher icon.
 */
internal fun selectLauncherIconVariant(
    theme: ColorThemePreference,
    customColor: Int,
    dark: Boolean,
    wallpaperPrimary: Int,
): LauncherIconVariant {
    val palette = when (theme) {
        ColorThemePreference.MEOWZIX -> LauncherIconPalette.MEOWZIX
        ColorThemePreference.CORAL -> LauncherIconPalette.CORAL
        ColorThemePreference.AMBER -> LauncherIconPalette.AMBER
        ColorThemePreference.OCEAN -> LauncherIconPalette.OCEAN
        ColorThemePreference.MINT -> LauncherIconPalette.MINT
        ColorThemePreference.VIOLET -> LauncherIconPalette.VIOLET
        ColorThemePreference.ROSE -> LauncherIconPalette.ROSE
        ColorThemePreference.CUSTOM -> nearestLauncherPalette(customColor)
        ColorThemePreference.WALLPAPER -> nearestLauncherPalette(wallpaperPrimary)
    }
    return LauncherIconVariant(palette, dark)
}

private fun nearestLauncherPalette(color: Int): LauncherIconPalette {
    val hue = launcherHue(color) ?: return LauncherIconPalette.MEOWZIX
    return LauncherIconPalette.entries.minBy { candidate ->
        val delta = abs(hue - (launcherHue(candidate.seedArgb) ?: 0f))
        minOf(delta, 360f - delta)
    }
}

/** Platform-independent hue extraction so preset mapping can be unit tested on the JVM. */
private fun launcherHue(color: Int): Float? {
    val r = ((color ushr 16) and 255) / 255f
    val g = ((color ushr 8) and 255) / 255f
    val b = (color and 255) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    if (delta < 0.04f) return null
    val sector = when (max) {
        r -> ((g - b) / delta) % 6f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    }
    return ((sector * 60f) % 360f + 360f) % 360f
}

/** Source selection is independent of the in-app accent and requires explicit user approval before applying. */
internal fun selectLauncherIconForSource(
    source: LauncherIconColorSource,
    appTheme: ColorThemePreference,
    customColor: Int,
    dark: Boolean,
    wallpaperPrimary: Int,
): LauncherIconVariant = selectLauncherIconVariant(
    if (source == LauncherIconColorSource.WALLPAPER) ColorThemePreference.WALLPAPER else appTheme,
    customColor,
    dark,
    wallpaperPrimary,
)
