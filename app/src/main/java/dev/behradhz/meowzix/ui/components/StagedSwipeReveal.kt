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
import androidx.compose.ui.platform.LocalDensity
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
 * Slide the first action *under the foreground song card* during a deeper pull.
 * The exposed part of the second (Remove) layer grows smoothly in its place.
 * Both swipe directions have identical thresholds and animations.
 */
internal fun stagedActionSlidePx(
    offsetPx: Float,
    secondStageStartPx: Float,
    transitionDistancePx: Float,
): Float {
    require(secondStageStartPx > 0f && transitionDistancePx > 0f)
    val progress = ((abs(offsetPx) - secondStageStartPx) / transitionDistancePx)
        .coerceIn(0f, 1f)
    return offsetPx * progress
}

/** Layer order: Remove at the bottom, first action above it, moving song card on top.
 * At the destructive threshold the action is fully tucked behind the song and Remove is exposed.
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
    // Restore the original quick 190ms tuck of the middle action underneath
    // the foreground song. The first small pull still exposes Play next/Add.
    val transitionDistance = with(LocalDensity.current) { 72.dp.toPx() }
    val slideTarget = if (secondaryLabel != null) {
        stagedActionSlidePx(
            offsetPx,
            secondStageStartPx = secondaryThresholdPx - transitionDistance,
            transitionDistancePx = transitionDistance,
        )
    } else 0f
    val animatedExitProgress by animateFloatAsState(
        targetValue = if (offsetPx == 0f) 0f else slideTarget / offsetPx,
        animationSpec = tween(durationMillis = SWIPE_ACTION_EXIT_DURATION_MS),
        label = "swipe-action-layer-exit",
    )
    val slide = offsetPx * animatedExitProgress

    // This clip MUST live in the stationary row's coordinates, not in the
    // translated action layer. The foreground has alpha=0.30: an action
    // rendered even one pixel under it would bleed through the glass.
    // Paint nothing except the gutter actually uncovered by the song card.
    Box(modifier = Modifier.matchParentSize().roundedSwipeReveal(offsetPx)) {
        if (secondaryLabel != null && abs(slide) > 0.5f) {
            Surface(
                modifier = Modifier.matchParentSize().roundedSwipeReveal(slide),
                shape = RoundedCornerShape(18.dp),
                color = (if (secondaryIsDestructive) MaterialTheme.colorScheme.errorContainer
                         else MaterialTheme.colorScheme.primaryContainer).copy(alpha = 0.78f),
                contentColor = if (secondaryIsDestructive) MaterialTheme.colorScheme.onErrorContainer
                               else MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 0.dp,
            ) {
                SwipeActionLabel(secondaryLabel, secondaryIcon, swipingRight)
            }
        }

        // The primary and Remove layers occupy disjoint parts of the exposed
        // gutter. Clipping the parent as well as the individual layers prevents
        // the primary card or its translucent Surface from showing through the
        // foreground while the original fast animation is in flight.
        Surface(
            modifier = Modifier.matchParentSize()
                .graphicsLayer { translationX = slide }
                .roundedSwipeReveal(offsetPx - slide),
            shape = RoundedCornerShape(18.dp),
            color = (if (swipingRight) MaterialTheme.colorScheme.primaryContainer
                     else MaterialTheme.colorScheme.tertiaryContainer).copy(alpha = 0.78f),
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalArrangement = if (swipingRight) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swipingRight) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(5.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        } else {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(5.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}
