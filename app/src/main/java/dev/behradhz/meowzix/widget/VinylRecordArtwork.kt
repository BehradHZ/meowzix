package dev.behradhz.meowzix.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draw the user's artwork as a circular record: dark outer rim, etched grooves,
 * glossy highlights and a central spindle. The entire composited bitmap rotates.
 *
 * At 192x192, partial widget updates stay bounded to a small Binder payload.
 * Rendering and artwork decoding never run on the UI thread.
 */
internal object VinylRecordArtwork {
    private const val SIZE = 192
    private const val STEP_DEGREES = 16f
    private val lock = Any()
    private var base: Bitmap? = null
    private var angle = 0f
    private var lastArtworkRef: String? = null

    suspend fun prepare(context: Context, artworkRef: String?): Bitmap =
        withContext(Dispatchers.IO) {
            val cached = synchronized(lock) {
                if (base != null && lastArtworkRef == artworkRef) currentFrameLocked() else null
            }
            if (cached != null) return@withContext cached
            val cover = artworkRef?.let { decode(context, it) }
            val record = drawRecord(cover)
            synchronized(lock) {
                base = record
                lastArtworkRef = artworkRef
                angle = 0f
            }
            record
        }

    /** Frames are sent only while the real playback service is playing and the screen is on. */
    fun nextFrame(): Bitmap? = synchronized(lock) {
        val record = base ?: return@synchronized null
        angle = (angle + STEP_DEGREES) % 360f
        currentFrameLocked()
    }

    private fun currentFrameLocked(): Bitmap {
        val record = requireNotNull(base)
        return Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).also { output ->
            val canvas = Canvas(output)
            canvas.rotate(angle, SIZE / 2f, SIZE / 2f)
            canvas.drawBitmap(record, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun drawRecord(cover: Bitmap?): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = SIZE / 2f
        val radius = SIZE * .485f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        paint.color = Color.rgb(12, 14, 21)
        canvas.drawCircle(cx, cx, radius, paint)

        // A thin record edge and subtle etched grooves remain visible over cover art.
        if (cover != null) {
            val side = min(cover.width, cover.height)
            val srcLeft = (cover.width - side) / 2
            val srcTop = (cover.height - side) / 2
            val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val matrix = Matrix().apply {
                setRectToRect(
                    RectF(srcLeft.toFloat(), srcTop.toFloat(),
                        (srcLeft + side).toFloat(), (srcTop + side).toFloat()),
                    RectF(cx - radius * .85f, cx - radius * .85f,
                        cx + radius * .85f, cx + radius * .85f),
                    Matrix.ScaleToFit.CENTER,
                )
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawCircle(cx, cx, radius * .85f, paint)
            paint.shader = null
        } else {
            paint.color = Color.rgb(38, 41, 55)
            canvas.drawCircle(cx, cx, radius * .85f, paint)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = SIZE * .005f
        listOf(.90f, .93f, .96f, .99f).forEachIndexed { index, fraction ->
            paint.color = if (index % 2 == 0) Color.argb(110, 196, 205, 223)
                else Color.argb(80, 60, 68, 81)
            canvas.drawCircle(cx, cx, radius * fraction, paint)
        }
        listOf(.48f, .57f, .66f, .75f, .82f).forEach { fraction ->
            paint.color = Color.argb(65, 12, 15, 25)
            canvas.drawCircle(cx, cx, radius * fraction, paint)
        }
        paint.style = Paint.Style.FILL

        // Spindle hole and its metallic lip stay at the center of the rotating artwork.
        paint.color = Color.argb(220, 13, 15, 22)
        canvas.drawCircle(cx, cx, radius * .11f, paint)
        paint.color = Color.argb(210, 214, 220, 230)
        canvas.drawCircle(cx, cx, radius * .046f, paint)
        paint.color = Color.rgb(30, 32, 42)
        canvas.drawCircle(cx, cx, radius * .022f, paint)
        return bitmap
    }

    private fun decode(context: Context, ref: String): Bitmap? {
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
            open()?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                })
            }
        }.getOrNull()
    }
}
