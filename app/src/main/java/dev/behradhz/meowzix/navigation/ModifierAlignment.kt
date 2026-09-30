package dev.behradhz.meowzix.navigation

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize

/**
 * Alignment modifier for content emitted by a helper composable outside the parent's BoxScope.
 * BoxScope's member `align` still takes precedence inside normal Box/BoxWithConstraints content.
 */
internal fun Modifier.align(alignment: Alignment): Modifier = layout { measurable, constraints ->
    val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
    val placeable = measurable.measure(childConstraints)
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
    val position = alignment.align(
        size = IntSize(placeable.width, placeable.height),
        space = IntSize(width, height),
        layoutDirection = layoutDirection,
    )
    layout(width, height) {
        placeable.placeRelative(position.x, position.y)
    }
}
