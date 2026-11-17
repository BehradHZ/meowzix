package dev.behradhz.meowzix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import kotlin.math.roundToInt

/** A single, subtle fast-scroller for the app's vertical list surfaces. */
@Composable
fun GlassLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.glassFastScroll(
            key = state,
            snapshot = {
                val layout = state.layoutInfo
                ScrollSnapshot(
                    totalItems = layout.totalItemsCount,
                    visibleItems = layout.visibleItemsInfo.size,
                    firstIndex = state.firstVisibleItemIndex,
                    firstOffsetPx = state.firstVisibleItemScrollOffset,
                    firstItemSizePx = layout.visibleItemsInfo.map { it.size }
                        .filter { it > 0 }.let { sizes ->
                            if (sizes.isEmpty()) 1 else sizes.average().roundToInt().coerceAtLeast(1)
                        },
                    atEnd = !state.canScrollForward,
                )
            },
            isScrolling = { state.isScrollInProgress },
            jumpTo = { target -> state.requestScrollToItem(target.index, target.offsetPx) },
            enabled = userScrollEnabled,
        ),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

/** Grid variant uses exactly the same gesture, dimensions, and materials as lists. */
@Composable
fun GlassLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalArrangement: Arrangement.Vertical = if (!reverseLayout) Arrangement.Top else Arrangement.Bottom,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.glassFastScroll(
            key = state,
            snapshot = {
                val layout = state.layoutInfo
                val columns = layout.maxSpan.coerceAtLeast(1)
                val visibleRows = layout.visibleItemsInfo.map { it.row }
                    .filter { it >= 0 }.distinct().size
                val rowHeights = layout.visibleItemsInfo.map { it.size.height }
                    .filter { it > 0 }
                ScrollSnapshot(
                    totalItems = (layout.totalItemsCount + columns - 1) / columns,
                    visibleItems = visibleRows.coerceAtLeast(1),
                    firstIndex = state.firstVisibleItemIndex / columns,
                    firstOffsetPx = state.firstVisibleItemScrollOffset,
                    firstItemSizePx = if (rowHeights.isEmpty()) 1
                        else rowHeights.average().roundToInt().coerceAtLeast(1),
                    atEnd = !state.canScrollForward,
                )
            },
            isScrolling = { state.isScrollInProgress },
            jumpTo = { target ->
                val columns = state.layoutInfo.maxSpan.coerceAtLeast(1)
                val last = state.layoutInfo.totalItemsCount - 1
                if (last >= 0) state.requestScrollToItem(
                    (target.index * columns).coerceAtMost(last), target.offsetPx,
                )
            },
            enabled = userScrollEnabled,
        ),
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}

private data class ScrollSnapshot(
    val totalItems: Int,
    val visibleItems: Int,
    val firstIndex: Int,
    val firstOffsetPx: Int,
    val firstItemSizePx: Int,
    val atEnd: Boolean,
)

@Composable
private fun Modifier.glassFastScroll(
    key: Any,
    snapshot: () -> ScrollSnapshot,
    isScrolling: () -> Boolean,
    jumpTo: (FastScrollbarTarget) -> Unit,
    enabled: Boolean,
): Modifier {
    val accent = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onSurface
    val haptics = rememberMeowzixHaptics()
    val density = LocalDensity.current
    // Leave the Back gesture strip untouched; the grip is inset from that strip.
    val rightInsetPx = maxOf(
        WindowInsets.systemGestures.getRight(density).toFloat(),
        with(density) { 24.dp.toPx() },
    ) + with(density) { 18.dp.toPx() }
    val thumbWidthPx = with(density) { 6.dp.toPx() }
    val halfTouchWidthPx = with(density) { 12.dp.toPx() }
    val verticalSlopPx = with(density) { 10.dp.toPx() }
    val railInsetPx = with(density) { 12.dp.toPx() }
    val minThumbHeightPx = with(density) { 40.dp.toPx() }
    var dragging by remember(key) { mutableStateOf(false) }
    var dragFraction by remember(key) { mutableFloatStateOf(0f) }
    // Only animate engagement, never the thumb position or the content itself.
    val visibility by animateFloatAsState(
        targetValue = if (dragging) 1f else if (isScrolling()) 0.8f else 0.35f,
        animationSpec = tween(140),
        label = "glass-fast-scroll-visibility",
    )
    if (!enabled) return this

    return this
        .pointerInput(key, rightInsetPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val initial = snapshot()
                val geometry = fastScrollbarGeometry(
                    viewportPx = size.height.toFloat(),
                    totalItems = initial.totalItems,
                    visibleItems = initial.visibleItems,
                    firstIndex = initial.firstIndex,
                    firstOffsetPx = initial.firstOffsetPx,
                    firstItemSizePx = initial.firstItemSizePx,
                    insetPx = railInsetPx,
                    minThumbHeightPx = minThumbHeightPx,
                    atEnd = initial.atEnd,
                ) ?: return@awaitEachGesture
                if (!fastScrollbarThumbHitTest(
                        xPx = down.position.x,
                        yPx = down.position.y,
                        viewportWidthPx = size.width.toFloat(),
                        rightInsetPx = rightInsetPx,
                        thumbWidthPx = thumbWidthPx,
                        halfTouchWidthPx = halfTouchWidthPx,
                        verticalSlopPx = verticalSlopPx,
                        geometry = geometry,
                    )
                ) return@awaitEachGesture

                // Avoid any jump just from grabbing the thumb off-center.
                val grabOffsetY = down.position.y - (geometry.thumbTopPx + geometry.thumbHeightPx / 2f)
                dragFraction = ((geometry.thumbTopPx - geometry.trackTopPx) /
                    (geometry.trackHeightPx - geometry.thumbHeightPx).coerceAtLeast(1f))
                    .coerceIn(0f, 1f)
                dragging = true
                haptics.perform(MeowzixHapticCue.DragStart)
                try {
                    down.consume()
                    var lastTarget: FastScrollbarTarget? = null
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val fraction = fastScrollbarFractionForY(change.position.y - grabOffsetY, geometry)
                        dragFraction = fraction
                        // Pixel offset gives continuous, intermediate positions inside each row.
                        // Freeze geometry/height during the gesture to avoid variable-row jitter.
                        val target = fastScrollbarTargetForFraction(
                            fraction = fraction,
                            totalItems = initial.totalItems,
                            visibleItems = initial.visibleItems,
                            estimatedItemSizePx = initial.firstItemSizePx.toFloat(),
                        )
                        if (target != lastTarget) {
                            lastTarget = target
                            jumpTo(target)
                        }
                        change.consume()
                    }
                } finally {
                    dragging = false
                    haptics.perform(MeowzixHapticCue.DragDrop)
                }
            }
        }
        .drawWithContent {
            drawContent()
            val data = snapshot()
            val geometry = fastScrollbarGeometry(
                viewportPx = size.height,
                totalItems = data.totalItems,
                visibleItems = data.visibleItems,
                firstIndex = data.firstIndex,
                firstOffsetPx = data.firstOffsetPx,
                firstItemSizePx = data.firstItemSizePx,
                insetPx = railInsetPx,
                minThumbHeightPx = minThumbHeightPx,
                atEnd = data.atEnd,
            ) ?: return@drawWithContent

            val thumbWidth = if (dragging) 6.dp.toPx() else 4.dp.toPx()
            val rightInset = rightInsetPx
            val left = size.width - rightInset - thumbWidth
            val radius = CornerRadius(thumbWidth / 2f)
            val thumbTop = if (dragging) {
                geometry.trackTopPx + (geometry.trackHeightPx - geometry.thumbHeightPx) * dragFraction
            } else geometry.thumbTopPx
            val thumbHeight = geometry.thumbHeightPx
            // An understated rail and a layered, frosted-glass-looking thumb.
            drawRoundRect(
                foreground.copy(alpha = 0.035f * visibility),
                topLeft = Offset(size.width - rightInset - 2.dp.toPx(), geometry.trackTopPx),
                size = Size(2.dp.toPx(), geometry.trackHeightPx),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            drawRoundRect(
                accent.copy(alpha = 0.12f * visibility),
                topLeft = Offset(left - 3.dp.toPx(), thumbTop),
                size = Size(thumbWidth + 6.dp.toPx(), thumbHeight),
                cornerRadius = CornerRadius(8.dp.toPx()),
            )
            drawRoundRect(
                foreground.copy(alpha = 0.12f * visibility),
                topLeft = Offset(left, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = radius,
            )
            drawRoundRect(
                accent.copy(alpha = 0.50f * visibility),
                topLeft = Offset(left, thumbTop),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = radius,
            )
            drawRoundRect(
                Color.White.copy(alpha = 0.55f * visibility),
                topLeft = Offset(left + 1.dp.toPx(), thumbTop + 3.dp.toPx()),
                size = Size(1.dp.toPx(), (thumbHeight - 6.dp.toPx()).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(1.dp.toPx()),
            )
        }
}
