package dev.behradhz.meowzix.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileInputStream

/** Bounded decoding shared by the compact widget and the rotating vinyl widget. */
internal fun decodeWidgetArtwork(context: Context, ref: String, maxSizePx: Int): Bitmap? {
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
    while (bounds.outWidth / sample > maxSizePx || bounds.outHeight / sample > maxSizePx)
        sample *= 2
    return runCatching {
        open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        }
    }.getOrNull()
}
