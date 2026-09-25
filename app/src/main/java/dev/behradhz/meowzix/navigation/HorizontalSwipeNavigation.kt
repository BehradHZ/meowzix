package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import kotlin.math.abs

/**
 * Observes horizontal navigation gestures without stealing them from child controls.
 *
 * A child that consumes a real horizontal drag (for example a track-row queue gesture or a
 * horizontally scrolling chip row) keeps ownership. Vertical LazyColumn scrolling does not block
 * a clearly horizontal tab swipe. A successful navigation swipe gets one subtle threshold/snap
 * haptic; consumed child gestures are handled by the app-wide haptic observer instead.
 */
fun Modifier.horizontalSwipeNavigation(
    enabled: Boolean = true,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    composed {
        val haptics = rememberMeowzixHaptics()
        pointerInput(onSwipeLeft, onSwipeRight, haptics) {
            val threshold = 48.dp.toPx()
            val axisSlop = 10.dp.toPx()

            awaitEachGesture {
                val down = awaitFirstDown(
                    requireUnconsumed = false,
                    pass = PointerEventPass.Initial,
                )
                var totalX = 0f
                var totalY = 0f
                var horizontalGestureClaimedByChild = false
                var pressed = true

                while (pressed) {
                    val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
                    val initialChange =
                        initialEvent.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = initialChange.positionChange()
                    totalX += delta.x
                    totalY += delta.y

                    val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                    val finalChange =
                        finalEvent.changes.firstOrNull { it.id == down.id } ?: break

                    val horizontalIntentSoFar =
                        abs(totalX) >= axisSlop &&
                            abs(totalX) > abs(totalY) * 1.05f

                    if (finalChange.isConsumed && horizontalIntentSoFar) {
                        horizontalGestureClaimedByChild = true
                    }
                    pressed = finalChange.pressed
                }

                val horizontalIntent = abs(totalX) > abs(totalY) * 1.05f
                if (
                    !horizontalGestureClaimedByChild &&
                    horizontalIntent &&
                    abs(totalX) >= threshold
                ) {
                    if (totalX < 0f) onSwipeLeft() else onSwipeRight()
                    haptics.perform(MeowzixHapticCue.Threshold)
                }
            }
        }
    }
}
