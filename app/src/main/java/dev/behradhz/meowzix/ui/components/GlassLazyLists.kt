package dev.behradhz.meowzix.ui.components

import android.graphics.Rect as AndroidRect
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

/** Shared glass fast-scrollbar: native pixel scrolling, no extra layout or overlay. */
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
            scrollable = state,
            indicator = { state.scrollIndicatorState },
            isScrolling = { state.isScrollInProgress },
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
            scrollable = state,
            indicator = { state.scrollIndicatorState },
            isScrolling = { state.isScrollInProgress },
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

private fun scrollMetrics(indicator: ScrollIndicatorState?): FastScrollMetrics? =
    indicator?.let {
        val metrics = FastScrollMetrics(
            scrollOffsetPx = it.scrollOffset.toFloat(),
            contentSizePx = it.contentSize.toFloat(),
            viewportSizePx = it.viewportSize.toFloat(),
        )
        metrics.takeIf { value -> value.scrollRangePx > 0f && value.viewportSizePx > 0f }
    }

@Composable
private fun Modifier.glassFastScroll(
    key: Any,
    scrollable: ScrollableState,
    indicator: () -> ScrollIndicatorState?,
    isScrolling: () -> Boolean,
    enabled: Boolean,
): Modifier {
    val accent = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onSurface
    val haptics = rememberMeowzixHaptics()
    val density = LocalDensity.current
    val view = LocalView.current

    // Keep the grip in its original, visually flush position. Android Back is only excluded
    // inside the moving handle, not across the height of the screen.
    val rightInsetPx = with(density) { 4.dp.toPx() }
    val thumbWidthPx = with(density) { 6.dp.toPx() }
    val halfTouchWidthPx = with(density) { 12.dp.toPx() }
    val verticalSlopPx = with(density) { 8.dp.toPx() }
    val railInsetPx = with(density) { 12.dp.toPx() }
    val minThumbHeightPx = with(density) { 44.dp.toPx() }
    val maxThumbHeightPx = with(density) { 96.dp.toPx() }
    var dragging by remember(key) { mutableStateOf(false) }
    var dragFraction by remember(key) { mutableFloatStateOf(0f) }
    var boundsInRoot by remember(key) { mutableStateOf<Rect?>(null) }
    val visibility by animateFloatAsState(
        targetValue = if (dragging) 1f else if (isScrolling()) 0.8f else 0.35f,
        animationSpec = tween(140),
        label = "glass-fast-scroll-visibility",
    )
    if (!enabled) return this

    // A narrow, updated gesture-exclusion rectangle moves with the thumb. Excluding the
    // entire right edge would disable Back over every list; moving the indicator inward is
    // not necessary. Preserve exclusions registered by other Compose controls.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        LaunchedEffect(view, key, rightInsetPx, railInsetPx) {
            var owned: AndroidRect? = null
            fun replaceOwned(next: AndroidRect?) {
                if (owned == next) return
                val all = view.systemGestureExclusionRects.toMutableList()
                owned?.let { all.remove(it) }
                if (next != null && !next.isEmpty) all.add(next)
                view.systemGestureExclusionRects = all
                owned = next
            }
            try {
                snapshotFlow {
                    val bounds = boundsInRoot
                    val metrics = scrollMetrics(indicator())
                    if (bounds == null || metrics == null) null else {
                        val geometry = fastScrollbarGeometry(
                            bounds.height, metrics, railInsetPx,
                            minThumbHeightPx, maxThumbHeightPx,
                        )
                        geometry?.let { g ->
                            AndroidRect(
                                (bounds.right - with(density) { 24.dp.toPx() }).roundToInt(),
                                (bounds.top + g.thumbTopPx - verticalSlopPx).roundToInt(),
                                bounds.right.roundToInt(),
                                (bounds.top + g.thumbTopPx + g.thumbHeightPx + verticalSlopPx)
                                    .roundToInt(),
                            )
                        }
                    }
                }.distinctUntilChanged().collect { replaceOwned(it) }
            } finally {
                replaceOwned(null)
            }
        }
    }

    return this
        .onGloballyPositioned { boundsInRoot = it.boundsInRoot() }
        .pointerInput(key, rightInsetPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val metrics = scrollMetrics(indicator()) ?: return@awaitEachGesture
                val geometry = fastScrollbarGeometry(
                    viewportPx = size.height.toFloat(),
                    metrics = metrics,
                    insetPx = railInsetPx,
                    minThumbHeightPx = minThumbHeightPx,
                    maxThumbHeightPx = maxThumbHeightPx,
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

                val grabOffsetY = down.position.y - geometry.thumbCenterPx
                dragFraction = metrics.fraction
                dragging = true
                haptics.perform(MeowzixHapticCue.DragStart)

                // Apply each drag delta synchronously. A deferred/conflated worker
                // can lose a short scrub when Down, Move and Up arrive in one frame.
                // The scrollbar itself owns this pointer; do not start a competing
                // scroll coroutine that can outlive the finished gesture.
                var appliedFraction = metrics.fraction
                try {
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val fraction = fastScrollbarFractionForY(
                            change.position.y - grabOffsetY, geometry,
                        )
                        dragFraction = fraction
                        val delta = fastScrollbarContentDelta(
                            appliedFraction, fraction, metrics.scrollRangePx,
                        )
                        appliedFraction = fraction
                        if (delta != 0f) scrollable.dispatchRawDelta(delta)
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
            val metrics = scrollMetrics(indicator()) ?: return@drawWithContent
            val geometry = fastScrollbarGeometry(
                viewportPx = size.height,
                metrics = metrics,
                insetPx = railInsetPx,
                minThumbHeightPx = minThumbHeightPx,
                maxThumbHeightPx = maxThumbHeightPx,
            ) ?: return@drawWithContent

            val thumbWidth = if (dragging) thumbWidthPx else 4.dp.toPx()
            val left = size.width - rightInsetPx - thumbWidth
            val radius = CornerRadius(thumbWidth / 2f)
            val thumbTop = if (dragging) {
                geometry.trackTopPx + geometry.travelPx * dragFraction
            } else geometry.thumbTopPx
            val thumbHeight = geometry.thumbHeightPx
            drawRoundRect(
                foreground.copy(alpha = 0.035f * visibility),
                topLeft = Offset(size.width - rightInsetPx - 2.dp.toPx(), geometry.trackTopPx),
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
