package dev.behradhz.meowzix.feature.nowplaying

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.behradhz.meowzix.domain.lyrics.LyricsContentType
import dev.behradhz.meowzix.domain.lyrics.LyricsFollowAction
import dev.behradhz.meowzix.domain.lyrics.LyricsFollowMachine
import dev.behradhz.meowzix.domain.lyrics.LyricsFollowMode
import dev.behradhz.meowzix.ui.theme.Vazirmatn
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun CompactLyricsPreview(
    state: NowPlayingLyricsState,
    onExpand: () -> Unit,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val selected = state.selected?.takeIf { state.hasDisplayableLyrics } ?: return
    Surface(
        onClick = onExpand,
        modifier = modifier.fillMaxWidth().height(118.dp),
        color = Color.Transparent,
    ) {
        when (selected.parsed.contentType) {
            LyricsContentType.TIMED -> AnimatedContent(
                targetState = state.activeLineIndex,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        (slideInVertically { it / 3 } + fadeIn()) togetherWith
                            (slideOutVertically { -it / 3 } + fadeOut())
                    }
                },
                label = "lyrics-preview-line-change",
                modifier = Modifier.fillMaxSize(),
            ) { activeIndex ->
                val lines = state.effectiveLines
                Column(
                    Modifier.fillMaxSize().lyricsEdgeFade(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Before the first timestamp there is no previous/current line: display
                    // the first three lines from the TOP instead of putting line one at the bottom.
                    val previewIndices = compactPreviewLineIndices(activeIndex)
                    previewIndices.forEachIndexed { slot, index ->
                        PreviewLine(lines.getOrNull(index)?.text, activeIndex >= 0 && slot == 1)
                    }
                }
            }
            LyricsContentType.PLAIN -> {
                val lines = selected.parsed.plainLines.take(3)
                Column(
                    Modifier.fillMaxSize().lyricsEdgeFade(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    lines.forEachIndexed { index, line -> PreviewLine(line, index == 1 || lines.size == 1) }
                }
            }
            LyricsContentType.INSTRUMENTAL -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Instrumental", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
            }
            LyricsContentType.INVALID -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Lyrics unavailable", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f))
            }
        }
    }
}

@Composable
private fun PreviewLine(text: String?, current: Boolean) {
    Box(Modifier.fillMaxWidth().heightIn(min = 28.dp), contentAlignment = Alignment.Center) {
        if (!text.isNullOrBlank()) {
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (current) 1f else 0.52f),
                fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                style = (if (current) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium).copy(
                    fontFamily = Vazirmatn,
                    fontSize = if (current) 24.sp else 19.sp,
                    lineHeight = if (current) 33.sp else 27.sp,
                ),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .blur(if (current) 0.dp else 1.4.dp)
                    .semantics {
                        selected = current
                        if (current) stateDescription = "Current lyric"
                    },
            )
        }
    }
}

@Composable
fun ExpandedLyricsPanel(
    state: NowPlayingLyricsState,
    onLineSeek: (Int) -> Unit,
    onImportLrc: (Uri) -> Unit,
    onPaste: (String) -> Unit,
    onAdjustDelay: (Long) -> Unit,
    onSelectVersion: (java.util.UUID) -> Unit,
    reduceMotion: Boolean = false,
    showToolbar: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var pasteDialog by remember { mutableStateOf(false) }
    var optionsExpanded by remember(state.trackId) { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportLrc)
    }
    val selected = state.selected

    Column(modifier.fillMaxSize()) {
        // The empty state owns Import/Paste until a displayable lyric version exists.
        if (state.hasDisplayableLyrics && showToolbar) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).testTag("lyrics-toolbar"),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = { optionsExpanded = true }) { Text("Lyrics options") }
                    DropdownMenu(optionsExpanded, onDismissRequest = { optionsExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Import .lrc") },
                            onClick = {
                                optionsExpanded = false
                                importLauncher.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Paste lyrics") },
                            onClick = { optionsExpanded = false; pasteDialog = true },
                        )
                        state.trackLyrics?.versions.orEmpty().takeIf { it.size > 1 }?.forEach { version ->
                            DropdownMenuItem(
                                text = {
                                    Text((version.sourceLabel ?: version.sourceType.name) +
                                        if (version.selected) " · selected" else "")
                                },
                                onClick = {
                                    optionsExpanded = false
                                    onSelectVersion(version.id)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (showToolbar && selected?.parsed?.contentType == LyricsContentType.TIMED) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onAdjustDelay(-250L) }) { Text("−250 ms") }
                Text(
                    text = "Delay ${if (selected.userDelayMs >= 0) "+" else ""}${selected.userDelayMs} ms",
                    style = MaterialTheme.typography.labelMedium,
                )
                TextButton(onClick = { onAdjustDelay(250L) }) { Text("+250 ms") }
            }
        }

        when (selected?.parsed?.contentType) {
            null -> EmptyLyricsState(
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onPaste = { pasteDialog = true },
                modifier = Modifier.weight(1f),
            )
            LyricsContentType.TIMED -> TimedLyricsList(
                state = state,
                onLineSeek = onLineSeek,
                reduceMotion = reduceMotion,
                modifier = Modifier.weight(1f),
            )
            LyricsContentType.PLAIN -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().lyricsEdgeFade(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 96.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(selected.parsed.plainLines.size) { index ->
                    Text(
                        selected.parsed.plainLines[index],
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = Vazirmatn,
                            fontSize = 29.sp,
                            lineHeight = 43.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
            }
            LyricsContentType.INSTRUMENTAL -> EmptyLyricsState(
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onPaste = { pasteDialog = true },
                modifier = Modifier.weight(1f),
            )
            LyricsContentType.INVALID -> EmptyLyricsState(
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onPaste = { pasteDialog = true },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (pasteDialog) {
        PasteLyricsDialog(
            onDismiss = { pasteDialog = false },
            onSave = {
                pasteDialog = false
                onPaste(it)
            },
        )
    }
}

@Composable
private fun TimedLyricsList(
    state: NowPlayingLyricsState,
    onLineSeek: (Int) -> Unit,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // A different song/lyrics version must never inherit the previous song's scroll position.
    val listState = remember(state.trackId, state.selected?.id) { LazyListState() }
    val followMachine = remember(state.trackId, state.selected?.id) { LyricsFollowMachine() }
    var mode by remember(state.trackId, state.selected?.id) { mutableStateOf(followMachine.mode) }
    var programmaticScroll by remember(state.trackId, state.selected?.id) { mutableStateOf(false) }
    var followAnchorPx by remember(state.trackId, state.selected?.id) { mutableFloatStateOf(-1f) }
    val lines = state.effectiveLines
    val activeIndex = state.activeLineIndex
    // The playing line is always active. Manual browsing only reveals surrounding lines.
    val browsingReveal by animateFloatAsState(
        targetValue = if (mode == LyricsFollowMode.FOLLOWING) 0f else 1f,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 280),
        label = "lyrics-browsing-reveal",
    )

    suspend fun settle(index: Int, animated: Boolean = true) {
        if (index !in lines.indices) return
        // With enough preceding content, the playing line occupies the fourth visible row.
        // For the first three timestamps the list stays at the top, without artificial blanks.
        val firstVisible = firstVisibleLyricsIndexForSync(index)
        programmaticScroll = true
        try {
            if (animated) listState.animateScrollToItem(firstVisible) else listState.scrollToItem(firstVisible)
            // Keep the actual settled center as the reattach zone so restoring auto-follow
            // still works with variable-height Persian/English lyric lines.
            followAnchorPx = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == index }
                ?.let { it.offset + it.size / 2f }
                ?: followAnchorPx
        } finally {
            programmaticScroll = false
        }
    }

    LaunchedEffect(listState.isScrollInProgress, programmaticScroll) {
        if (listState.isScrollInProgress && !programmaticScroll) {
            followMachine.onUserInteraction()
            mode = followMachine.mode
        }
    }

    LaunchedEffect(activeIndex, mode, lines.size) {
        if (followMachine.mode == LyricsFollowMode.FOLLOWING && lines.isNotEmpty()) {
            if (activeIndex < 0) {
                // Before the first timestamp, display the first line at the list's top.
                listState.scrollToItem(0)
                followAnchorPx = -1f
            } else {
                settle(activeIndex, animated = !reduceMotion)
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress, activeIndex, mode) {
        if (listState.isScrollInProgress || mode == LyricsFollowMode.FOLLOWING || activeIndex !in lines.indices) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == activeIndex } ?: return@LaunchedEffect
        val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat().coerceAtLeast(1f)
        val anchor = followAnchorPx.takeIf { it >= 0f }
            ?: (layout.viewportStartOffset + viewportHeight * 0.40f)
        val center = item.offset + item.size / 2f
        followMachine.evaluate(center, anchor, item.size.toFloat(), false, false, false, android.os.SystemClock.uptimeMillis())
        mode = followMachine.mode
        if (mode == LyricsFollowMode.REATTACH_ELIGIBLE) {
            delay(250L)
            if (!listState.isScrollInProgress) {
                val latest = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.activeLineIndex }
                if (latest != null) {
                    val latestLayout = listState.layoutInfo
                    val latestViewport = (latestLayout.viewportEndOffset - latestLayout.viewportStartOffset).toFloat().coerceAtLeast(1f)
                    val latestAnchor = followAnchorPx.takeIf { it >= 0f }
                        ?: (latestLayout.viewportStartOffset + latestViewport * 0.40f)
                    val action = followMachine.evaluate(
                        latest.offset + latest.size / 2f,
                        latestAnchor,
                        latest.size.toFloat(),
                        false,
                        false,
                        false,
                        android.os.SystemClock.uptimeMillis(),
                    )
                    mode = followMachine.mode
                    if (action == LyricsFollowAction.SETTLE_AND_FOLLOW) {
                        settle(state.activeLineIndex, animated = !reduceMotion)
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
                .lyricsEdgeFade(browsingReveal, revealTop = listState.firstVisibleItemIndex == 0)
                .testTag("expanded-lyrics-scroll"),
            // No 120.dp leading gap: the pre-lyrics first line belongs at the top.
            // Trailing space still allows the last playing line to occupy the fourth row.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 12.dp,
                bottom = maxHeight.coerceAtLeast(120.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(lines.size, key = { index -> "${lines[index].timeMs}-${lines[index].originalOrder}" }) { index ->
                val playing = index == activeIndex
                Text(
                    text = lines[index].text,
                    // Playback timing owns emphasis in BOTH modes; only neighboring text changes
                    // opacity and blur when the user begins or ends browsing.
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = lyricLineOpacity(index, activeIndex, browsingReveal)),
                    fontWeight = if (playing) FontWeight.Bold else FontWeight.SemiBold,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = Vazirmatn,
                        fontSize = if (playing) 34.sp else 30.sp,
                        lineHeight = if (playing) 47.sp else 43.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLineSeek(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .blur(lyricLineBlur(index, activeIndex, browsingReveal).dp)
                        .testTag("lyrics-line-$index")
                        .semantics {
                            selected = playing
                            if (playing) stateDescription = "Current lyric"
                        },
                )
            }
        }
        if (mode != LyricsFollowMode.FOLLOWING && activeIndex >= 0) {
            Button(
                onClick = {
                    followMachine.forceFollow()
                    mode = followMachine.mode
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            ) {
                Text("Return to current line")
            }
        }
    }
}

@Composable
private fun EmptyLyricsState(onImport: () -> Unit, onPaste: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("lyrics-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No lyrics yet", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall)
        Row {
            TextButton(onClick = onImport) { Text("Import .lrc") }
            TextButton(onClick = onPaste) { Text("Paste lyrics") }
        }
    }
}

@Composable
internal fun PasteLyricsDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste lyrics") },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= dev.behradhz.meowzix.domain.lyrics.LrcParser.MAX_CHARS) text = it },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                decorationBox = { inner ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Box(Modifier.fillMaxSize().padding(12.dp)) {
                            if (text.isBlank()) Text("Lyrics text", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    }
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onSave(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Modifier.lyricsEdgeFade(browsingReveal: Float = 0f, revealTop: Boolean = false): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha =
                        if (revealTop) 1f else browsingReveal.coerceIn(0f, 1f)),
                    0.12f to Color.Black,
                    0.42f to Color.Black,
                    0.64f to Color.Black,
                    0.88f to Color.Black,
                    1f to Color.Black.copy(alpha = browsingReveal.coerceIn(0f, 1f)),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/** Restore the old playback-synced visual hierarchy, interpolating to readable browsing text. */
internal fun lyricLineOpacity(index: Int, activeIndex: Int, browsingReveal: Float = 0f): Float {
    val syncOpacity = when {
        activeIndex < 0 -> 0.70f
        index == activeIndex -> 1f
        abs(index - activeIndex) == 1 -> 0.62f
        abs(index - activeIndex) == 2 -> 0.38f
        else -> 0.22f
    }
    return syncOpacity + (1f - syncOpacity) * browsingReveal.coerceIn(0f, 1f)
}

internal fun lyricLineBlur(index: Int, activeIndex: Int, browsingReveal: Float = 0f): Float {
    val syncBlur = when {
        activeIndex < 0 -> 0f
        abs(index - activeIndex) <= 1 -> 0f
        abs(index - activeIndex) == 2 -> 1.2f
        else -> 2f
    }
    return syncBlur * (1f - browsingReveal.coerceIn(0f, 1f))
}

/** Three-line compact preview: before the first timestamp line one starts at the top. */
internal fun compactPreviewLineIndices(activeIndex: Int): List<Int> =
    if (activeIndex < 0) listOf(0, 1, 2)
    else listOf(activeIndex - 1, activeIndex, activeIndex + 1)

/** Keep three preceding lyric rows above the synchronized row whenever they exist. */
internal fun firstVisibleLyricsIndexForSync(activeIndex: Int): Int =
    (activeIndex - 3).coerceAtLeast(0)
