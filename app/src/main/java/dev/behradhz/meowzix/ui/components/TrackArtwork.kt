package dev.behradhz.meowzix.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.downloads.DownloadStatus
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun rememberArtworkBitmap(
    artworkRef: String?,
    targetPixels: Int,
): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(artworkRef, targetPixels) {
        mutableStateOf<ImageBitmap?>(null)
    }

    LaunchedEffect(artworkRef, targetPixels) {
        bitmap = if (artworkRef.isNullOrBlank()) {
            null
        } else {
            decodeSampledArtwork(
                context = context,
                uri = Uri.parse(artworkRef),
                targetPixels = targetPixels.coerceAtLeast(64),
            )
        }
    }
    return bitmap
}

private suspend fun decodeSampledArtwork(
    context: Context,
    uri: Uri,
    targetPixels: Int,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return@runCatching null
        }

        var sampleSize = 1
        val longestEdge = max(bounds.outWidth, bounds.outHeight)
        while (longestEdge / (sampleSize * 2) >= targetPixels) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
        }
    }.getOrNull()
}

@Composable
fun TrackArtwork(
    artworkRef: String?,
    description: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val targetPixels = with(density) { (size * 2f).roundToPx() }
    val bitmap = rememberArtworkBitmap(artworkRef, targetPixels)

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

@Composable
fun ChatAvatar(
    artworkRef: String?,
    description: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val targetPixels = with(density) { (size * 2f).roundToPx() }
    val bitmap = rememberArtworkBitmap(artworkRef, targetPixels)

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.extraLarge)
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
                Icons.Rounded.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.46f),
            )
        }
    }
}

@Composable
fun DownloadableTrackArtwork(
    artworkRef: String?,
    description: String,
    size: Dp = 52.dp,
    isOffline: Boolean,
    download: OfflineDownload?,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size)) {
        TrackArtwork(artworkRef, description, size = size)
        if (!isOffline) {
            val active = download?.status == DownloadStatus.DOWNLOADING ||
                download?.status == DownloadStatus.QUEUED
            if (active) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Black.copy(alpha = 0.36f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val total = download?.totalBytes?.takeIf { it > 0L }
                        if (total != null) {
                            CircularProgressIndicator(
                                progress = {
                                    (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.size(size * 0.60f),
                                strokeWidth = 3.dp,
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.24f),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(size * 0.60f),
                                strokeWidth = 3.dp,
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.24f),
                            )
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(27.dp)
                        .clickable(onClick = onDownload),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = Color.Black.copy(alpha = 0.70f),
                    contentColor = Color.White,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-bleed artwork used by Now Playing. The sharp cover is slightly zoomed, while a second
 * display-sized decode is blurred and alpha-masked into the lower half. The mask makes the two
 * layers blend continuously instead of creating a visible horizontal seam behind the controls.
 */
@Composable
fun TrackArtworkBackdrop(
    artworkRef: String?,
    modifier: Modifier = Modifier,
) {
    val bitmap = rememberArtworkBitmap(artworkRef, BACKDROP_TARGET_PIXELS)

    Box(modifier = modifier.background(Color(0xFF101010))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = SHARP_ARTWORK_SCALE
                        scaleY = SHARP_ARTWORK_SCALE
                    },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        val blurMask = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Transparent,
                                0.38f to Color.Transparent,
                                0.54f to Color.Black.copy(alpha = 0.14f),
                                0.66f to Color.Black.copy(alpha = 0.58f),
                                0.78f to Color.Black.copy(alpha = 0.90f),
                                1.00f to Color.Black,
                            ),
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(brush = blurMask, blendMode = BlendMode.DstIn)
                        }
                    },
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = BLURRED_ARTWORK_SCALE
                            scaleY = BLURRED_ARTWORK_SCALE
                        }
                        .blur(PLAYER_BACKDROP_BLUR),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.08f),
                                0.32f to Color.Transparent,
                                0.50f to Color.Black.copy(alpha = 0.05f),
                                0.66f to Color.Black.copy(alpha = 0.22f),
                                0.80f to Color.Black.copy(alpha = 0.56f),
                                1.00f to Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Transparent,
                                0.48f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.44f),
                                1.00f to Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )
        }
    }
}

private val PLAYER_BACKDROP_BLUR = 46.dp
private const val BACKDROP_TARGET_PIXELS = 1800
private const val SHARP_ARTWORK_SCALE = 1.10f
private const val BLURRED_ARTWORK_SCALE = 1.16f