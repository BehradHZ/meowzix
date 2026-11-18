package dev.behradhz.meowzix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Consistent glass-card spacing and swipe targets across Library and Queue. */
const val TRACK_CARD_ALPHA = 0.30f
internal const val SWIPE_ACTION_EXIT_DURATION_MS = 190
val TRACK_ROW_HORIZONTAL_PADDING = 12.dp
val TRACK_ROW_VERTICAL_PADDING = 8.dp
val TRACK_ROW_SPACING = 8.dp
val TRACK_SWIPE_ACTION_THRESHOLD = 100.dp
val TRACK_SWIPE_REMOVE_THRESHOLD = 176.dp
val TRACK_SWIPE_MAX_DISTANCE = 240.dp

/**
 * Two-stage swipe: keep the primary action visible until the secondary
 * threshold, then slide it beneath the song to uncover Remove over 190ms.
 * The pointer controls the song itself directly, independently of this animation.
 */
internal fun originalSwipeActionExitTarget(offsetPx: Float, secondaryThresholdPx: Float): Float {
    require(secondaryThresholdPx > 0f)
    return if (abs(offsetPx) >= secondaryThresholdPx) 1f else 0f
}

/**
 * Slide the primary action fully beneath the foreground card, including
 * its rounded corner cutouts. The extra overlap avoids a visible sliver
 * when the primary action completes its exit.
 */
internal fun stagedSwipeActionSlidePx(
    offsetPx: Float,
    exitProgress: Float,
    cornerOverlapPx: Float,
): Float {
    require(cornerOverlapPx >= 0f)
    val progress = exitProgress.coerceIn(0f, 1f)
    return when {
        offsetPx > 0f -> (offsetPx + cornerOverlapPx) * progress
        offsetPx < 0f -> (offsetPx - cornerOverlapPx) * progress
        else -> 0f
    }
}

/** Layer order: Remove at the bottom, first action above it, moving song card on top.
 * At the secondary threshold the first action slides entirely underneath the song.
 */
@Composable
fun BoxScope.StagedSwipeReveal(
    offsetPx: Float,
    primaryLabel: String,
    primaryIcon: ImageVector,
    secondaryLabel: String? = null,
    secondaryThresholdPx: Float = Float.POSITIVE_INFINITY,
    secondaryIcon: ImageVector = Icons.Rounded.Delete,
    secondaryIsDestructive: Boolean = true,
) {
    if (abs(offsetPx) <= 0.5f) return
    val swipingRight = offsetPx > 0f
    // Keep the primary action fully visible until the second-stage threshold;
    // the stationary action layers then trade visibility within the same gutter.
    val animatedExitProgress by animateFloatAsState(
        targetValue = if (secondaryLabel != null) {
            originalSwipeActionExitTarget(offsetPx, secondaryThresholdPx)
        } else 0f,
        animationSpec = tween(durationMillis = SWIPE_ACTION_EXIT_DURATION_MS),
        label = "swipe-action-layer-exit",
    )
    val density = LocalDensity.current
    // 18dp matches the foreground card corners; the additional 2dp keeps
    // the exiting action out of anti-aliased corner pixels at full progress.
    val cornerOverlapPx = with(density) { 20.dp.toPx() }
    val primarySlidePx = stagedSwipeActionSlidePx(
        offsetPx, animatedExitProgress, cornerOverlapPx,
    )

    // Actions are full rounded surfaces stacked underneath the foreground
    // song card. Remove stays beneath the opaque primary layer until the
    // secondary threshold; then Play Next slides out of the exposed area.
    // The moving song-card silhouette (not a vertical gutter) masks BOTH
    // action layers so nothing shines through the translucent track surface.
    Box(modifier = Modifier.matchParentSize().maskBehindSwipeTrack(offsetPx)) {
        if (secondaryLabel != null) {
            Surface(
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(18.dp),
                color = if (secondaryIsDestructive) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (secondaryIsDestructive) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 0.dp,
            ) {
                SwipeActionLabel(secondaryLabel, secondaryIcon, swipingRight)
            }
        }

        Surface(
            modifier = Modifier.matchParentSize()
                .graphicsLayer { translationX = primarySlidePx },
            shape = RoundedCornerShape(18.dp),
            // The first layer MUST be opaque or Remove leaks through before
            // the staged transition even when the main song card is masked.
            color = (if (swipingRight) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.tertiaryContainer).copy(alpha = 1f),
            contentColor = if (swipingRight) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onTertiaryContainer,
            tonalElevation = 0.dp,
        ) {
            SwipeActionLabel(primaryLabel, primaryIcon, swipingRight)
        }
    }
}

@Composable
private fun SwipeActionLabel(label: String, icon: ImageVector, swipingRight: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        horizontalArrangement = if (swipingRight) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swipingRight) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(8.dp))
            Icon(icon, contentDescription = null)
        }
    }
}
