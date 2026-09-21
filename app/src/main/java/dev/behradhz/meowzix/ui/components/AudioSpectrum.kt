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
    bands: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    SpectrumCanvas(
        bandCount = bands.size,
        levelAt = bands::get,
        modifier = modifier,
        color = color,
    )
}

/** Compact derived spectra can stay list-backed without forcing callers to copy again. */
@Composable
fun AudioSpectrum(
    bands: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    SpectrumCanvas(
        bandCount = bands.size,
        levelAt = bands::get,
        modifier = modifier,
        color = color,
    )
}

@Composable
private fun SpectrumCanvas(
    bandCount: Int,
    levelAt: (Int) -> Float,
    modifier: Modifier,
    color: Color,
) {
    Canvas(modifier = modifier) {
        if (bandCount == 0) return@Canvas

        val gap = 3.5f
        val totalGap = gap * (bandCount - 1)
        val barWidth = ((size.width - totalGap) / bandCount).coerceAtLeast(1f)
        val centerY = size.height / 2f
        val minimumHeight = 4f

        for (index in 0 until bandCount) {
            val normalized = levelAt(index).coerceIn(0f, 1f).pow(0.72f)
            val barHeight = minimumHeight + normalized * (size.height - minimumHeight)
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
