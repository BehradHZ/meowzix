package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

fun Modifier.horizontalSwipeNavigation(
    enabled: Boolean = true,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(onSwipeLeft, onSwipeRight) {
    val threshold = 64.dp.toPx()
    val axisSlop = 12.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var totalX = 0f
        var totalY = 0f
        var childClaimedHorizontalDrag = false
        var pressed = true

        while (pressed) {
            val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
            val initialChange = initialEvent.changes.firstOrNull { it.id == down.id } ?: break
            val delta = initialChange.positionChange()
            totalX += delta.x
            totalY += delta.y

            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
            val finalChange = finalEvent.changes.firstOrNull { it.id == down.id } ?: break
            if (
                finalChange.isConsumed &&
                abs(totalX) >= axisSlop &&
                abs(delta.x) > abs(delta.y)
            ) {
                childClaimedHorizontalDrag = true
            }
            pressed = finalChange.pressed
        }

        val horizontalIntent = abs(totalX) > abs(totalY) * 1.15f
        if (!childClaimedHorizontalDrag && horizontalIntent && abs(totalX) >= threshold) {
            if (totalX < 0f) onSwipeLeft() else onSwipeRight()
        }
    }
}
