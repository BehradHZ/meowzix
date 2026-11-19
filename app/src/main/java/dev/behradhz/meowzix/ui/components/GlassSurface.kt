package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
    fallbackColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
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

/** Shared appearance for the floating dock and the collapsed Mini Player. */
@Composable
fun Modifier.floatingChromeFrame(shape: RoundedCornerShape): Modifier =
    shadow(6.dp, shape)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f), shape)

/**
 * Keep the dock and collapsed Mini Player visually identical. The expanded player
 * retains its pre-existing lighter backdrop by gradually restoring its old tint.
 */
@Composable
fun FloatingChromeGlassSurface(
    hazeState: HazeState,
    modifier: Modifier,
    shape: RoundedCornerShape,
    expansionFraction: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
) {
    val expansion = expansionFraction.coerceIn(0f, 1f)
    val surface = MaterialTheme.colorScheme.surface
    GlassSurface(
        hazeState = hazeState,
        modifier = modifier,
        shape = shape,
        fallbackColor = surface.copy(alpha = 0.90f + (0.85f - 0.90f) * expansion),
        tint = surface.copy(alpha = 0.74f + (0.10f - 0.74f) * expansion),
        content = content,
    )
}
