package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** A song card lets 70% of the background show through. */
const val TRACK_CARD_ALPHA = 0.30f

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
) {
    if (abs(offsetPx) <= 0.5f) return
    val swipingRight = offsetPx > 0f
    val transitionDistance = with(LocalDensity.current) { 72.dp.toPx() }
    val slide = if (secondaryLabel != null) {
        stagedActionSlidePx(
            offsetPx,
            secondStageStartPx = secondaryThresholdPx - transitionDistance,
            transitionDistancePx = transitionDistance,
        )
    } else 0f

    if (secondaryLabel != null && abs(slide) > 0.5f) {
        Surface(
            // Reveal only the section no longer covered by the first panel.
            // Avoid red bleeding through the translucent Play next/Add layer.
            modifier = Modifier.matchParentSize().roundedSwipeReveal(slide),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.78f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            tonalElevation = 0.dp,
        ) {
            SwipeActionLabel(secondaryLabel, Icons.Rounded.Delete, swipingRight)
        }
    }

    // The mask is in the translated card's local coordinates, so the primary
    // panel is visible only between the moving foreground and the Remove panel.
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

@Composable
private fun SwipeActionLabel(label: String, icon: ImageVector, swipingRight: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = if (swipingRight) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swipingRight) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.size(7.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(7.dp))
            Icon(icon, contentDescription = null)
        }
    }
}
