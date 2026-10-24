package dev.behradhz.meowzix.feature.nowplaying

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.behradhz.meowzix.domain.playback.PlaybackState
import dev.behradhz.meowzix.domain.playback.QueueState
import kotlin.math.min
import dev.behradhz.meowzix.ui.theme.Vazirmatn

private val HeroPrimary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurface
private val HeroSecondary: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * One artwork node and one identity node are retained for the whole transition. Their bounds,
 * positions and typography are interpolated; the compact lyric preview occupies the space directly
 * below the same artwork and fades out as the hero becomes the expanded horizontal header.
 */
@Composable
internal fun MorphingPlayerHero(
    state: PlaybackState,
    queueState: QueueState,
    progress: Float,
    collapsedHeightPx: Int,
    lyricsLayoutVisible: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGoToArtist: (String) -> Unit,
    favorite: Boolean,
    reduceMotion: Boolean,
    onBackdropTransition: (ArtworkBackdropTransition?) -> Unit,
    onArtworkSettled: (java.util.UUID) -> Unit,
    compactLyrics: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return
    val lyricSpace by animateFloatAsState(
        targetValue = if (lyricsLayoutVisible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.86f, stiffness = 195f),
        label = "artwork-lyrics-layout-settle",
    )

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            Box {
                ArtworkGestureZoneForMorph(
                    state = state,
                    queueState = queueState,
                    artworkRef = track.artworkRef,
                    artworkDescription = "${track.title} cover art",
                    onBack = onBack,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onBackdropTransition = onBackdropTransition,
                    onTransitionSettled = onArtworkSettled,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(Modifier.graphicsLayer { alpha = (1f - progress) * lyricSpace }) { compactLyrics() }
            MorphingIdentity(
                title = track.title,
                artist = track.artist ?: "Unknown artist",
                favorite = favorite,
                progress = progress,
                onGoToArtist = onGoToArtist,
                reduceMotion = reduceMotion,
                onToggleFavorite = onToggleFavorite,
            )
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        // The expanded layout is 76.dp tall; preserve the original full area for origin geometry.
        val currentHeight = constraints.maxHeight.takeIf { it != Constraints.Infinity } ?: width
        val height = collapsedHeightPx.coerceAtLeast(currentHeight).coerceAtLeast(1)
        val compactArt = 64.dp.roundToPx()
        val gap = 12.dp.roundToPx()
        val previewHeight = 118.dp.roundToPx()
        val expandedIdentityWidth = (width - compactArt - gap).coerceAtLeast(1)
        val identityWidth = lerpInt(width, expandedIdentityWidth, progress).coerceAtLeast(1)
        // Measure the real title/artist row before sizing artwork. The reserved bottom area must
        // also support larger accessibility font scales instead of assuming a fixed text height.
        val identity = measurables[2].measure(
            Constraints(
                minWidth = identityWidth,
                maxWidth = identityWidth,
                minHeight = 0,
                maxHeight = currentHeight,
            ),
        )
        val identityReserve = maxOf(66.dp.roundToPx(), identity.height)
        val geometry = collapsedHeroGeometry(
            width = width,
            height = height,
            compactArt = compactArt,
            previewHeight = previewHeight,
            identityReserve = identityReserve,
            identityHeight = identity.height,
            margin = gap,
            lyricSpace = lyricSpace,
        )
        val artSize = lerpInt(geometry.artworkSize, compactArt, progress).coerceAtLeast(1)
        val artwork = measurables[0].measure(Constraints.fixed(artSize, artSize))
        val preview = measurables[1].measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = previewHeight,
                maxHeight = previewHeight,
            ),
        )

        val collapsedArtX = (width - artSize) / 2
        val artX = lerpInt(collapsedArtX, 0, progress)
        val artY = lerpInt(geometry.artworkTop, 4.dp.roundToPx(), progress)
        val previewY = geometry.artworkTop + geometry.artworkSize + 4.dp.roundToPx()
        val identityX = lerpInt(0, compactArt + gap, progress)
        val identityY = lerpInt(geometry.identityTop, 2.dp.roundToPx(), progress)

        // Report the actual animated height, never the old full-height geometry.
        layout(width, currentHeight) {
            artwork.placeRelative(artX, artY)
            preview.placeRelative(0, previewY)
            identity.placeRelative(identityX, identityY)
        }
    }
}

@Composable
private fun MorphingIdentity(
    title: String,
    artist: String,
    favorite: Boolean,
    progress: Float,
    onToggleFavorite: () -> Unit,
    onGoToArtist: (String) -> Unit,
    reduceMotion: Boolean,
) {
    val navigableArtist = artist.trim().takeUnless { it.isBlank() || it == "Unknown artist" }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            dev.behradhz.meowzix.ui.components.DirectionalMarqueeTitle(
                text = title,
                fontSize = (24f - 8f * progress).sp,
                lineHeight = (29f - 8f * progress).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = Vazirmatn,
                color = HeroPrimary,
                reduceMotion = reduceMotion,
            )
            Text(
                text = artist,
                fontSize = (16f - 3f * progress).sp,
                lineHeight = (21f - 3f * progress).sp,
                color = HeroSecondary,
                fontFamily = Vazirmatn,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.then(
                    if (navigableArtist != null) Modifier.clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = "Open artist $navigableArtist",
                    ) { onGoToArtist(navigableArtist) } else Modifier,
                ),
            )
        }
        // Favorite belongs to the full player; the compact header instead reserves a menu slot.
        Box(
            Modifier.size(width = (48f * (1f - progress)).coerceAtLeast(0f).dp, height = 48.dp)
                .clipToBounds(),
        ) {
            if (progress < 0.95f) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(48.dp).graphicsLayer {
                        alpha = (1f - progress * 3f).coerceIn(0f, 1f)
                    },
                ) {
                    Icon(
                        imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else HeroPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width((48f * progress).dp))
    }
}

@Composable
private fun ArtworkGestureZoneForMorph(
    state: PlaybackState,
    queueState: QueueState,
    artworkRef: String?,
    artworkDescription: String,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackdropTransition: (ArtworkBackdropTransition?) -> Unit,
    onTransitionSettled: (java.util.UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    NowPlayingArtworkPager(
        state = state,
        queueState = queueState,
        fallbackArtworkRef = artworkRef,
        fallbackArtworkDescription = artworkDescription,
        onBack = onBack,
        onPrevious = onPrevious,
        onNext = onNext,
        onBackdropTransition = onBackdropTransition,
        onTransitionSettled = onTransitionSettled,
        modifier = modifier,
    )
}

/** Pixel-space compact player bounds. The title row is pinned to the hero's bottom edge,
 * as in v0.5, rather than to the artwork/lyrics preview (which may leave empty space on tall screens).
 * The artwork can still ease between its centered/no-lyrics and compact-lyrics positions independently.
 */
internal data class CollapsedHeroGeometry(val artworkSize: Int, val artworkTop: Int, val identityTop: Int)

internal fun collapsedHeroGeometry(
    width: Int,
    height: Int,
    compactArt: Int,
    previewHeight: Int,
    identityReserve: Int,
    identityHeight: Int,
    margin: Int,
    lyricSpace: Float,
): CollapsedHeroGeometry {
    val withLyrics = min(width, (height - previewHeight - identityReserve - margin).coerceAtLeast(compactArt))
    val withoutLyrics = min(width, (height - identityReserve - margin).coerceAtLeast(compactArt))
    val artworkSize = lerpInt(withoutLyrics, withLyrics, lyricSpace).coerceAtLeast(1)
    val centeredTop = ((height - identityHeight - withoutLyrics) / 2).coerceAtLeast(0)
    val artworkTop = lerpInt(centeredTop, 0, lyricSpace)
    // Keep the title and artist just above the seekbar for all screen heights and lyrics states.
    // This also prevents their position from jumping when the new artwork finishes sliding.
    val identityTop = (height - identityHeight).coerceAtLeast(0)
    return CollapsedHeroGeometry(artworkSize, artworkTop, identityTop)
}

private fun lerpInt(start: Int, stop: Int, fraction: Float): Int =
    (start + (stop - start) * fraction.coerceIn(0f, 1f)).toInt()

/** Interpolates one persistent hero between the full-player area and a real compact header. */
internal fun lyricsMorphHeroHeight(fullHeight: Dp, progress: Float, compactHeight: Dp = 76.dp): Dp {
    val available = fullHeight.coerceAtLeast(0.dp)
    val target = compactHeight.coerceIn(0.dp, available)
    return available + (target - available) * progress.coerceIn(0f, 1f)
}
