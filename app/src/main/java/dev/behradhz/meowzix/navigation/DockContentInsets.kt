package dev.behradhz.meowzix.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class DockLayout(
    val compact: Boolean = false,
    val reduceMotion: Boolean = false,
)

internal val LocalDockLayout = compositionLocalOf { DockLayout() }

/** Allow content to use the lower edge when the dock is small, without obscuring controls. */
@Composable
internal fun dockAwareBottomPadding(expanded: Dp): Dp {
    val layout = LocalDockLayout.current
    val target = if (layout.compact) (expanded - 78.dp).coerceAtLeast(88.dp) else expanded
    val value by animateDpAsState(
        targetValue = target,
        animationSpec = if (layout.reduceMotion) snap() else tween(durationMillis = 260),
        label = "dock-content-padding",
    )
    return value
}

/** Only an unconsumed upward drag at a scroll boundary should reopen the dock. */
internal fun shouldExpandDockAtEnd(
    isUserInput: Boolean,
    searchActive: Boolean,
    compact: Boolean,
    unconsumedDy: Float,
): Boolean = isUserInput && !searchActive && compact && unconsumedDy < -1f
