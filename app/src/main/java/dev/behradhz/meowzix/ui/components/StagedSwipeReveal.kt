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
 * threshold, then replace it with the action underneath over 190ms.
 * The pointer controls the song itself directly, independently of this animation.
 */
internal fun originalSwipeActionExitTarget(offsetPx: Float, secondaryThresholdPx: Float): Float {
    require(secondaryThresholdPx > 0f)
    return if (abs(offsetPx) >= secondaryThresholdPx) 1f else 0f
}

/** Layer order: Remove at the bottom, first action above it, moving song card on top.
 * At the secondary threshold the foreground action fades out before Remove fades in.
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
    // Like October's Queue swipe, wait for the second-stage threshold before
    // sliding the primary layer behind the song (instead of scrubbing it away).
    val animatedExitProgress by animateFloatAsState(
        targetValue = if (secondaryLabel != null) {
            originalSwipeActionExitTarget(offsetPx, secondaryThresholdPx)
        } else 0f,
        animationSpec = tween(durationMillis = SWIPE_ACTION_EXIT_DURATION_MS),
        label = "swipe-action-layer-exit",
    )
    // Two stationary layers share the SAME rounded gutter. Remove is below the
    // primary action, never beside it; the track itself is painted on top by
    // the caller. A two-phase fade avoids overlapping labels during handoff.
    val primaryAlpha = if (secondaryLabel == null) 1f
        else (1f - animatedExitProgress * 2f).coerceIn(0f, 1f)
    val secondaryAlpha = (animatedExitProgress * 2f - 1f).coerceIn(0f, 1f)

    // Clip once in stationary row coordinates. In particular, do not clip a
    // translated action card: that creates adjacent Play Next / Remove panels.
    // roundedSwipeReveal also rounds the newly exposed edge of the gutter.
    Box(modifier = Modifier.matchParentSize().roundedSwipeReveal(offsetPx)) {
        if (secondaryLabel != null) {
            Surface(
                modifier = Modifier.matchParentSize()
                    .graphicsLayer { alpha = secondaryAlpha },
                shape = RoundedCornerShape(18.dp),
                color = if (secondaryIsDestructive) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (secondaryIsDestructive) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 2.dp,
            ) {
                SwipeActionLabel(secondaryLabel, secondaryIcon, swipingRight)
            }
        }

        Surface(
            modifier = Modifier.matchParentSize()
                .graphicsLayer { alpha = primaryAlpha },
            shape = RoundedCornerShape(18.dp),
            color = if (swipingRight) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = if (swipingRight) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onTertiaryContainer,
            tonalElevation = 2.dp,
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
