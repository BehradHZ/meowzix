package dev.behradhz.meowzix.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.abs
import kotlin.math.max

/** Semantic haptic vocabulary for Meowzix.
 *
 * UI code asks for an interaction meaning rather than a raw vibration waveform. Android chooses
 * the closest platform haptic and the implementation intentionally respects the user's system
 * haptic setting by never using FLAG_IGNORE_GLOBAL_SETTING.
 */
enum class MeowzixHapticCue {
    Selection,
    LightClick,
    Toggle,
    Threshold,
    LongPress,
    DragStart,
    DragDrop,
    Success,
    Reject,
}

class MeowzixHaptics internal constructor(
    private val view: View,
) {
    fun perform(cue: MeowzixHapticCue) {
        val primary = primaryFeedback(cue)
        val fallback = fallbackFeedback(cue)
        val handled = view.performHapticFeedback(primary)
        if (!handled && fallback != primary) {
            view.performHapticFeedback(fallback)
        }
    }

    private fun primaryFeedback(cue: MeowzixHapticCue): Int = when (cue) {
        MeowzixHapticCue.Selection -> if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        MeowzixHapticCue.LightClick -> HapticFeedbackConstants.KEYBOARD_TAP
        MeowzixHapticCue.Toggle -> if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.TOGGLE_ON
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        MeowzixHapticCue.Threshold -> if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
        MeowzixHapticCue.LongPress -> HapticFeedbackConstants.LONG_PRESS
        MeowzixHapticCue.DragStart -> when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.DRAG_START
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.GESTURE_START
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        MeowzixHapticCue.DragDrop -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        MeowzixHapticCue.Success -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        MeowzixHapticCue.Reject -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    }

    private fun fallbackFeedback(cue: MeowzixHapticCue): Int = when (cue) {
        MeowzixHapticCue.Selection,
        MeowzixHapticCue.Threshold,
        MeowzixHapticCue.DragStart -> HapticFeedbackConstants.CLOCK_TICK

        MeowzixHapticCue.LightClick,
        MeowzixHapticCue.Success -> HapticFeedbackConstants.KEYBOARD_TAP

        MeowzixHapticCue.Toggle,
        MeowzixHapticCue.DragDrop -> HapticFeedbackConstants.CONTEXT_CLICK

        MeowzixHapticCue.LongPress,
        MeowzixHapticCue.Reject -> HapticFeedbackConstants.LONG_PRESS
    }
}

@Composable
fun rememberMeowzixHaptics(): MeowzixHaptics {
    val view = LocalView.current
    return remember(view) { MeowzixHaptics(view) }
}

/**
 * App-wide observer for subtle interaction feedback.
 *
 * It never consumes pointer input. Child controls keep full gesture ownership. Feedback is emitted
 * only after a child actually consumes a tap/long press. Scroll gestures are always silent, and
 * gestures starting in Android's bottom system-gesture area (plus the transparent clearance around
 * the floating dock) are ignored so Home/Overview gestures never trigger app haptics. Horizontal
 * gesture thresholds remain the responsibility of the component that owns each gesture.
 */
fun Modifier.meowzixInteractionHaptics(): Modifier = composed {
    val haptics = rememberMeowzixHaptics()
    val view = LocalView.current

    pointerInput(haptics, view) {
        val tapSlop = 12.dp.toPx()
        val longPressMillis = 430L
        val dockGestureClearance = 12.dp.toPx()

        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            val bottomSystemGestureInset = ViewCompat.getRootWindowInsets(view)
                ?.getInsets(WindowInsetsCompat.Type.systemGestures())
                ?.bottom
                ?: 0
            val startsInBottomSystemGestureArea =
                down.position.y >= size.height - bottomSystemGestureInset - dockGestureClearance

            var totalX = 0f
            var totalY = 0f
            var childConsumed = false
            var pressed = true
            var lastUptimeMillis = down.uptimeMillis

            while (pressed) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val delta = change.positionChange()
                totalX += delta.x
                totalY += delta.y
                lastUptimeMillis = change.uptimeMillis
                childConsumed = childConsumed || change.isConsumed
                pressed = change.pressed
            }

            if (startsInBottomSystemGestureArea || !childConsumed) return@awaitEachGesture

            val maxMovement = max(abs(totalX), abs(totalY))
            val duration = lastUptimeMillis - down.uptimeMillis
            when {
                maxMovement <= tapSlop && duration >= longPressMillis -> {
                    haptics.perform(MeowzixHapticCue.LongPress)
                }
                maxMovement <= tapSlop -> haptics.perform(MeowzixHapticCue.LightClick)
            }
        }
    }
}
