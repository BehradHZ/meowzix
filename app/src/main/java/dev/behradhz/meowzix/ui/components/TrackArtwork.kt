package dev.behradhz.meowzix.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artworkCache = object : LruCache<String, Bitmap>(ARTWORK_CACHE_KB) {
    override fun sizeOf(key: String, value: Bitmap): Int =
        (value.allocationByteCount / 1024).coerceAtLeast(1)
}

@Composable
private fun rememberArtworkBitmap(
    artworkRef: String?,
    targetSizePx: Int,
): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = artworkRef?.let { "$it@$targetSizePx" }
    var bitmap by remember(cacheKey) { mutableStateOf<Bitmap?>(cacheKey?.let(artworkCache::get)) }

    LaunchedEffect(cacheKey) {
        if (cacheKey == null || artworkRef == null) {
            bitmap = null
            return@LaunchedEffect
        }
        artworkCache.get(cacheKey)?.let {
            bitmap = it
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            runCatching { decodeSampledBitmap(context, Uri.parse(artworkRef), targetSizePx) }
                .getOrNull()
                ?.also { decoded -> artworkCache.put(cacheKey, decoded) }
        }
    }
    return bitmap?.asImageBitmap()
}

@Composable
fun TrackArtwork(
    artworkRef: String?,
    description: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val targetSizePx = with(density) { size.roundToPx().coerceAtLeast(1) }
    val bitmap = rememberArtworkBitmap(artworkRef, targetSizePx)

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.42f),
            )
        }
    }
}

/**
 * Artwork-derived ambient field for immersive playback screens.
 *
 * This deliberately blurs the artwork itself. Floating controls use Haze for
 * real backdrop glass above this field.
 */
@Composable
fun TrackArtworkBackdrop(
    artworkRef: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberArtworkBitmap(artworkRef, BACKDROP_TARGET_PX)

    Box(
        modifier = modifier.background(Color(0xFF121212)),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.18f
                        scaleY = 1.18f
                    }
                    .blur(56.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f),
                                Color(0xFF262626),
                                Color(0xFF101010),
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.48f to Color.Black.copy(alpha = 0.34f),
                        1f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )
    }
}

private fun decodeSampledBitmap(context: Context, uri: Uri, targetSizePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (
        bounds.outWidth / (sampleSize * 2) >= targetSizePx &&
        bounds.outHeight / (sampleSize * 2) >= targetSizePx
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}

private const val ARTWORK_CACHE_KB = 24 * 1024
private const val BACKDROP_TARGET_PX = 1024
