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
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.net.Uri
import java.io.File
import java.io.FileInputStream

/** Shared record artwork for the home widget and the in-app gesture surface.
 * Contains no Media3 or launcher-host APIs; both consumers use the same geometry.
 */
internal object VinylRecordRenderer {
    private const val SIZE = 240

        internal fun decodeCover(context: Context, ref: String): Bitmap? {
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

        internal fun drawVinyl(cover: Bitmap?, angle: Float, lift: Float): Bitmap {
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(bitmap)
            c.scale(SIZE / 280f, SIZE / 280f)
            drawVinylOnCanvas(c, cover, angle, lift)
            return bitmap
        }

        /** Shared native Canvas renderer for the widget and the full-fidelity gesture surface. */
        internal fun drawVinylOnCanvas(c: Canvas, cover: Bitmap?, angle: Float, lift: Float) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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
        }
    }
