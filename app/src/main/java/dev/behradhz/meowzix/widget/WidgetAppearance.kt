package dev.behradhz.meowzix.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import android.os.Build
import androidx.core.graphics.ColorUtils
import dev.behradhz.meowzix.data.settings.DataStoreSettingsRepository
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import kotlinx.coroutines.flow.first

internal data class WidgetPalette(
    val background: Int,
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val monochrome: Boolean = false,
)

internal suspend fun widgetPalette(context: Context): WidgetPalette =
    deriveWidgetPalette(
        context,
        DataStoreSettingsRepository(context.applicationContext).appearanceSettings.first(),
    )

internal fun deriveWidgetPalette(context: Context, settings: AppearanceSettings): WidgetPalette {
    if (settings.widgetTheme == WidgetThemeMode.NOTHING) {
        // Black/white, minimal contrast hierarchy, nothing to do with in-app accent.
        return WidgetPalette(
            background = Color.rgb(12, 12, 12),
            primary = Color.WHITE,
            secondary = Color.rgb(194, 194, 194),
            accent = Color.WHITE,
            monochrome = true,
        )
    }
    val useWallpaper = settings.widgetTheme == WidgetThemeMode.WALLPAPER ||
        settings.colorTheme == ColorThemePreference.WALLPAPER
    val seed = if (useWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_300)
    } else when (settings.colorTheme) {
        ColorThemePreference.MEOWZIX -> Color.rgb(120, 148, 246)
        ColorThemePreference.CORAL -> Color.rgb(233, 114, 118)
        ColorThemePreference.AMBER -> Color.rgb(220, 164, 66)
        ColorThemePreference.OCEAN -> Color.rgb(61, 142, 216)
        ColorThemePreference.MINT -> Color.rgb(52, 183, 153)
        ColorThemePreference.VIOLET -> Color.rgb(157, 115, 235)
        ColorThemePreference.ROSE -> Color.rgb(226, 114, 175)
        ColorThemePreference.CUSTOM -> settings.customColorSeed
        ColorThemePreference.WALLPAPER -> Color.rgb(120, 148, 246)
    }
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    val dark = when (settings.theme) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> systemDark
    }
    return if (dark) {
        WidgetPalette(
            background = ColorUtils.blendARGB(Color.rgb(12, 15, 23), seed, .18f),
            primary = Color.WHITE,
            secondary = Color.rgb(205, 212, 223),
            accent = ColorUtils.blendARGB(Color.WHITE, seed, .48f),
        )
    } else {
        WidgetPalette(
            background = ColorUtils.blendARGB(Color.WHITE, seed, .15f),
            primary = Color.rgb(25, 29, 39),
            secondary = Color.rgb(73, 78, 91),
            accent = ColorUtils.blendARGB(Color.BLACK, seed, .65f),
        )
    }
}

/** Nothing-style compact typography without custom system font files. */
internal fun widgetStyledText(text: String, palette: WidgetPalette): CharSequence =
    if (!palette.monochrome) text else SpannableString(text).apply {
        setSpan(TypefaceSpan("monospace"), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
