package dev.behradhz.meowzix.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Draw the user's artwork as a circular record: dark outer rim, etched grooves,
 * glossy highlights and a central spindle. The entire composited bitmap rotates.
 *
 * At 288x288, widget updates stay bounded to a moderate Binder payload.
 * Rendering and artwork decoding never run on the UI thread.
 */
internal object VinylRecordArtwork {
    private const val SIZE = 288
    private const val STEP_DEGREES = 16f
    // Colors sampled from the user's Nothing-style home-screen icons.
    private val nothingRed = Color.rgb(215, 26, 33) // #D71A21
    private val nothingBlack = Color.rgb(31, 27, 24) // #1F1B18
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

    /**
     * Render the same color treatment in the launcher widget and Settings preview.
     * Nothing monochrome keeps highlights white while its dark tone blends between
     * screenshot black and red. Pausing always restores black without losing the setting.
     */
    fun themedFrame(source: Bitmap, palette: WidgetPalette, isPlaying: Boolean = false): Bitmap {
        val mode = palette.vinylColorMode
        if (mode == dev.behradhz.meowzix.domain.settings.WidgetThemeMode.FULL_COLOR) {
            return source
        }
        val (tint, ringColor) =
            if (mode == dev.behradhz.meowzix.domain.settings.WidgetThemeMode.NOTHING) {
                val strength = if (isPlaying) palette.vinylRedPercent.coerceIn(0, 100) / 100f else 0f
                val darkR = Color.red(nothingBlack) +
                    (Color.red(nothingRed) - Color.red(nothingBlack)) * strength
                val darkG = Color.green(nothingBlack) +
                    (Color.green(nothingRed) - Color.green(nothingBlack)) * strength
                val darkB = Color.blue(nothingBlack) +
                    (Color.blue(nothingRed) - Color.blue(nothingBlack)) * strength
                // Output = darkTone * (1 - luminance) + white * luminance.
                // White stays white at every slider value; dark pixels turn red only while playing.
                ColorMatrix(floatArrayOf(
                    .299f * (1f - darkR / 255f), .587f * (1f - darkR / 255f),
                    .114f * (1f - darkR / 255f), 0f, darkR,
                    .299f * (1f - darkG / 255f), .587f * (1f - darkG / 255f),
                    .114f * (1f - darkG / 255f), 0f, darkG,
                    .299f * (1f - darkB / 255f), .587f * (1f - darkB / 255f),
                    .114f * (1f - darkB / 255f), 0f, darkB,
                    0f, 0f, 0f, 1f, 0f,
                )) to Color.rgb(darkR.roundToInt(), darkG.roundToInt(), darkB.roundToInt())
            } else {
                val accent = palette.accent
                val r = Color.red(accent) / 255f
                val g = Color.green(accent) / 255f
                val b = Color.blue(accent) / 255f
                ColorMatrix(floatArrayOf(
                    .299f * r, .587f * r, .114f * r, 0f, 0f,
                    .299f * g, .587f * g, .114f * g, 0f, 0f,
                    .299f * b, .587f * b, .114f * b, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )) to accent
            }
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(tint)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColor
            alpha = 200
            strokeWidth = source.width * .012f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(source.width / 2f, source.height / 2f, source.width * .482f, ring)
        return result
    }

    fun snapshotFrame(): Bitmap? = synchronized(lock) {
        if (base == null) null else currentFrameLocked()
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
