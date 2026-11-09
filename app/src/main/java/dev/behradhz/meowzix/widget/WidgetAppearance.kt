package dev.behradhz.meowzix.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.TypefaceSpan
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import dev.behradhz.meowzix.data.settings.DataStoreSettingsRepository
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.domain.settings.WidgetThemeMode
import dev.behradhz.meowzix.ui.theme.MeowzixDarkColorScheme
import dev.behradhz.meowzix.ui.theme.MeowzixLightColorScheme
import dev.behradhz.meowzix.ui.theme.seedArgb
import dev.behradhz.meowzix.ui.theme.seededColorScheme
import kotlinx.coroutines.flow.first

internal data class WidgetPalette(
    val background: Int,
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val monochrome: Boolean = false,
    val style: WidgetVisualStyle = WidgetVisualStyle.CLASSIC,
    val controlBackground: Int = background,
)

/**
 * Resolve the EXACT same Material 3 color roles as MeowzixTheme.
 * system_accent1_300 was the wrong role for a light surface and caused the vivid,
 * yellow/orange widget despite a pastel wallpaper chosen in Android settings.
 */
internal fun deriveWidgetPalette(
    context: Context,
    settings: AppearanceSettings,
    mode: WidgetThemeMode = settings.widgetTheme,
    style: WidgetVisualStyle = WidgetVisualStyle.CLASSIC,
): WidgetPalette {
    if (mode == WidgetThemeMode.NOTHING) {
        return WidgetPalette(
            background = if (style == WidgetVisualStyle.MINIMAL) Color.BLACK else Color.rgb(13, 13, 13),
            primary = Color.WHITE,
            secondary = Color.rgb(202, 202, 202),
            accent = Color.WHITE,
            monochrome = true,
            style = style,
            controlBackground = Color.rgb(49, 49, 49),
        )
    }
    val dark = when (settings.theme) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
    val wallpaper = mode == WidgetThemeMode.WALLPAPER ||
        settings.colorTheme == ColorThemePreference.WALLPAPER
    val scheme: ColorScheme = when {
        wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        settings.colorTheme == ColorThemePreference.MEOWZIX || wallpaper ->
            if (dark) MeowzixDarkColorScheme else MeowzixLightColorScheme
        else -> seededColorScheme(settings.colorTheme.seedArgb(settings.customColorSeed), dark)
    }
    val background = if (style == WidgetVisualStyle.MINIMAL) scheme.surface
        else if (wallpaper) scheme.primaryContainer else scheme.surfaceVariant
    return WidgetPalette(
        background = background.toArgb(),
        primary = if (wallpaper && style == WidgetVisualStyle.CLASSIC)
            scheme.onPrimaryContainer.toArgb() else scheme.onSurface.toArgb(),
        secondary = if (wallpaper && style == WidgetVisualStyle.CLASSIC)
            scheme.onPrimaryContainer.copy(alpha = .78f).toArgb()
            else scheme.onSurfaceVariant.toArgb(),
        accent = scheme.primary.toArgb(),
        monochrome = false,
        style = style,
        controlBackground = scheme.secondaryContainer.toArgb(),
    )
}

internal suspend fun widgetPalette(context: Context, widgetId: Int): WidgetPalette {
    val settings = DataStoreSettingsRepository(context.applicationContext).appearanceSettings.first()
    return deriveWidgetPalette(
        context,
        settings,
        WidgetInstancePreferences.theme(context, widgetId),
        WidgetInstancePreferences.style(context, widgetId),
    )
}

/** Nothing-style typography uses the system monospace font, not a bundled font file. */
internal fun widgetStyledText(text: String, palette: WidgetPalette): CharSequence =
    if (!palette.monochrome) text else SpannableString(text).apply {
        setSpan(TypefaceSpan("monospace"), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
