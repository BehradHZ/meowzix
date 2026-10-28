package dev.behradhz.meowzix.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap

/**
 * Shared choreography for the floating dock and the mini player.
 *
 * Two eased keyframe segments bring the geometry almost to rest before the
 * final settle. Both directions use mirrored targets, so scroll reversals
 * remain continuous rather than snapping back through a one-way animation.
 */
internal fun dockMorphSpec(
    compact: Boolean,
    reduceMotion: Boolean,
): FiniteAnimationSpec<Float> {
    if (reduceMotion) return snap()
    val ease = CubicBezierEasing(0.20f, 0.72f, 0.30f, 1f)
    return keyframes {
        durationMillis = 440
        (if (compact) 0.76f else 0.24f) at 275 using ease
        (if (compact) 0.96f else 0.04f) at 385 using ease
    }
}

/** A subtle transient motion blur, not a permanent blur of labels or album art. */
internal fun dockMotionBlurDp(progress: Float, reduceMotion: Boolean): Float {
    if (reduceMotion) return 0f
    val clamped = progress.coerceIn(0f, 1f)
    return 1.6f * 4f * clamped * (1f - clamped)
}

/** Ensures the two compact buttons have the same circular geometry. */
internal const val CompactDockButtonDp = 52
internal const val CompactMiniPlayerInsetDp = 80
