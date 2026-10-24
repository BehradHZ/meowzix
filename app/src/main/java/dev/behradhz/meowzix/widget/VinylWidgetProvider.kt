package dev.behradhz.meowzix.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.net.Uri
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.behradhz.meowzix.R
import dev.behradhz.meowzix.playback.PlaybackService
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A SECOND optional home-screen widget; the existing compact Playback widget and in-app
 * artwork pager are unchanged. Only a live PlaybackService drives frames while playing:
 * Android's minimum periodic widget update interval cannot deliver smooth animation.
 */
@UnstableApi
class VinylWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            var controller: MediaController? = null
            try {
                controller = MediaController.Builder(
                    context, SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                ).buildAsync().await()
                updateAll(context.applicationContext, PlaybackWidgetState.fromPlayer(controller))
            } catch (_: Exception) {
                render(context.applicationContext, PlaybackWidgetState.Empty, 0f, 1f)
            } finally {
                controller?.release()
                pending.finish()
                scope.cancel()
            }
        }
    }

    override fun onDisabled(context: Context) {
        animationJob?.cancel()
        animationJob = null
        super.onDisabled(context)
    }

    companion object {
        private const val SIZE = 280
        private const val FRAME_MS = 250L
        private const val DEGREES_PER_MS = 360f / 5_600f
        // Each frame uses its own Paint to avoid races between canceled and new renders.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var animationJob: Job? = null
        private var lastPlaying = false
        private var lastTitle: String? = null
        private var angle = 0f
        private val coverLock = Any()
        private var cachedRef: String? = null
        private var cachedCover: Bitmap? = null

        fun hasWidgets(context: Context): Boolean = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, VinylWidgetProvider::class.java)).isNotEmpty()

        suspend fun updateAll(context: Context, state: PlaybackWidgetState, reduceMotion: Boolean = false) {
            if (!hasWidgets(context)) {
                animationJob?.cancel()
                animationJob = null
                return
            }
            animationJob?.cancel()
            val wasPlaying = lastPlaying
            lastPlaying = state.isPlaying
            if (lastTitle != state.title) {
                angle = 0f
                lastTitle = state.title
            }
            if (reduceMotion) {
                render(context, state, angle, if (state.isPlaying) 0f else 1f)
                return
            }
            animationJob = scope.launch {
                if (wasPlaying != state.isPlaying) {
                    // Sweep the stylus out on pause, back onto the grooves on resume.
                    for (frame in 0..5) {
                        if (!isActive) return@launch
                        val fraction = frame / 5f
                        render(context, state, angle, if (state.isPlaying) 1f - fraction else fraction)
                        delay(80L)
                    }
                } else {
                    render(context, state, angle, if (state.isPlaying) 0f else 1f)
                }
                while (isActive && state.isPlaying && hasWidgets(context)) {
                    delay(FRAME_MS)
                    angle = (angle + FRAME_MS * DEGREES_PER_MS) % 360f
                    render(context, state, angle, 0f)
                }
            }
        }

        private suspend fun render(context: Context, state: PlaybackWidgetState, rotation: Float, lift: Float) {
            val ids = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, VinylWidgetProvider::class.java))
            if (ids.isEmpty()) return
            // Bound the bitmap and cache decoded art: a frame must never re-read a track from disk.
            val cover = withContext(Dispatchers.IO) {
                synchronized(coverLock) {
                    if (cachedRef != state.artworkRef) {
                        cachedRef = state.artworkRef
                        cachedCover = state.artworkRef?.let { decodeCover(context, it) }
                    }
                    cachedCover
                }
            }
            val record = withContext(Dispatchers.Default) { drawVinyl(cover, rotation, lift) }
            val title = state.title ?: "Nothing playing"
            val manager = AppWidgetManager.getInstance(context)
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_vinyl).apply {
                    setImageViewBitmap(R.id.vinyl_record, record)
                    // Keep the entire visible vinyl a single action; no hidden previous/next
                    // click zones that could unexpectedly override play/pause.
                    setContentDescription(
                        R.id.vinyl_record,
                        if (state.isPlaying) "Pause $title" else "Play $title",
                    )
                    setOnClickPendingIntent(
                        R.id.vinyl_record,
                        transport(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 4302),
                    )
                }
                manager.updateAppWidget(id, views)
            }
        }

        private fun transport(context: Context, code: Int, request: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, request, Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                    component = ComponentName(context, MediaButtonReceiver::class.java)
                    putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code))
                }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun decodeCover(context: Context, ref: String): Bitmap? {
            val uri = runCatching { Uri.parse(ref) }.getOrNull() ?: return null
            fun open() = when (uri.scheme?.lowercase()) {
                "file" -> uri.path?.let(::File)?.takeIf(File::isFile)?.let(::FileInputStream)
                "content", "android.resource" -> context.contentResolver.openInputStream(uri)
                else -> null
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching { open()?.use { BitmapFactory.decodeStream(it, null, bounds) } }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > SIZE || bounds.outHeight / sample > SIZE) sample *= 2
            return runCatching {
                open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            }.getOrNull()
        }

        private fun drawVinyl(cover: Bitmap?, angle: Float, lift: Float): Bitmap {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            val cx = 136f
            val cy = 143f
            val radius = 124f
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            paint.shader = RadialGradient(cx, cy, radius, intArrayOf(
                Color.rgb(42, 43, 47), Color.rgb(8, 9, 13), Color.rgb(35, 37, 40),
                Color.rgb(6, 8, 11), Color.rgb(33, 35, 38),
            ), floatArrayOf(0f, .24f, .58f, .82f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, radius, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            for (i in 0..30) {
                paint.color = if (i % 5 == 0) 0x2899A0A6 else 0x126B7179
                paint.strokeWidth = if (i % 5 == 0) 1.5f else .8f
                c.drawCircle(cx, cy, 55f + i * 2.15f, paint)
            }
            paint.style = Paint.Style.FILL
            c.save()
            c.rotate(angle, cx, cy)
            paint.color = Color.rgb(63, 56, 52)
            c.drawCircle(cx, cy, 54f, paint)
            if (cover != null && !cover.isRecycled) {
                val matrix = Matrix().apply {
                    val scale = 108f / minOf(cover.width, cover.height).coerceAtLeast(1)
                    setScale(scale, scale)
                    postTranslate(cx - cover.width * scale / 2f, cy - cover.height * scale / 2f)
                }
                paint.shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
                c.drawCircle(cx, cy, 51f, paint)
                paint.shader = null
            }
            paint.color = 0xCCF2E6D5.toInt()
            c.drawCircle(cx, cy, 7f, paint)
            paint.color = Color.rgb(20, 20, 23)
            c.drawCircle(cx, cy, 3f, paint)
            c.restore()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 9f
            paint.shader = SweepGradient(cx, cy, intArrayOf(0x00FFFFFF, 0x17FFFFFF, 0x00FFFFFF, 0x0EFFFFFF, 0x00FFFFFF), null)
            c.drawCircle(cx, cy, 112f, paint)
            paint.shader = null
            paint.style = Paint.Style.FILL

            // Stylus moves away from the playable grooves when playback stops.
            val parked = lift.coerceIn(0f, 1f)
            val pivotX = 245f
            val pivotY = 30f
            val tipX = 188f + parked * 73f
            val tipY = 85f - parked * 3f
            val path = Path().apply {
                moveTo(pivotX, pivotY)
                lineTo(233f + parked * 17f, 45f)
                lineTo(tipX, tipY)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 8f
            paint.color = 0x99000000.toInt()
            c.drawPath(path, paint)
            paint.strokeWidth = 4.5f
            paint.color = Color.rgb(204, 209, 215)
            c.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(218, 166, 97)
            c.drawCircle(tipX, tipY, 4.5f, paint)
            paint.color = Color.rgb(35, 37, 41)
            c.drawCircle(pivotX, pivotY, 11f, paint)
            paint.color = Color.rgb(198, 202, 208)
            c.drawCircle(pivotX, pivotY, 5.5f, paint)
            return bitmap
        }
    }
}
