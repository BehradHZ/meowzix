package dev.behradhz.meowzix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics

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
                    firstItemSizePx = layout.visibleItemsInfo.firstOrNull()?.size ?: 1,
                    atEnd = !state.canScrollForward,
                )
            },
            isScrolling = { state.isScrollInProgress },
            jumpTo = { state.requestScrollToItem(it) },
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
                ScrollSnapshot(
                    totalItems = layout.totalItemsCount,
                    visibleItems = layout.visibleItemsInfo.size,
                    firstIndex = state.firstVisibleItemIndex,
                    firstOffsetPx = state.firstVisibleItemScrollOffset,
                    firstItemSizePx = layout.visibleItemsInfo.firstOrNull()?.size?.height ?: 1,
                    atEnd = !state.canScrollForward,
                )
            },
            isScrolling = { state.isScrollInProgress },
            jumpTo = { state.requestScrollToItem(it) },
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
    jumpTo: (Int) -> Unit,
    enabled: Boolean,
): Modifier {
    val accent = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onSurface
    val haptics = rememberMeowzixHaptics()
    var dragging by remember(key) { mutableStateOf(false) }
    // Only animate engagement, never the thumb position or the content itself.
    val visibility by animateFloatAsState(
        targetValue = if (dragging) 1f else if (isScrolling()) 0.8f else 0.35f,
        animationSpec = tween(140),
        label = "glass-fast-scroll-visibility",
    )
    if (!enabled) return this

    return this
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val current = snapshot()
                if (current.totalItems <= current.visibleItems || current.visibleItems == 0 ||
                    down.position.x < size.width - 24.dp.toPx()
                ) return@awaitEachGesture

                dragging = true
                haptics.perform(MeowzixHapticCue.DragStart)
                try {
                    down.consume()
                    var lastIndex = -1
                    fun seek(y: Float) {
                        val data = snapshot()
                        val index = fastScrollbarIndexForY(
                            yPx = y,
                            viewportPx = size.height.toFloat(),
                            totalItems = data.totalItems,
                            visibleItems = data.visibleItems,
                            insetPx = 12.dp.toPx(),
                            minThumbHeightPx = 40.dp.toPx(),
                        )
                        if (index != lastIndex) {
                            lastIndex = index
                            jumpTo(index)
                        }
                    }
                    seek(down.position.y)
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        seek(change.position.y)
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
                insetPx = 12.dp.toPx(),
                minThumbHeightPx = 40.dp.toPx(),
                atEnd = data.atEnd,
            ) ?: return@drawWithContent

            val thumbWidth = if (dragging) 6.dp.toPx() else 4.dp.toPx()
            val rightInset = 4.dp.toPx()
            val left = size.width - rightInset - thumbWidth
            val radius = CornerRadius(thumbWidth / 2f)
            val thumbTop = geometry.thumbTopPx
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
