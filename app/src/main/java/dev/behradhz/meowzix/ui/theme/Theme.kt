package dev.behradhz.meowzix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun MeowzixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MeowzixDarkColorScheme else MeowzixLightColorScheme,
        shapes = MeowzixShapes,
        typography = MeowzixTypography,
        content = content,
    )
}
