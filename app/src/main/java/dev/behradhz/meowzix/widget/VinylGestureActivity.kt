@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.behradhz.meowzix.widget

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.domain.playback.PlaybackController
import dev.behradhz.meowzix.domain.playback.PlaybackStatus
import dev.behradhz.meowzix.domain.settings.AppearanceSettings
import dev.behradhz.meowzix.domain.settings.SettingsRepository
import dev.behradhz.meowzix.domain.settings.ThemePreference
import dev.behradhz.meowzix.ui.theme.MeowzixTheme
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Nothing-style real touch surface launched by a home-screen app shortcut.
 *
 * This is NOT an Essential Apps widget. Neither the Android widget host nor
 * Nothing's closed Essential Apps beta exposes third-party pointer events.
 * It exists so the gestures are functional without misrepresenting that limit.
 */
@AndroidEntryPoint
class VinylGestureActivity : ComponentActivity() {
    @Inject lateinit var playback: PlaybackController
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val appearance by settings.appearanceSettings.collectAsStateWithLifecycle(initialValue = AppearanceSettings())
            val systemDark = isSystemInDarkTheme()
            val dark = when (appearance.theme) {
                ThemePreference.SYSTEM -> systemDark
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
            }
            MeowzixTheme(
                darkTheme = dark,
                colorTheme = appearance.colorTheme,
                customColorSeed = appearance.customColorSeed,
            ) {
                VinylGestureSurface(playback, appearance.reduceMotion)
            }
        }
    }
}

@Composable
private fun VinylGestureSurface(playback: PlaybackController, reduceMotion: Boolean) {
    val state by playback.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val artworkRef = state.currentTrack?.artworkRef
    var cover by remember(artworkRef) { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var draggedRotation by remember { mutableFloatStateOf(0f) }
    var drag by remember { mutableStateOf<VinylGestureAccumulator?>(null) }
    val playing = state.status == PlaybackStatus.PLAYING

    LaunchedEffect(artworkRef) {
        cover = withContext(Dispatchers.IO) {
            artworkRef?.let { VinylWidgetProvider.decodeCover(context.applicationContext, it) }
        }
    }
    LaunchedEffect(playing, reduceMotion) {
        if (!playing || reduceMotion) return@LaunchedEffect
        var lastFrameNs = withFrameNanos { it }
        while (isActive) {
            val frameNs = withFrameNanos { it }
            // Compose owns the canvas and the launcher isn't asked to redraw it.
            rotation = (rotation + (frameNs - lastFrameNs) / 1_000_000f * 360f / 5_600f) % 360f
            lastFrameNs = frameNs
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(300.dp)
                .testTag("vinyl-gesture-surface")
                .pointerInput(playback) {
                    detectTapGestures(onTap = { playback.togglePlayPause() })
                }
                .pointerInput(playback) {
                    detectDragGestures(
                        onDragStart = { down ->
                            drag = VinylGestureAccumulator(
                                centerX = size.width / 2f,
                                centerY = size.height / 2f,
                                startX = down.x,
                                startY = down.y,
                            )
                            draggedRotation = 0f
                        },
                        onDragEnd = {
                            when (val action = drag?.finish()) {
                                VinylGestureAction.Next ->
                                    if (playback.state.value.canSkipNext) playback.skipToNext()
                                VinylGestureAction.Previous ->
                                    if (playback.state.value.canSkipPrevious) playback.skipToPrevious()
                                is VinylGestureAction.SeekBy -> {
                                    val live = playback.state.value
                                    if (live.durationMs > 0) {
                                        playback.seekTo(
                                            (live.positionMs + action.milliseconds)
                                                .coerceIn(0L, live.durationMs),
                                        )
                                    }
                                }
                                else -> Unit
                            }
                            drag = null
                            draggedRotation = 0f
                        },
                        onDragCancel = {
                            drag = null
                            draggedRotation = 0f
                        },
                        onDrag = { change, _ ->
                            drag?.move(change.position.x, change.position.y)
                            draggedRotation = Math.toDegrees(drag?.rotationRadians ?: 0.0).toFloat()
                            change.consume()
                        },
                    )
                },
        ) {
            val native = drawContext.canvas.nativeCanvas
            val token = native.save()
            native.scale(size.width / 280f, size.height / 280f)
            VinylWidgetProvider.drawVinylOnCanvas(
                native, cover, rotation + draggedRotation, if (playing) 0f else 1f,
            )
            native.restoreToCount(token)
        }
    }
}
