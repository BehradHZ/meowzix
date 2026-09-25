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
 * only after a child actually consumes the gesture, so tapping inert/empty UI does not vibrate.
 * Vertical scrolling is deliberately silent. Horizontal action gestures get one threshold tick and
 * one soft settle tick. A vertical drag that starts after a long press is treated as reorder/drag
 * affordance, which covers queue reordering without making ordinary list scrolling vibrate.
 */
fun Modifier.meowzixInteractionHaptics(): Modifier = composed {
    val haptics = rememberMeowzixHaptics()

    pointerInput(haptics) {
        val tapSlop = 12.dp.toPx()
        val horizontalThreshold = 56.dp.toPx()
        val longPressDragSlop = 8.dp.toPx()
        val longPressMillis = 430L

        awaitEachGesture {
            val down = awaitFirstDown(
                requireUnconsumed = false,
                pass = PointerEventPass.Initial,
            )
            var totalX = 0f
            var totalY = 0f
            var childConsumed = false
            var horizontalThresholdSent = false
            var longPressDragStarted = false
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

                val elapsed = lastUptimeMillis - down.uptimeMillis
                val horizontalIntent = abs(totalX) > abs(totalY) * 1.15f
                val verticalIntent = abs(totalY) > abs(totalX) * 1.15f

                if (
                    childConsumed &&
                    !horizontalThresholdSent &&
                    horizontalIntent &&
                    abs(totalX) >= horizontalThreshold
                ) {
                    horizontalThresholdSent = true
                    haptics.perform(MeowzixHapticCue.Threshold)
                }

                if (
                    childConsumed &&
                    !longPressDragStarted &&
                    elapsed >= longPressMillis &&
                    verticalIntent &&
                    abs(totalY) >= longPressDragSlop
                ) {
                    longPressDragStarted = true
                    haptics.perform(MeowzixHapticCue.DragStart)
                }

                pressed = change.pressed
            }

            if (!childConsumed) return@awaitEachGesture

            val maxMovement = max(abs(totalX), abs(totalY))
            val duration = lastUptimeMillis - down.uptimeMillis
            when {
                longPressDragStarted -> haptics.perform(MeowzixHapticCue.DragDrop)
                horizontalThresholdSent -> haptics.perform(MeowzixHapticCue.DragDrop)
                maxMovement <= tapSlop && duration >= longPressMillis -> {
                    haptics.perform(MeowzixHapticCue.LongPress)
                }
                maxMovement <= tapSlop -> haptics.perform(MeowzixHapticCue.LightClick)
            }
        }
    }
}
