package dev.behradhz.meowzix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@Composable
fun MeowzixTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) MeowzixDarkColorScheme else MeowzixLightColorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = MeowzixShapes,
        typography = MeowzixTypography,
        content = content,
    )
}
