package dev.behradhz.meowzix.ui.components

import kotlin.math.abs
import kotlin.math.floor
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
    val fraction = fastScrollbarFractionForY(yPx, geometry)
    return (fraction * (totalItems - visibleItems)).roundToInt()
}

/** Scrubbing can stop inside a row, not only at its beginning. */
internal data class FastScrollbarTarget(val index: Int, val offsetPx: Int)

internal fun fastScrollbarFractionForY(yPx: Float, geometry: FastScrollbarGeometry): Float {
    val travel = geometry.trackHeightPx - geometry.thumbHeightPx
    if (travel <= 0f) return 0f
    return ((yPx - geometry.trackTopPx - geometry.thumbHeightPx / 2f) / travel)
        .coerceIn(0f, 1f)
}

/**
 * An estimated row height is held steady for one finger gesture. This prevents
 * jitter while different-height lazy rows or section headers enter the viewport.
 */
internal fun fastScrollbarTargetForFraction(
    fraction: Float,
    totalItems: Int,
    visibleItems: Int,
    estimatedItemSizePx: Float,
): FastScrollbarTarget {
    if (totalItems <= 0) return FastScrollbarTarget(0, 0)
    val progress = fraction.coerceIn(0f, 1f)
    // At the end, LazyColumn/LazyGrid clamps the last item to the bottom viewport.
    if (progress >= 1f) return FastScrollbarTarget(totalItems - 1, 0)
    val maxFirst = (totalItems - visibleItems).coerceAtLeast(0)
    val position = progress * maxFirst
    val index = floor(position).toInt().coerceIn(0, totalItems - 1)
    val offset = ((position - index) * estimatedItemSizePx.coerceAtLeast(1f)).roundToInt()
    return FastScrollbarTarget(index, offset)
}

/** Only thumb touches inside the system-safe area may initiate a fast scroll. */
internal fun fastScrollbarThumbHitTest(
    xPx: Float,
    yPx: Float,
    viewportWidthPx: Float,
    rightInsetPx: Float,
    thumbWidthPx: Float,
    halfTouchWidthPx: Float,
    verticalSlopPx: Float,
    geometry: FastScrollbarGeometry,
): Boolean {
    val thumbCenterX = viewportWidthPx - rightInsetPx - thumbWidthPx / 2f
    return abs(xPx - thumbCenterX) <= halfTouchWidthPx &&
        yPx >= geometry.thumbTopPx - verticalSlopPx &&
        yPx <= geometry.thumbTopPx + geometry.thumbHeightPx + verticalSlopPx
}
