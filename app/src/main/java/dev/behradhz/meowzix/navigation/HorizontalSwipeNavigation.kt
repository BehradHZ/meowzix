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
    val threshold = 72.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        var totalX = 0f
        var blockedByChild = down.isConsumed
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.isConsumed) blockedByChild = true
            if (!blockedByChild) totalX += change.positionChange().x
            if (!change.pressed) break
        }
        if (!blockedByChild && abs(totalX) >= threshold) {
            if (totalX < 0f) onSwipeLeft() else onSwipeRight()
        }
    }
}
