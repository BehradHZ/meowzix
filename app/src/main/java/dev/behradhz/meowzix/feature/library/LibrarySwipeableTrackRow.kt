package dev.behradhz.meowzix.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistAddCircle
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.behradhz.meowzix.domain.telegram.TelegramTrackOrigin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.core.model.Track
import dev.behradhz.meowzix.domain.downloads.OfflineDownload
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistSummary
import dev.behradhz.meowzix.ui.components.maskBehindSwipeTrack
import dev.behradhz.meowzix.ui.components.StagedSwipeReveal
import dev.behradhz.meowzix.ui.components.TRACK_CARD_ALPHA
import dev.behradhz.meowzix.ui.components.TRACK_ROW_HORIZONTAL_PADDING
import dev.behradhz.meowzix.ui.components.TRACK_ROW_VERTICAL_PADDING
import dev.behradhz.meowzix.ui.components.TRACK_SWIPE_REMOVE_THRESHOLD
import dev.behradhz.meowzix.ui.components.DownloadableTrackArtwork
import dev.behradhz.meowzix.ui.haptics.MeowzixHapticCue
import dev.behradhz.meowzix.ui.haptics.rememberMeowzixHaptics
import java.util.UUID
import kotlin.math.abs

internal data class LibraryRecommendationActions(
    val continueVibe: (UUID) -> Unit,
    val why: (UUID) -> Unit,
)

internal val LocalLibraryRecommendationActions =
    staticCompositionLocalOf<LibraryRecommendationActions?> { null }

@Composable
internal fun SwipeableLibraryTrackRow(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    availability: LibraryTrackAvailability,
    download: OfflineDownload?,
    playlists: List<PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
    onGoToArtist: () -> Unit,
    onEnsureArtwork: () -> Unit,
    onRemoveOfflineCopy: (() -> Unit)? = null,
    onRemoveTrack: ((offline: Boolean, library: Boolean) -> Unit)? = null,
    canRemoveFromLibrary: Boolean = true,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onAddToLibrary: (() -> Unit)? = null,
    /** Non-null only for Telegram chat playlists; true when the song is in Library. */
    chatLibraryMembership: Boolean? = null,
    onChatLibraryToggle: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    containerColor: Color? = null,
    isNewArrival: Boolean = false,
    reduceMotion: Boolean = false,
    artworkVisible: Boolean = true,
    onTrackInteracted: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    LaunchedEffect(track.id, track.artworkRef, artworkVisible) {
        if (artworkVisible) onEnsureArtwork()
    }

    val density = LocalDensity.current
    val haptics = rememberMeowzixHaptics()
    val actionThreshold = with(density) { 75.dp.toPx() }
    val libraryThreshold = with(density) { TRACK_SWIPE_REMOVE_THRESHOLD.toPx() }
    val maximumSwipe = with(density) { 300.dp.toPx() }
    val hasChatLibraryAction = chatLibraryMembership != null && onChatLibraryToggle != null
    var swipeOffsetX by remember(track.id) { mutableFloatStateOf(0f) }
    var swipeStage by remember(track.id) { mutableIntStateOf(0) }
    var horizontalDragActive by remember(track.id) { mutableStateOf(false) }
    val visualOffsetX by animateFloatAsState(
        targetValue = swipeOffsetX,
        animationSpec = if (horizontalDragActive) snap() else tween(190),
        label = "library-swipe-action",
    )
    val swipingRight = visualOffsetX >= 0f

    Box(modifier = Modifier.fillMaxWidth()) {
        if (hasChatLibraryAction) {
            // Keep the Telegram-specific second action, while ordinary track rows
            // use the original single-layer Play Next / Add to queue reveal.
            StagedSwipeReveal(
                offsetPx = visualOffsetX,
                primaryLabel = if (swipingRight) "Play next" else "Add to queue",
                primaryIcon = if (swipingRight) Icons.Rounded.PlaylistPlay else Icons.Rounded.PlaylistAdd,
                secondaryLabel = chatLibrarySwipeLabel(chatLibraryMembership == true),
                secondaryThresholdPx = libraryThreshold,
                secondaryIcon = if (chatLibraryMembership == true) Icons.Rounded.DeleteOutline
                                else Icons.Rounded.LibraryMusic,
                secondaryIsDestructive = chatLibraryMembership == true,
            )
        } else if (abs(visualOffsetX) > 0.5f) {
            Surface(
                modifier = Modifier.matchParentSize()
                    .maskBehindSwipeTrack(visualOffsetX),
                // An action is not an independent card: the moving song's
                // rounded silhouette alone determines what can be seen.
                shape = RectangleShape,
                color = if (swipingRight) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    horizontalArrangement = if (swipingRight) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (swipingRight) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Play next", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Add to queue", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = visualOffsetX
                    shape = RoundedCornerShape(18.dp)
                    clip = true
                }
                .pointerInput(track.id, actionThreshold, libraryThreshold, chatLibraryMembership,
                    hasChatLibraryAction) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            horizontalDragActive = true
                            swipeStage = when {
                                hasChatLibraryAction && abs(swipeOffsetX) >= libraryThreshold -> 2
                                abs(swipeOffsetX) >= actionThreshold -> 1
                                else -> 0
                            }
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            val nextOffset = (swipeOffsetX + amount).coerceIn(-maximumSwipe, maximumSwipe)
                            val nextStage = when {
                                hasChatLibraryAction && abs(nextOffset) >= libraryThreshold -> 2
                                abs(nextOffset) >= actionThreshold -> 1
                                else -> 0
                            }
                            if (nextStage > swipeStage) {
                                haptics.perform(MeowzixHapticCue.Threshold)
                            }
                            swipeStage = nextStage
                            swipeOffsetX = nextOffset
                        },
                        onDragCancel = {
                            horizontalDragActive = false
                            swipeStage = 0
                            swipeOffsetX = 0f
                        },
                        onDragEnd = {
                            val releasedOffset = swipeOffsetX
                            horizontalDragActive = false
                            swipeStage = 0
                            swipeOffsetX = 0f
                            val action = chatLibrarySwipeActionForOffset(
                                offsetPx = releasedOffset,
                                actionThresholdPx = actionThreshold,
                                libraryThresholdPx = libraryThreshold,
                                chatLibraryMembership = chatLibraryMembership,
                                libraryToggleAvailable = hasChatLibraryAction,
                            )
                            when (action) {
                                ChatLibrarySwipeAction.ADD_TO_LIBRARY,
                                ChatLibrarySwipeAction.REMOVE_FROM_LIBRARY -> onChatLibraryToggle?.invoke()
                                ChatLibrarySwipeAction.PLAY_NEXT -> onPlayNext()
                                ChatLibrarySwipeAction.ADD_TO_QUEUE -> onAddToQueue()
                                ChatLibrarySwipeAction.NONE -> Unit
                            }
                        },
                    )
                },
        ) {
            LibraryTrackRowContent(
                track = track,
                isCurrent = isCurrent,
                onClick = onClick,
                onTrackInteracted = onTrackInteracted,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPinOffline = onPinOffline,
                availability = availability,
                download = download,
                playlists = playlists,
                onFavorite = onFavorite,
                onAddToPlaylist = onAddToPlaylist,
                onGoToArtist = onGoToArtist,
                onRemoveFromPlaylist = onRemoveFromPlaylist,
                onAddToLibrary = onAddToLibrary,
                onRemoveOfflineCopy = onRemoveOfflineCopy,
                onRemoveTrack = onRemoveTrack,
                canRemoveFromLibrary = canRemoveFromLibrary,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                containerColor = containerColor,
                isNewArrival = isNewArrival,
                reduceMotion = reduceMotion,
                artworkVisible = artworkVisible,
                onLongClick = onLongClick,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTrackRowContent(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onTrackInteracted: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onPinOffline: () -> Unit,
    availability: LibraryTrackAvailability,
    download: OfflineDownload?,
    playlists: List<PlaylistSummary>,
    onFavorite: () -> Unit,
    onAddToPlaylist: (UUID) -> Unit,
    onGoToArtist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onAddToLibrary: (() -> Unit)?,
    onRemoveOfflineCopy: (() -> Unit)?,
    onRemoveTrack: ((offline: Boolean, library: Boolean) -> Unit)?,
    canRemoveFromLibrary: Boolean,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    containerColor: Color?,
    isNewArrival: Boolean,
    reduceMotion: Boolean,
    artworkVisible: Boolean,
    onLongClick: (() -> Unit)?,
) {
    val recommendationActions = LocalLibraryRecommendationActions.current
    val trackToolsActions = LocalLibraryTrackToolsActions.current
    val forwardAction = LocalTrackForwardAction.current
    val chatLinkActions = LocalTrackChatLinkActions.current
    var menuExpanded by remember(track.id) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // Register only for the expanded popup, not for every item in the paged Library list.
    DisposableEffect(lifecycleOwner, menuExpanded) {
        if (menuExpanded) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    menuExpanded = false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        } else {
            onDispose {}
        }
    }
    var chatOrigin by remember(track.id) { mutableStateOf<TelegramTrackOrigin?>(null) }
    var chatOriginLoaded by remember(track.id) { mutableStateOf(false) }
    val menuActionScope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(menuExpanded, track.id) {
        chatOrigin = null
        chatOriginLoaded = false
        if (menuExpanded) {
            chatOrigin = try {
                chatLinkActions?.originLookup?.invoke(track.id)
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
            // Show the action immediately; hydrate only this open menu's avatar on demand.
            if (chatOrigin != null && chatOrigin?.profilePhotoRef == null) {
                val refreshed = try {
                    chatLinkActions?.refreshOrigin?.invoke(track.id)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    null
                }
                if (refreshed != null) chatOrigin = refreshed
            }
            chatOriginLoaded = true
        }
    }
    val neutralSurface = (containerColor ?: MaterialTheme.colorScheme.surfaceVariant).copy(alpha = TRACK_CARD_ALPHA)
    val background by animateColorAsState(
        targetValue = if (isNewArrival) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f)
            .compositeOver(neutralSurface) else neutralSurface,
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else tween(520),
        label = "new-song-background",
    )
    val outline by animateColorAsState(
        targetValue = if (isNewArrival) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.62f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f),
        animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else tween(520),
        label = "new-song-outline",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (onLongClick != null) onLongClick() else menuExpanded = true
                },
            )
            .semantics {
                customActions = buildList {
                    onMoveUp?.let { action -> add(CustomAccessibilityAction("Move up") { action(); true }) }
                    onMoveDown?.let { action -> add(CustomAccessibilityAction("Move down") { action(); true }) }
                }
            },
        shape = RoundedCornerShape(18.dp),
        color = background,
        border = BorderStroke(0.8.dp, outline),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TRACK_ROW_HORIZONTAL_PADDING, vertical = TRACK_ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DownloadableTrackArtwork(
                artworkRef = if (artworkVisible) track.artworkRef else null,
                description = track.title,
                size = 52.dp,
                isOffline = availability == LibraryTrackAvailability.OFFLINE,
                download = download,
                onDownload = onPinOffline,
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = "Currently playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.size(5.dp))
                    }
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Text(
                    "${track.artist ?: "Unknown artist"} · ${libraryAvailabilityLabel(availability)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Text(
                formatLibraryDuration(track.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track actions")
                }
                if (menuExpanded) {
                    TrackActionsMenu(
                        expanded = menuExpanded,
                        title = track.title,
                        artist = track.artist,
                        favorite = track.favorite,
                        isQueue = false,
                        playlists = playlists,
                        onDismiss = { menuExpanded = false },
                        onPrimary = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onFavorite = onFavorite,
                        onForward = { forwardAction(TrackForwardSelection(track.id, track.title, track.artist)) },
                        onViewInChat = if (chatLinkActions != null && chatOrigin != null) {
                            {
                                // Resolve TDLib's message link only when tapped, never while opening the menu.
                                menuActionScope.launch {
                                    val link = try {
                                        chatLinkActions?.lookup?.invoke(track.id)
                                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                                        throw cancel
                                    } catch (_: Exception) {
                                        null
                                    }
                                    if (link != null) chatLinkActions?.open?.invoke(link)
                                    else Toast.makeText(context, "Telegram message unavailable", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else null,
                        viewInChatOrigin = chatOrigin,
                        isLocalSource = chatOriginLoaded && chatOrigin == null,
                        onGoToArtist = onGoToArtist,
                        onAddToPlaylist = onAddToPlaylist,
                        onContinueVibe = { recommendationActions?.continueVibe?.invoke(track.id) },
                        onEditMetadata = { trackToolsActions?.editMetadata?.invoke(track.id) },
                        onManageDuplicates = { trackToolsActions?.manageDuplicates?.invoke(track.id) },
                        onWhy = { recommendationActions?.why?.invoke(track.id) },
                        onMoveUp = onMoveUp,
                        onMoveDown = onMoveDown,
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        onAddToLibrary = onAddToLibrary,
                        onRemoveOfflineCopy = if (download?.hasManagedCopy == true &&
                            download.status == dev.behradhz.meowzix.domain.downloads.DownloadStatus.COMPLETED
                        ) onRemoveOfflineCopy else null,
                        onRemoveFromLocations = onRemoveTrack,
                        canRemoveOffline = download != null &&
                            download.status != dev.behradhz.meowzix.domain.downloads.DownloadStatus.CANCELED,
                        canRemoveFromLibrary = canRemoveFromLibrary,
                    )
                }
            }
            // Always reserve this slot, including for already-seen songs. The overflow
            // buttons therefore share one vertical axis while the dot fades in/out.
            Box(
                modifier = Modifier.width(14.dp).height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isNewArrival,
                    enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(250)),
                    exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(420)),
                ) {
                    Box(
                        Modifier.size(7.dp)
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            .semantics { contentDescription = "New song" },
                    )
                }
            }
        }
    }
}

private fun libraryAvailabilityLabel(availability: LibraryTrackAvailability): String = when (availability) {
    LibraryTrackAvailability.OFFLINE -> "Offline"
    LibraryTrackAvailability.CLOUD -> "Cloud"
    LibraryTrackAvailability.UNAVAILABLE -> "Unavailable"
}

private fun formatLibraryDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
