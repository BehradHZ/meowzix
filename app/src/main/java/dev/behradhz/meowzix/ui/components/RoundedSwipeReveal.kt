package dev.behradhz.meowzix.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clips a swipe action to the uncovered gutter, not the full translucent track card.
 * Both exposed edges are rounded, avoiding a rectangular cut during drag/settle.
 * [offsetPx] is the translated foreground card's current horizontal offset in pixels.
 */
fun Modifier.roundedSwipeReveal(offsetPx: Float, radius: Dp = 18.dp): Modifier =
    drawWithContent {
        val offset = offsetPx.coerceIn(-size.width, size.width)
        if (offset == 0f) return@drawWithContent
        val left = if (offset > 0f) 0f else size.width + offset
        val right = if (offset > 0f) offset else size.width
        if (right <= left) return@drawWithContent
        val corner = minOf(radius.toPx(), (right - left) / 2f, size.height / 2f)
        val gutter = Path().apply {
            addRoundRect(RoundRect(left, 0f, right, size.height, CornerRadius(corner)))
        }
        clipPath(gutter) { this@drawWithContent.drawContent() }
    }
