package dev.behradhz.meowzix.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.behradhz.meowzix.domain.settings.ColorThemePreference
import dev.behradhz.meowzix.domain.settings.DEFAULT_CUSTOM_COLOR_SEED

@Composable
fun MeowzixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: ColorThemePreference = ColorThemePreference.MEOWZIX,
    customColorSeed: Int = DEFAULT_CUSTOM_COLOR_SEED,
    content: @Composable () -> Unit,
) {
    // Observe configuration so a changed system wallpaper palette is picked up on recomposition.
    LocalConfiguration.current
    val context = LocalContext.current
    val palette = when {
        colorTheme == ColorThemePreference.WALLPAPER && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        colorTheme == ColorThemePreference.MEOWZIX || colorTheme == ColorThemePreference.WALLPAPER ->
            if (darkTheme) MeowzixDarkColorScheme else MeowzixLightColorScheme
        else -> seededColorScheme(colorTheme.seedArgb(customColorSeed), darkTheme)
    }

    MaterialTheme(
        colorScheme = palette,
        shapes = MeowzixShapes,
        typography = MeowzixTypography,
        content = content,
    )
}
