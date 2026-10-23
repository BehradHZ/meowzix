package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.playback.PlaybackStatus

/** A transient buffer must not retract the stylus; a genuine pause must. */
internal fun tonearmParked(status: PlaybackStatus): Boolean =
    status == PlaybackStatus.PAUSED || status == PlaybackStatus.IDLE || status == PlaybackStatus.ERROR

/** Share the platter angle across swipe fragments and read it only from a draw-layer. */
@Composable
internal fun rememberVinylAngle(isPlaying: Boolean, reduceMotion: Boolean): State<Float> {
    val angle = remember { Animatable(0f) }
    var hasPlayed by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying, reduceMotion) {
        if (reduceMotion) {
            angle.snapTo(0f)
        } else if (isPlaying) {
            hasPlayed = true
            while (true) {
                angle.animateTo(
                    targetValue = angle.value + 360f,
                    animationSpec = tween(durationMillis = 5_600, easing = LinearEasing),
                )
                angle.snapTo(angle.value % 360f)
            }
        } else if (hasPlayed) {
            angle.animateTo(
                targetValue = angle.value + 13f,
                animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
            )
        }
    }
    return angle.asState()
}

@Composable
internal fun VinylRecord(
    artworkRef: String?,
    description: String,
    angle: State<Float>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Vinyl record: " + description
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = angle.value }
                .clip(CircleShape).background(Color(0xFF090A0D)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to Color(0xFF25262A),
                        0.28f to Color(0xFF141518),
                        0.62f to Color(0xFF222327),
                        0.84f to Color(0xFF0B0C0F),
                        1.00f to Color(0xFF1E2024),
                        center = center, radius = r,
                    ),
                    radius = r,
                )
                repeat(35) { i ->
                    drawCircle(
                        color = Color.White.copy(alpha = if (i % 5 == 0) 0.105f else 0.032f),
                        radius = r * (0.235f + i * 0.0071f),
                        style = Stroke(width = 0.65.dp.toPx()),
                    )
                }
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = r - 1.7.dp.toPx(), style = Stroke(width = 0.9.dp.toPx()),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.58f),
                    radius = r * 0.222f, style = Stroke(width = 3.dp.toPx()),
                )
            }
            Box(
                modifier = Modifier.fillMaxSize(0.425f).clip(CircleShape)
                    .background(Color(0xFF302A27))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            )
            dev.behradhz.meowzix.ui.components.NowPlayingArtwork(
                artworkRef = artworkRef,
                description = description,
                showShadow = false,
                cornerRadius = 1_000.dp,
                modifier = Modifier.fillMaxSize(0.393f),
            )
            Box(
                modifier = Modifier.size(7.dp).clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFF9F5E9), Color(0xFF92969C), Color(0xFF34383D)),
                        ),
                    )
                    .border(0.5.dp, Color.Black.copy(alpha = 0.65f), CircleShape),
            )
        }
        // The reflected light remains stationary even though the physical grooves rotate.
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.075f),
                        Color.Transparent, Color.White.copy(alpha = 0.015f), Color.Transparent),
                ),
                radius = r * 0.955f, style = Stroke(width = r * 0.075f),
            )
        }
    }
}

@Composable
internal fun VinylTonearm(
    status: PlaybackStatus,
    reduceMotion: Boolean,
    visibility: Float,
    modifier: Modifier = Modifier,
) {
    val parked = tonearmParked(status)
    val sweep by animateFloatAsState(
        targetValue = if (parked) -58f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(
            durationMillis = if (parked) 760 else 940, easing = FastOutSlowInEasing,
        ),
        label = "vinyl-tonearm-sweep",
    )
    val lift by animateFloatAsState(
        targetValue = if (parked) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(250, easing = FastOutSlowInEasing),
        label = "vinyl-stylus-lift",
    )
    Canvas(
        modifier = modifier.graphicsLayer { alpha = visibility.coerceIn(0f, 1f) }
            .semantics { contentDescription = if (parked) "Tonearm parked" else "Tonearm on record" },
    ) {
        val d = size.minDimension
        val pivot = Offset(size.width * 0.86f, size.height * 0.08f)
        fun point(x: Float, y: Float) = Offset(size.width * x, size.height * y)
        rotate(sweep, pivot = pivot) {
            val arm = Path().apply {
                moveTo(pivot.x, pivot.y)
                lineTo(size.width * 0.835f, size.height * 0.165f)
                lineTo(size.width * 0.775f, size.height * 0.253f)
            }
            drawPath(
                path = arm, color = Color.Black.copy(alpha = 0.44f),
                style = Stroke(width = d * 0.029f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = arm,
                brush = Brush.linearGradient(
                    listOf(Color(0xFFF5F4F1), Color(0xFF9DA5AE), Color(0xFFDFE2E4)),
                    start = pivot, end = point(0.775f, 0.253f),
                ),
                style = Stroke(width = d * 0.017f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawLine(
                color = Color(0xFFD8B778), start = point(0.775f, 0.253f),
                end = point(0.786f, 0.275f - lift * 0.006f),
                strokeWidth = d * 0.031f, cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFF292D31), start = point(0.786f, 0.275f - lift * 0.006f),
                end = point(0.798f, 0.283f - lift * 0.009f),
                strokeWidth = d * 0.018f, cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFD7DBDF), start = point(0.798f, 0.283f - lift * 0.009f),
                end = point(0.79f, 0.298f - lift * 0.015f),
                strokeWidth = d * 0.004f, cap = StrokeCap.Round,
            )
            drawCircle(Color(0xFF8D969F), radius = d * 0.024f, center = point(0.867f, 0.047f))
            drawCircle(Color(0xFFE0E2E5), radius = d * 0.013f, center = point(0.867f, 0.047f))
        }
        drawCircle(Color.Black.copy(alpha = 0.7f), radius = d * 0.046f, center = pivot)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFE9E9E6), Color(0xFF89929C), Color(0xFF3B424B)),
                center = pivot, radius = d * 0.035f,
            ),
            radius = d * 0.035f, center = pivot,
        )
        drawCircle(Color(0xFF444A52), radius = d * 0.009f, center = pivot)
    }
}
