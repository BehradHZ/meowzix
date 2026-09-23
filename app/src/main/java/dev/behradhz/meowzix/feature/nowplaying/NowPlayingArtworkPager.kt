package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.QueueState
import dev.behradhz.meowzix.ui.components.NowPlayingArtwork
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Queue-backed artwork pager for Now Playing.
 *
 * The pager viewport is exactly the artwork size, so adjacent covers meet at the moving seam while
 * swiping instead of leaving a gutter. Keeping one page beyond the viewport composed also warms the
 * previous/next artwork before it is exposed. Playback remains the source of truth: user-settled
 * pages issue one Previous/Next command, while external playback changes animate the pager to the
 * new queue index without feeding back into playback.
 */
@Composable
internal fun NowPlayingArtworkPager(
    state: PlaybackState,
    queueState: QueueState,
    fallbackArtworkRef: String?,
    fallbackArtworkDescription: String,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeIndex = remember(
        state.currentTrack?.id,
        state.queueIndex,
        queueState.currentIndex,
        queueState.items,
    ) {
        resolveActiveQueueIndex(state, queueState)
    }

    val density = LocalDensity.current
    val closeThresholdPx = with(density) { 96.dp.toPx() }
    var verticalDragPx by remember(state.currentTrack?.id) { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier.pointerInput(state.currentTrack?.id) {
            detectVerticalDragGestures(
                onDragStart = { verticalDragPx = 0f },
                onVerticalDrag = { change, dragAmount ->
                    if (dragAmount > 0f || verticalDragPx > 0f) {
                        change.consume()
                        verticalDragPx = (verticalDragPx + dragAmount).coerceAtLeast(0f)
                    }
                },
                onDragCancel = { verticalDragPx = 0f },
                onDragEnd = {
                    if (verticalDragPx >= closeThresholdPx) onBack()
                    verticalDragPx = 0f
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        val artworkSize = minOf(maxWidth * 0.90f, maxHeight * 0.90f)
        val queueAvailable = activeIndex in queueState.items.indices && queueState.items.isNotEmpty()

        if (!queueAvailable) {
            NowPlayingArtwork(
                artworkRef = fallbackArtworkRef,
                description = fallbackArtworkDescription,
                modifier = Modifier.size(artworkSize),
            )
            return@BoxWithConstraints
        }

        val pagerState = rememberPagerState(
            initialPage = activeIndex,
            pageCount = { queueState.items.size },
        )
        val latestActiveIndex by rememberUpdatedState(activeIndex)
        val latestQueueSize by rememberUpdatedState(queueState.items.size)
        val latestCanSkipPrevious by rememberUpdatedState(state.canSkipPrevious)
        val latestCanSkipNext by rememberUpdatedState(state.canSkipNext)
        val latestPrevious by rememberUpdatedState(onPrevious)
        val latestNext by rememberUpdatedState(onNext)

        // Button presses, auto-advance, queue restoration, and external playback changes all drive
        // the same artwork motion. Retargeting this effect cancels any in-flight animation cleanly.
        LaunchedEffect(activeIndex, queueState.items.size) {
            if (activeIndex in queueState.items.indices && pagerState.currentPage != activeIndex) {
                pagerState.animateScrollToPage(activeIndex)
            }
        }

        // Only a user-settled page that differs from playback may drive a skip. Programmatic pager
        // motion finishes after the playback index has already changed, so it is ignored here.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.isScrollInProgress to pagerState.currentPage }
                .distinctUntilChanged()
                .filter { (isScrolling, _) -> !isScrolling }
                .collect { (_, settledPage) ->
                    val playbackIndex = latestActiveIndex
                    when {
                        settledPage > playbackIndex && latestCanSkipNext -> latestNext()
                        settledPage < playbackIndex && latestCanSkipPrevious -> latestPrevious()
                        settledPage != playbackIndex && playbackIndex in 0 until latestQueueSize -> {
                            pagerState.animateScrollToPage(playbackIndex)
                        }
                    }
                }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.size(artworkSize),
            beyondViewportPageCount = 1,
            userScrollEnabled = state.canSkipPrevious || state.canSkipNext,
            key = { page -> "now-playing-${queueState.items[page].id}-$page" },
        ) { page ->
            val item = queueState.items[page]
            val signedOffset = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            ).coerceIn(-1f, 1f)
            val progress = signedOffset.absoluteValue
            val seamPivot = if (signedOffset >= 0f) 1f else 0f
            val cameraDistancePx = with(density) { 32.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // A restrained edge-pivoted transform gives the shared seam a page-turn
                        // feel while the pager itself handles the physical translation.
                        transformOrigin = TransformOrigin(seamPivot, 0.5f)
                        rotationY = -8f * signedOffset
                        scaleX = 1f - (0.10f * progress)
                        scaleY = 1f - (0.025f * progress)
                        alpha = 1f - (0.05f * progress)
                        cameraDistance = cameraDistancePx
                    },
                contentAlignment = Alignment.Center,
            ) {
                NowPlayingArtwork(
                    artworkRef = if (page == activeIndex) {
                        item.artworkRef ?: fallbackArtworkRef
                    } else {
                        item.artworkRef
                    },
                    description = "${item.title} cover art",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun resolveActiveQueueIndex(
    state: PlaybackState,
    queueState: QueueState,
): Int {
    val currentTrackId = state.currentTrack?.id ?: return -1

    if (
        state.queueIndex in queueState.items.indices &&
        queueState.items[state.queueIndex].id == currentTrackId
    ) {
        return state.queueIndex
    }

    if (
        queueState.currentIndex in queueState.items.indices &&
        queueState.items[queueState.currentIndex].id == currentTrackId
    ) {
        return queueState.currentIndex
    }

    return queueState.items.indexOfFirst { it.id == currentTrackId }
}
