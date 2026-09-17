package dev.behradhz.meowzix.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * Draws normalized FFT bands exactly as supplied by the playback visualizer.
 * No random or time-generated motion is introduced here.
 */
@Composable
fun AudioSpectrum(
    bands: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        if (bands.isEmpty()) return@Canvas

        val gap = 3.5f
        val totalGap = gap * (bands.size - 1)
        val barWidth = ((size.width - totalGap) / bands.size).coerceAtLeast(1f)
        val centerY = size.height / 2f
        val minimumHeight = 4f

        bands.forEachIndexed { index, level ->
            val normalized = level.coerceIn(0f, 1f).pow(0.72f)
            val barHeight = minimumHeight +
                normalized * (size.height - minimumHeight)
            val x = index * (barWidth + gap)
            val top = centerY - barHeight / 2f

            drawRoundRect(
                color = color.copy(alpha = 0.38f + normalized * 0.62f),
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(
                    x = barWidth / 2f,
                    y = barWidth / 2f,
                ),
            )
        }
    }
}
