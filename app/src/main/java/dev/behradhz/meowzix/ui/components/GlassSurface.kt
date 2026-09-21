package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Shared floating glass material used by Meowzix chrome.
 *
 * Haze 1.6.10 keeps the real backdrop blur while allowing the app to compile
 * against Android 16 / API 36. The explicit fallback tint keeps the surface
 * readable if blur is unavailable on a device or renderer.
 */
@Composable
fun GlassSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(30.dp),
    fallbackColor: Color = Color.Black.copy(alpha = 0.38f),
    tint: Color = Color.White.copy(alpha = 0.10f),
    interactive: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = remember(fallbackColor, tint) {
        HazeStyle(
            backgroundColor = fallbackColor,
            tint = HazeTint(tint),
            blurRadius = 28.dp,
            noiseFactor = 0.08f,
            fallbackTint = HazeTint(fallbackColor),
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = style,
            ),
        content = content,
    )
}
