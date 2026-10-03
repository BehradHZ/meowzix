package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Only the ambient layer is a blur source: panels never recursively blur their own content. */
@Composable
internal fun PlaylistGlassBackdrop(content: @Composable (HazeState) -> Unit) {
    val hazeState = remember { HazeState() }
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
            drawRect(colors.background)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(colors.primary.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.20f),
                    radius = size.width * 0.95f,
                ),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(colors.tertiary.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 1.05f, size.height * 0.62f),
                    radius = size.width * 0.85f,
                ),
            )
        }
        content(hazeState)
    }
}

/** Theme-aware frosted material with a bright upper edge and an opaque blur fallback. */
@Composable
internal fun PlaylistGlassPanel(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    accented: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val light = colors.background.luminance() > 0.5f
    val shape = RoundedCornerShape(radius)
    GlassSurface(
        hazeState = hazeState,
        modifier = modifier.border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (light) 0.90f else 0.24f),
                    colors.outlineVariant.copy(alpha = 0.30f),
                    colors.primary.copy(alpha = if (accented) 0.24f else 0.08f),
                ),
            ),
            shape = shape,
        ),
        shape = shape,
        fallbackColor = colors.surface.copy(alpha = 0.96f),
        tint = if (accented) {
            colors.primary.copy(alpha = 0.07f).compositeOver(colors.surface.copy(alpha = 0.68f))
        } else {
            colors.surface.copy(alpha = if (light) 0.72f else 0.60f)
        },
        content = content,
    )
}

@Composable
internal fun PlaylistGlassAction(
    hazeState: HazeState,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accented: Boolean = false,
) {
    PlaylistGlassPanel(
        hazeState = hazeState,
        modifier = modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        radius = 18.dp,
        accented = accented,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}
