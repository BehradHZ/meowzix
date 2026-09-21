from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"{label}: expected text not found in {path}")
    p.write_text(text.replace(old, new, 1))


# Telegram artwork: retain the real album-cover thumbnail file id for a background HQ upgrade.
path = "app/src/main/java/dev/behradhz/meowzix/data/telegram/TelegramAudioMessageMapper.kt"
replace_once(
    path,
    "    val fileSizeBytes: Long?,\n    val artworkMinithumbnail: ByteArray?,\n)",
    "    val fileSizeBytes: Long?,\n    val artworkMinithumbnail: ByteArray?,\n    val artworkFileId: Int?,\n)",
    "candidate artwork field",
)
replace_once(
    path,
    "            fileSizeBytes = audio.audio.size.toLong().takeIf { it > 0L },\n            artworkMinithumbnail = audio.albumCoverMinithumbnail?.data?.copyOf(),\n        )",
    "            fileSizeBytes = audio.audio.size.toLong().takeIf { it > 0L },\n            artworkMinithumbnail = audio.albumCoverMinithumbnail?.data?.copyOf(),\n            artworkFileId = audio.albumCoverThumbnail?.file?.id,\n        )",
    "audio artwork id",
)
replace_once(
    path,
    "            fileSizeBytes = document.document.size.toLong().takeIf { it > 0L },\n            artworkMinithumbnail = null,\n        )",
    "            fileSizeBytes = document.document.size.toLong().takeIf { it > 0L },\n            artworkMinithumbnail = null,\n            artworkFileId = null,\n        )",
    "document artwork id",
)

# Upgrade provisional Telegram minithumbnails to the actual JPEG album-cover thumbnail.
path = "app/src/main/java/dev/behradhz/meowzix/data/repository/LocalMusicLibraryRepository.kt"
replace_once(
    path,
    '    private val artworkAttempts = ConcurrentHashMap.newKeySet<String>()\n    private val previewDirectory = File(context.cacheDir, "artwork-preview")\n',
    '    private val artworkAttempts = ConcurrentHashMap.newKeySet<String>()\n    private val previewDirectory = File(context.cacheDir, "artwork-preview")\n    private val artworkDirectory = File(context.filesDir, "artwork")\n',
    "artwork directories",
)
old = '''    override fun prefetchArtwork(trackIds: List<UUID>) {
        val requested = trackIds.map(UUID::toString).filter(artworkAttempts::add)
        if (requested.isEmpty()) return
        artworkScope.launch {
            val client = TdLibClientAdapter.activeOrNull() ?: return@launch
            val wanted = requested.toSet()
            val tracksById = dao.allTracks().filter { it.id in wanted }.associateBy { it.id }
            val sources = dao.allSources().filter {
                it.trackId in wanted &&
                    it.type == TrackSourceType.TELEGRAM_REMOTE &&
                    it.availability != SourceAvailability.MISSING
            }
            val telegramBySource = telegramDao.allTelegramTrackSources().associateBy { it.trackSourceId }
            previewDirectory.mkdirs()

            for (source in sources) {
                val track = tracksById[source.trackId] ?: continue
                if (!track.artworkRef.isNullOrBlank()) continue
                val telegram = telegramBySource[source.id] ?: continue
                val message = runCatching {
                    client.send(TdApi.GetMessage(telegram.chatId, telegram.messageId))
                }.getOrNull() ?: continue
                val bytes = message.toAudioCandidate()?.artworkMinithumbnail ?: continue
                if (bytes.isEmpty()) continue
                val file = File(previewDirectory, "${track.id}.jpg")
                runCatching {
                    file.writeBytes(bytes)
                    dao.setArtworkRef(
                        trackId = track.id,
                        artworkRef = Uri.fromFile(file).toString(),
                        updatedAt = Instant.now().toEpochMilli(),
                    )
                }
            }
        }
    }
'''
new = '''    override fun prefetchArtwork(trackIds: List<UUID>) {
        val requested = trackIds.map(UUID::toString).filter(artworkAttempts::add)
        if (requested.isEmpty()) return
        artworkScope.launch {
            val client = TdLibClientAdapter.activeOrNull()
            if (client == null) {
                requested.forEach(artworkAttempts::remove)
                return@launch
            }
            val wanted = requested.toSet()
            val tracksById = dao.allTracks().filter { it.id in wanted }.associateBy { it.id }
            val sources = dao.allSources().filter {
                it.trackId in wanted &&
                    it.type == TrackSourceType.TELEGRAM_REMOTE &&
                    it.availability != SourceAvailability.MISSING
            }
            val telegramBySource = telegramDao.allTelegramTrackSources().associateBy { it.trackSourceId }
            previewDirectory.mkdirs()
            artworkDirectory.mkdirs()

            for (source in sources) {
                val track = tracksById[source.trackId] ?: continue
                val existingArtwork = track.artworkRef
                val needsUpgrade = existingArtwork.isNullOrBlank() || existingArtwork.contains("/artwork-preview/")
                if (!needsUpgrade) continue
                val telegram = telegramBySource[source.id] ?: continue
                val message = runCatching {
                    client.send(TdApi.GetMessage(telegram.chatId, telegram.messageId))
                }.getOrNull() ?: continue
                val candidate = message.toAudioCandidate() ?: continue

                if (existingArtwork.isNullOrBlank()) {
                    candidate.artworkMinithumbnail
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { bytes ->
                            val previewFile = File(previewDirectory, "${track.id}.jpg")
                            runCatching {
                                previewFile.writeBytes(bytes)
                                dao.setArtworkRef(
                                    trackId = track.id,
                                    artworkRef = Uri.fromFile(previewFile).toString(),
                                    updatedAt = Instant.now().toEpochMilli(),
                                )
                            }
                        }
                }

                val artworkFileId = candidate.artworkFileId ?: continue
                val downloaded = runCatching {
                    client.send(TdApi.DownloadFile(artworkFileId, ARTWORK_PRIORITY, 0L, 0L, true))
                }.getOrNull() ?: continue
                val downloadedPath = downloaded.local.path.takeIf {
                    downloaded.local.isDownloadingCompleted && it.isNotBlank()
                } ?: continue
                val sourceFile = File(downloadedPath).takeIf(File::isFile) ?: continue
                val finalArtwork = File(artworkDirectory, "${track.id}.jpg")
                runCatching {
                    sourceFile.inputStream().buffered().use { input ->
                        finalArtwork.outputStream().buffered().use(input::copyTo)
                    }
                    dao.setArtworkRef(
                        trackId = track.id,
                        artworkRef = Uri.fromFile(finalArtwork).toString(),
                        updatedAt = Instant.now().toEpochMilli(),
                    )
                }
            }
        }
    }
'''
replace_once(path, old, new, "prefetch artwork")
replace_once(
    path,
    "private fun localSourcePriority(source: TrackSourceEntity): Int = when (source.type) {\n",
    "private const val ARTWORK_PRIORITY = 3\n\nprivate fun localSourcePriority(source: TrackSourceEntity): Int = when (source.type) {\n",
    "artwork priority",
)

# Ask the repository to upgrade all artwork; it deduplicates and ignores already-final refs.
path = "app/src/main/java/dev/behradhz/meowzix/feature/library/LibraryViewModel.kt"
old = '''                    repository.prefetchArtwork(
                        tracks.asSequence()
                            .map { it.track }
                            .filter { it.artworkRef.isNullOrBlank() }
                            .map { it.id }
                            .toList(),
                    )
'''
new = '''                    repository.prefetchArtwork(
                        tracks.map { it.track.id },
                    )
'''
replace_once(path, old, new, "library artwork upgrade requests")

# Keep Now Playing and the mini-player synchronized with artwork upgrades in Room.
path = "app/src/main/java/dev/behradhz/meowzix/feature/nowplaying/NowPlayingViewModel.kt"
replace_once(
    path,
    '''    val state = playbackController.state
    val queueState = queueRepository.queueState
    val spectrum = audioVisualizerRepository.spectrum
''',
    '''    val state = combine(playbackController.state, libraryRepository.observeTracks()) { playback, tracks ->
        val current = playback.currentTrack ?: return@combine playback
        val liveTrack = tracks.firstOrNull { it.id == current.id } ?: return@combine playback
        playback.copy(
            currentTrack = current.copy(
                title = liveTrack.title,
                artist = liveTrack.artist,
                artworkRef = liveTrack.artworkRef ?: current.artworkRef,
            ),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        playbackController.state.value,
    )
    val queueState = queueRepository.queueState
    val spectrum = audioVisualizerRepository.spectrum
''',
    "live now-playing metadata",
)

# Spectrum: make a small remote prefix readable before decoding instead of failing once forever.
path = "app/src/main/java/dev/behradhz/meowzix/playback/AndroidAudioVisualizer.kt"
old = '''    private suspend fun resolveReadableUri(sourceUri: String): Uri {
        val uri = Uri.parse(sourceUri)
        if (uri.scheme != TDLIB_SCHEME) return uri
        val fileId = uri.lastPathSegment?.toIntOrNull() ?: return uri
        val client = TdLibClientAdapter.activeOrNull() ?: return uri
        val file = client.send(TdApi.GetFile(fileId))
        val path = file.local.path
        return if (path.isNotBlank() && File(path).isFile) Uri.fromFile(File(path)) else uri
    }
'''
new = '''    private suspend fun resolveReadableUri(sourceUri: String): Uri {
        val uri = Uri.parse(sourceUri)
        if (uri.scheme != TDLIB_SCHEME) return uri
        val fileId = uri.lastPathSegment?.toIntOrNull() ?: return uri
        val client = TdLibClientAdapter.activeOrNull() ?: return uri
        val file = runCatching {
            client.send(
                TdApi.DownloadFile(
                    fileId,
                    VISUALIZER_PRIORITY,
                    0L,
                    VISUALIZER_PREVIEW_BYTES,
                    true,
                ),
            )
        }.getOrNull() ?: runCatching { client.send(TdApi.GetFile(fileId)) }.getOrNull() ?: return uri
        val path = file.local.path
        return if (path.isNotBlank() && File(path).isFile) Uri.fromFile(File(path)) else uri
    }
'''
replace_once(path, old, new, "visualizer remote prefix")
replace_once(
    path,
    '        const val TDLIB_SCHEME = "meowzix-tdlib"\n        const val CODEC_TIMEOUT_US = 8_000L\n',
    '        const val TDLIB_SCHEME = "meowzix-tdlib"\n        const val VISUALIZER_PRIORITY = 12\n        const val VISUALIZER_PREVIEW_BYTES = 2L * 1024L * 1024L\n        const val CODEC_TIMEOUT_US = 8_000L\n',
    "visualizer constants",
)

# Next/previous are playback actions: guarantee playWhenReady after changing the item.
path = "app/src/main/java/dev/behradhz/meowzix/playback/AndroidPlaybackController.kt"
replace_once(
    path,
    "    override fun skipToPrevious() = withController(MediaController::seekToPrevious)\n\n    override fun skipToNext() = withController(MediaController::seekToNext)\n",
    '''    override fun skipToPrevious() = withController { connected ->
        if (connected.hasPreviousMediaItem()) {
            connected.seekToPrevious()
            if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
            connected.play()
        }
    }

    override fun skipToNext() = withController { connected ->
        if (connected.hasNextMediaItem()) {
            connected.seekToNext()
            if (connected.playbackState == Player.STATE_IDLE) connected.prepare()
            connected.play()
        }
    }
''',
    "skip should play",
)

# Explicit Next on a remote item should resolve its playable prefix first.
path = "app/src/main/java/dev/behradhz/meowzix/playback/ResolvingPlaybackController.kt"
replace_once(
    path,
    '''    override fun skipToNext() {
        intentionalSkip = true
        delegate.skipToNext()
    }
''',
    '''    override fun skipToNext() {
        intentionalSkip = true
        scope.launch {
            val queue = delegate.queueState.value
            val nextTrackId = queue.items.getOrNull(queue.currentIndex + 1)?.id
            if (nextTrackId != null && !isLocal(nextTrackId)) {
                runCatching { remoteResolver.prepareForPlayback(nextTrackId) }
            }
            delegate.skipToNext()
        }
    }
''',
    "resolve next remote track",
)

# Swipe navigation: observe before children; suppress only when a child claims a horizontal drag.
Path("app/src/main/java/dev/behradhz/meowzix/navigation/HorizontalSwipeNavigation.kt").write_text('''package dev.behradhz.meowzix.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

fun Modifier.horizontalSwipeNavigation(
    enabled: Boolean = true,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(onSwipeLeft, onSwipeRight) {
    val threshold = 64.dp.toPx()
    val axisSlop = 12.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var totalX = 0f
        var totalY = 0f
        var childClaimedHorizontalDrag = false
        var pressed = true

        while (pressed) {
            val initialEvent = awaitPointerEvent(PointerEventPass.Initial)
            val initialChange = initialEvent.changes.firstOrNull { it.id == down.id } ?: break
            val delta = initialChange.positionChange()
            totalX += delta.x
            totalY += delta.y

            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
            val finalChange = finalEvent.changes.firstOrNull { it.id == down.id } ?: break
            if (
                finalChange.isConsumed &&
                abs(totalX) >= axisSlop &&
                abs(delta.x) > abs(delta.y)
            ) {
                childClaimedHorizontalDrag = true
            }
            pressed = finalChange.pressed
        }

        val horizontalIntent = abs(totalX) > abs(totalY) * 1.15f
        if (!childClaimedHorizontalDrag && horizontalIntent && abs(totalX) >= threshold) {
            if (totalX < 0f) onSwipeLeft() else onSwipeRight()
        }
    }
}
''')

# Now Playing opens upward from the bottom and closes downward.
path = "app/src/main/java/dev/behradhz/meowzix/navigation/MeowzixApp.kt"
replace_once(
    path,
    "package dev.behradhz.meowzix.navigation\n\nimport androidx.compose.foundation.clickable\n",
    '''package dev.behradhz.meowzix.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
''',
    "player transition imports",
)
old = '''            composable(NOW_PLAYING_ROUTE) {
                NowPlayingRoute(
                    onBack = navController::popBackStack,
                    onOpenQueue = {
                        navController.navigate(QUEUE_ROUTE) { launchSingleTop = true }
                    },
                )
            }
'''
new = '''            composable(
                route = NOW_PLAYING_ROUTE,
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    )
                },
                exitTransition = {
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    )
                },
                popExitTransition = {
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                    )
                },
            ) {
                NowPlayingRoute(
                    onBack = navController::popBackStack,
                    onOpenQueue = {
                        navController.navigate(QUEUE_ROUTE) { launchSingleTop = true }
                    },
                )
            }
'''
replace_once(path, old, new, "now-playing slide transition")

# Spotify-style track rows: transparent row shell, with swipe background only while swiping.
path = "app/src/main/java/dev/behradhz/meowzix/feature/library/LibraryRoute.kt"
old = '''        backgroundContent = {
            val playNext = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                color = if (playNext) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = if (playNext) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playNext) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Play next", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text("Add to queue", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.size(8.dp))
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                    }
                }
            }
        },
'''
new = '''        backgroundContent = {
            val direction = dismissState.dismissDirection
            if (direction != SwipeToDismissBoxValue.Settled) {
                val playNext = direction == SwipeToDismissBoxValue.StartToEnd
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (playNext) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = if (playNext) Arrangement.Start else Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (playNext) {
                            Icon(Icons.Rounded.PlaylistPlay, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Play next", style = MaterialTheme.typography.labelLarge)
                        } else {
                            Text("Add to queue", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.size(8.dp))
                            Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                        }
                    }
                }
            }
        },
'''
replace_once(path, old, new, "track swipe background")
old = '''    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
'''
new = '''    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
'''
replace_once(path, old, new, "spotify track row shell")
