package dev.behradhz.meowzix.ui.components

import kotlin.math.abs

/** Pixel-based coordinates shared by lists and grids; no item-index snapping. */
internal data class FastScrollMetrics(
    val scrollOffsetPx: Float,
    val contentSizePx: Float,
    val viewportSizePx: Float,
) {
    val scrollRangePx: Float get() = (contentSizePx - viewportSizePx).coerceAtLeast(0f)
    val fraction: Float
        get() = if (scrollRangePx <= 0f) 0f
        else (scrollOffsetPx / scrollRangePx).coerceIn(0f, 1f)
}

internal data class FastScrollbarGeometry(
    val trackTopPx: Float,
    val trackHeightPx: Float,
    val thumbTopPx: Float,
    val thumbHeightPx: Float,
) {
    val thumbCenterPx: Float get() = thumbTopPx + thumbHeightPx / 2f
    val travelPx: Float get() = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
}

internal fun fastScrollbarGeometry(
    viewportPx: Float,
    metrics: FastScrollMetrics,
    insetPx: Float,
    minThumbHeightPx: Float,
    maxThumbHeightPx: Float,
): FastScrollbarGeometry? {
    if (viewportPx <= 0f || metrics.viewportSizePx <= 0f || metrics.scrollRangePx <= 0f) {
        return null
    }
    val top = insetPx.coerceAtLeast(0f)
    val railHeight = (viewportPx - top * 2f).coerceAtLeast(0f)
    if (railHeight <= 0f) return null
    val minimum = minThumbHeightPx.coerceIn(0f, railHeight)
    val maximum = maxThumbHeightPx.coerceIn(minimum, railHeight)
    val thumbHeight = (railHeight * metrics.viewportSizePx / metrics.contentSizePx)
        .coerceIn(minimum, maximum)
    return FastScrollbarGeometry(
        trackTopPx = top,
        trackHeightPx = railHeight,
        thumbTopPx = top + (railHeight - thumbHeight) * metrics.fraction,
        thumbHeightPx = thumbHeight,
    )
}

/** The finger can occupy any subpixel position along the complete rail. */
internal fun fastScrollbarFractionForY(centerY: Float, geometry: FastScrollbarGeometry): Float =
    if (geometry.travelPx <= 0f) 0f
    else ((centerY - geometry.trackTopPx - geometry.thumbHeightPx / 2f) /
        geometry.travelPx).coerceIn(0f, 1f)

/** Delta scrolling preserves movement inside a single list item, unlike requestScrollToItem. */
internal fun fastScrollbarContentDelta(
    previousFraction: Float,
    nextFraction: Float,
    contentScrollRangePx: Float,
): Float =
    (nextFraction.coerceIn(0f, 1f) - previousFraction.coerceIn(0f, 1f)) *
        contentScrollRangePx.coerceAtLeast(0f)

/** Limit input to the actual grip; ordinary edge swipes remain Android Back. */
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
    val centerX = viewportWidthPx - rightInsetPx - thumbWidthPx / 2f
    return abs(xPx - centerX) <= halfTouchWidthPx &&
        yPx >= geometry.thumbTopPx - verticalSlopPx &&
        yPx <= geometry.thumbTopPx + geometry.thumbHeightPx + verticalSlopPx
}
