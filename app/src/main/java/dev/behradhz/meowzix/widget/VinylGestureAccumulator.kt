package dev.behradhz.meowzix.widget

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToLong

/**
 * Interprets raw touch coordinates in a real Activity, never in RemoteViews.
 * In particular, a straight horizontal stroke must not be mistaken for a
 * circular record turn simply because its polar angle changes.
 */
internal sealed interface VinylGestureAction {
    data object None : VinylGestureAction
    data object Next : VinylGestureAction
    data object Previous : VinylGestureAction
    data class SeekBy(val milliseconds: Long) : VinylGestureAction
}

internal class VinylGestureAccumulator(
    private val centerX: Float,
    private val centerY: Float,
    private val startX: Float,
    private val startY: Float,
) {
    private var lastX = startX
    private var lastY = startY
    private var lastAngle = angle(startX, startY)
    private var pathLength = 0.0
    var rotationRadians: Double = 0.0
        private set

    fun move(x: Float, y: Float) {
        val distance = hypot((x - lastX).toDouble(), (y - lastY).toDouble())
        val currentAngle = angle(x, y)
        // Do not infer rotation from dragging over the center label: angle is unstable there.
        val oldRadius = radius(lastX, lastY)
        val newRadius = radius(x, y)
        if (oldRadius > 30.0 && newRadius > 30.0) {
            var delta = currentAngle - lastAngle
            while (delta > PI) delta -= 2 * PI
            while (delta < -PI) delta += 2 * PI
            rotationRadians += delta
        }
        pathLength += distance
        lastX = x
        lastY = y
        lastAngle = currentAngle
    }

    fun finish(): VinylGestureAction {
        val dx = (lastX - startX).toDouble()
        val dy = (lastY - startY).toDouble()
        val chord = hypot(dx, dy)
        if (chord < 24 && pathLength < 36) return VinylGestureAction.None
        val actuallyCurved = pathLength > chord * 1.065 && pathLength > 48.0
        if (actuallyCurved && abs(rotationRadians) >= 0.35) {
            // One quarter turn seeks five seconds. Clockwise = forward on the display.
            val seekMs = (rotationRadians / (PI / 2) * 5_000).roundToLong()
            return if (seekMs == 0L) VinylGestureAction.None else VinylGestureAction.SeekBy(seekMs)
        }
        if (abs(dx) >= 62 && abs(dx) > abs(dy) * 1.35) {
            return if (dx < 0) VinylGestureAction.Next else VinylGestureAction.Previous
        }
        return VinylGestureAction.None
    }

    private fun angle(x: Float, y: Float): Double =
        atan2((y - centerY).toDouble(), (x - centerX).toDouble())

    private fun radius(x: Float, y: Float): Double =
        hypot((x - centerX).toDouble(), (y - centerY).toDouble())
}
