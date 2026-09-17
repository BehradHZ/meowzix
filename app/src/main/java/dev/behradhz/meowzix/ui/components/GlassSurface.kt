package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Shared floating glass material used by Meowzix chrome.
 *
 * Haze renders the sampled backdrop when supported and degrades to the authored
 * translucent background/tint when an optical effect cannot be rendered.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassSurface(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(30.dp),
    fallbackColor: Color = Color.Black.copy(alpha = 0.38f),
    tint: Color = Color.White.copy(alpha = 0.10f),
    interactive: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val style = remember(shape, fallbackColor, tint, interactive) {
        GlassStyle.regular.then {
            backgroundColor(fallbackColor)
            tint(tint)
            shape(shape)
            specularIntensity(0.55f)
            edgeSoftness(10.dp)
            if (interactive) {
                pressed {
                    lightingIntensity(1f)
                    refractionMultiplier(1.05f)
                    scale(0.985f)
                }
            }
        }
    }

    Box(
        modifier = modifier.hazeGlass(
            input = HazeInput.Backdrop(hazeState),
            style = style,
            interactionSource = interactionSource,
        ),
        content = content,
    )
}
