package dev.behradhz.meowzix.feature.profile

import dev.behradhz.meowzix.domain.settings.LibrarySelectionAction

/** The dragged row remains under the finger while neighboring rows change positions immediately. */
internal data class SelectionMenuDragFrame(
    val order: List<LibrarySelectionAction>,
    val offsetPx: Float,
)

/**
 * Reorder as the finger crosses neighboring row midpoints, not when it is released.
 * Translate the dragged row by one row in the opposite direction after each move
 * so its on-screen position remains continuous as its layout index changes.
 */
internal fun advanceSelectionMenuDrag(
    order: List<LibrarySelectionAction>,
    action: LibrarySelectionAction,
    offsetPx: Float,
    deltaY: Float,
    rowHeightPx: Float,
): SelectionMenuDragFrame {
    if (rowHeightPx <= 0f || !rowHeightPx.isFinite() || !deltaY.isFinite()) {
        return SelectionMenuDragFrame(order, offsetPx)
    }
    val startingIndex = order.indexOf(action)
    if (startingIndex < 0) return SelectionMenuDragFrame(order, offsetPx)

    val reordered = order.toMutableList()
    var index = startingIndex
    var displacement = offsetPx + deltaY
    val threshold = rowHeightPx / 2f

    while (displacement > threshold && index < reordered.lastIndex) {
        reordered.add(index + 1, reordered.removeAt(index))
        displacement -= rowHeightPx
        index++
    }
    while (displacement < -threshold && index > 0) {
        reordered.add(index - 1, reordered.removeAt(index))
        displacement += rowHeightPx
        index--
    }

    // At the list edges, don't allow the dragged item to escape its container.
    displacement = displacement.coerceIn(-rowHeightPx * 0.65f, rowHeightPx * 0.65f)
    return SelectionMenuDragFrame(reordered, displacement)
}
