package dev.behradhz.meowzix.ui.components

import kotlin.math.roundToInt

/** Layout calculations are pure so dragging never depends on a frame-delayed animation. */
internal data class FastScrollbarGeometry(
    val trackTopPx: Float,
    val trackHeightPx: Float,
    val thumbTopPx: Float,
    val thumbHeightPx: Float,
)

internal fun fastScrollbarGeometry(
    viewportPx: Float,
    totalItems: Int,
    visibleItems: Int,
    firstIndex: Int,
    firstOffsetPx: Int,
    firstItemSizePx: Int,
    insetPx: Float,
    minThumbHeightPx: Float,
    atEnd: Boolean,
): FastScrollbarGeometry? {
    if (totalItems <= visibleItems || visibleItems <= 0 || viewportPx <= 0f) return null
    val top = insetPx.coerceAtLeast(0f)
    val height = (viewportPx - top * 2f).coerceAtLeast(0f)
    if (height == 0f) return null
    val thumbHeight = (height * visibleItems / totalItems.toFloat())
        .coerceAtLeast(minThumbHeightPx.coerceIn(0f, height))
        .coerceAtMost(height)
    val maxFirstIndex = (totalItems - visibleItems).coerceAtLeast(1)
    val approximateIndex = firstIndex.coerceAtLeast(0) +
        firstOffsetPx.coerceAtLeast(0) / firstItemSizePx.coerceAtLeast(1).toFloat()
    val fraction = if (atEnd) 1f else (approximateIndex / maxFirstIndex).coerceIn(0f, 1f)
    return FastScrollbarGeometry(
        trackTopPx = top,
        trackHeightPx = height,
        thumbTopPx = top + (height - thumbHeight) * fraction,
        thumbHeightPx = thumbHeight,
    )
}

internal fun fastScrollbarIndexForY(
    yPx: Float,
    viewportPx: Float,
    totalItems: Int,
    visibleItems: Int,
    insetPx: Float,
    minThumbHeightPx: Float,
): Int {
    val geometry = fastScrollbarGeometry(
        viewportPx, totalItems, visibleItems, 0, 0, 1, insetPx, minThumbHeightPx, false,
    ) ?: return 0
    val travel = geometry.trackHeightPx - geometry.thumbHeightPx
    if (travel <= 0f) return 0
    val fraction = ((yPx - geometry.trackTopPx - geometry.thumbHeightPx / 2f) / travel)
        .coerceIn(0f, 1f)
    return (fraction * (totalItems - visibleItems)).roundToInt()
}
