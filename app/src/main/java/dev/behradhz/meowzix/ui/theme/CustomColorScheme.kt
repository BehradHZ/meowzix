package dev.behradhz.meowzix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import dev.behradhz.meowzix.domain.settings.ColorThemePreference

/** Opaque user-provided sRGB only. Do not accept alpha or arbitrary serialized CSS colors. */
fun parseCustomColorHex(value: String): Int? {
    val input = value.trim().removePrefix("#")
    if (!input.matches(Regex("[0-9a-fA-F]{6}"))) return null
    return (input.toLong(16) or 0xFF000000L).toInt()
}

internal fun ColorThemePreference.seedArgb(custom: Int): Int = when (this) {
    ColorThemePreference.MEOWZIX, ColorThemePreference.WALLPAPER -> 0xFFE85D04.toInt()
    ColorThemePreference.CORAL -> 0xFFE45A5C.toInt()
    ColorThemePreference.AMBER -> 0xFFE2A000.toInt()
    ColorThemePreference.OCEAN -> 0xFF187CB9.toInt()
    ColorThemePreference.MINT -> 0xFF16856F.toInt()
    ColorThemePreference.VIOLET -> 0xFF8257D4.toInt()
    ColorThemePreference.ROSE -> 0xFFBB4D8D.toInt()
    ColorThemePreference.CUSTOM -> custom or 0xFF000000.toInt()
}

/** The logo displays its exact RGB background, independent of the tonal Material palette. */
internal fun ColorThemePreference.logoBackgroundArgb(custom: Int): Int =
    if (this == ColorThemePreference.MEOWZIX) 0xFFE5948A.toInt() else seedArgb(custom)

/**
 * Build light/dark color ROLES from a chosen hue, not by placing a raw seed behind arbitrary text.
 * Contrast is checked for both primary/onPrimary combinations; muted neutral surfaces keep
 * prose legible even with very light (e.g. yellow) or highly saturated custom seeds.
 */
internal fun seededColorScheme(seedArgb: Int, darkTheme: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seedArgb or 0xFF000000.toInt(), hsl)
    val hue = hsl[0]
    val saturation = if (hsl[1] < 0.08f) 0.035f else hsl[1].coerceIn(0.38f, 0.78f)
    fun toned(h: Float, s: Float, l: Float): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))
    fun accessibleAccent(h: Float, l: Float, dark: Boolean): Color {
        var lightness = l
        val text = if (dark) Color.Black else Color.White
        var candidate = toned(h, saturation, lightness)
        while (ColorUtils.calculateContrast(text.toArgb(), candidate.toArgb()) < 4.5 && lightness in 0.05f..0.95f) {
            lightness = (lightness + if (dark) 0.012f else -0.012f).coerceIn(0.05f, 0.95f)
            candidate = toned(h, saturation, lightness)
        }
        return candidate
    }
    val primary = accessibleAccent(hue, if (darkTheme) 0.76f else 0.32f, darkTheme)
    val secondary = accessibleAccent(hue + 28f, if (darkTheme) 0.77f else 0.31f, darkTheme)
    val tertiary = accessibleAccent(hue + 75f, if (darkTheme) 0.77f else 0.30f, darkTheme)

    return if (darkTheme) {
        MeowzixDarkColorScheme.copy(
            primary = primary,
            onPrimary = Color.Black,
            primaryContainer = toned(hue, saturation * 0.53f, 0.21f),
            onPrimaryContainer = toned(hue, saturation * 0.48f, 0.92f),
            secondary = secondary,
            onSecondary = Color.Black,
            secondaryContainer = toned(hue + 28f, 0.30f, 0.23f),
            onSecondaryContainer = toned(hue + 28f, 0.38f, 0.92f),
            tertiary = tertiary,
            onTertiary = Color.Black,
            tertiaryContainer = toned(hue + 75f, 0.32f, 0.23f),
            onTertiaryContainer = toned(hue + 75f, 0.40f, 0.93f),
            background = toned(hue, 0.07f, 0.065f),
            onBackground = Color(0xFFF8F5F8),
            surface = toned(hue, 0.07f, 0.105f),
            onSurface = Color(0xFFF8F5F8),
            surfaceVariant = toned(hue, 0.08f, 0.17f),
            onSurfaceVariant = Color(0xFFD3CDD3),
            surfaceTint = primary,
            outline = toned(hue, 0.09f, 0.56f),
            outlineVariant = toned(hue, 0.09f, 0.30f),
        )
    } else {
        MeowzixLightColorScheme.copy(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = toned(hue, saturation * 0.34f, 0.91f),
            onPrimaryContainer = toned(hue, saturation * 0.66f, 0.16f),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = toned(hue + 28f, 0.23f, 0.91f),
            onSecondaryContainer = toned(hue + 28f, 0.56f, 0.17f),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = toned(hue + 75f, 0.29f, 0.91f),
            onTertiaryContainer = toned(hue + 75f, 0.60f, 0.16f),
            background = toned(hue, 0.075f, 0.976f),
            onBackground = Color(0xFF201E20),
            surface = toned(hue, 0.07f, 0.969f),
            onSurface = Color(0xFF201E20),
            surfaceVariant = toned(hue, 0.075f, 0.925f),
            onSurfaceVariant = Color(0xFF514D53),
            surfaceTint = primary,
            outline = toned(hue, 0.09f, 0.46f),
            outlineVariant = toned(hue, 0.09f, 0.79f),
        )
    }
}
