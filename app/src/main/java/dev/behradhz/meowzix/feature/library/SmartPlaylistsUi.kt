package dev.behradhz.meowzix.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.behradhz.meowzix.domain.library.LibraryTrackAvailability
import dev.behradhz.meowzix.domain.library.PlaylistRule
import dev.behradhz.meowzix.domain.library.RuleKind
import dev.behradhz.meowzix.domain.library.RuleMatchMode
import dev.behradhz.meowzix.domain.library.RulePlaylistRecord
import dev.behradhz.meowzix.domain.library.RulePlaylistSort
import dev.behradhz.meowzix.domain.playback.PlaybackMode
import kotlinx.coroutines.launch

@Composable
internal fun SmartPlaylistsOverlay(
    ruleViewModel: RulePlaylistsViewModel,
    libraryViewModel: LibraryViewModel,
    onOpenNowPlaying: () -> Unit,
) {
    val rulesState by ruleViewModel.state.collectAsStateWithLifecycle()
    val liveTracks by ruleViewModel.selectedTracks.collectAsStateWithLifecycle()
    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 152.dp)
                .clickable { open = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Smart playlists", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (rulesState.records.isNotEmpty()) {
                    Text("${rulesState.records.size}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (!open) return

    ModalBottomSheet(
        onDismissRequest = {
            open = false
            ruleViewModel.backToList()
        },
    ) {
        when {
            rulesState.editing != null -> SmartPlaylistEditor(
                state = rulesState,
                onMatch = ruleViewModel::setMatchMode,
                onSort = ruleViewModel::setSort,
                onRemoveRule = ruleViewModel::removeRule,
                onAddRule = ruleViewModel::addRule,
                onSave = ruleViewModel::saveEdit,
                onBack = ruleViewModel::cancelEdit,
            )
            rulesState.selectedPlaylistId != null -> {
                val selectedId = rulesState.selectedPlaylistId
                val selected = rulesState.records.firstOrNull { it.definition.playlistId == selectedId }
                SmartPlaylistDetail(
                    record = selected,
                    tracks = liveTracks,
                    busy = rulesState.busy,
                    error = rulesState.error,
                    currentTrackId = libraryState.playback.currentTrack?.id,
                    availability = libraryState.availability,
                    downloads = libraryState.downloads,
                    playlists = libraryState.playlists,
                    onBack = ruleViewModel::backToList,
                    onEdit = ruleViewModel::editSelected,
                    onDelete = ruleViewModel::deleteSelected,
                    onPlay = {
                        if (selectedId != null) {
                            scope.launch {
                                val snapshot = ruleViewModel.playbackSnapshot(selectedId)
                                if (snapshot.isNotEmpty()) {
                                    libraryViewModel.playCollection(snapshot, PlaybackMode.ORDERED)
                                    onOpenNowPlaying()
                                }
                            }
                        }
                    },
                    onPlayTrack = { track ->
                        libraryViewModel.playTrack(track, liveTracks)
                        onOpenNowPlaying()
                    },
                    onPlayNext = libraryViewModel::playNext,
                    onAddToQueue = libraryViewModel::addToQueue,
                    onPinOffline = libraryViewModel::pinOffline,
                    onFavorite = libraryViewModel::setFavorite,
                    onAddToPlaylist = libraryViewModel::addToPlaylist,
                    onEnsureArtwork = libraryViewModel::ensureArtwork,
                )
            }
            else -> SmartPlaylistHome(
                state = rulesState,
                onOpen = ruleViewModel::select,
                onCreateStarter = ruleViewModel::createStarter,
            )
        }
        Spacer(Modifier.size(22.dp))
    }
}

@Composable
private fun SmartPlaylistHome(
    state: RulePlaylistsUiState,
    onOpen: (RulePlaylistRecord) -> Unit,
    onCreateStarter: (SmartPlaylistStarter) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("smart-heading") {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(10.dp))
                    Text("Smart playlists", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Rule-based collections update automatically from your local library. Playback uses a snapshot of membership when you press Play.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let { Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }
            }
        }
        if (state.records.isNotEmpty()) {
            item("smart-existing-label") {
                Text("Your smart playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(state.records, key = { it.definition.playlistId }) { record ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !state.busy) { onOpen(record) },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(record.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                ruleSummary(record.definition.rules, record.definition.matchMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        item("smart-starters-label") {
            Text("Starter templates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(RulePlaylistsViewModel.STARTERS, key = { it.title }) { starter ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(starter.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            ruleSummary(starter.rules, starter.matchMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(enabled = !state.busy, onClick = { onCreateStarter(starter) }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartPlaylistDetail(
    record: RulePlaylistRecord?,
    tracks: List<dev.behradhz.meowzix.core.model.Track>,
    busy: Boolean,
    error: String?,
    currentTrackId: java.util.UUID?,
    availability: Map<java.util.UUID, LibraryTrackAvailability>,
    downloads: Map<java.util.UUID, dev.behradhz.meowzix.domain.downloads.OfflineDownload>,
    playlists: List<dev.behradhz.meowzix.domain.library.PlaylistSummary>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrack: (dev.behradhz.meowzix.core.model.Track) -> Unit,
    onPlayNext: (dev.behradhz.meowzix.core.model.Track) -> Unit,
    onAddToQueue: (dev.behradhz.meowzix.core.model.Track) -> Unit,
    onPinOffline: (dev.behradhz.meowzix.core.model.Track) -> Unit,
    onFavorite: (dev.behradhz.meowzix.core.model.Track) -> Unit,
    onAddToPlaylist: (dev.behradhz.meowzix.core.model.Track, java.util.UUID) -> Unit,
    onEnsureArtwork: (dev.behradhz.meowzix.core.model.Track) -> Unit,
) {
    Column(Modifier.fillMaxWidth().heightIn(min = 520.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(record?.title ?: "Smart playlist", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${tracks.size} live matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(enabled = !busy, onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = "Edit rules") }
            IconButton(enabled = !busy, onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete smart playlist") }
            Button(enabled = !busy && tracks.isNotEmpty(), onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(4.dp))
                Text("Play")
            }
        }
        record?.let {
            Text(
                ruleSummary(it.definition.rules, it.definition.matchMode),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No tracks match these rules right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(tracks, key = { it.id }) { track ->
                    SwipeableLibraryTrackRow(
                        track = track,
                        isCurrent = currentTrackId == track.id,
                        onClick = { onPlayTrack(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onPinOffline = { onPinOffline(track) },
                        availability = availability[track.id] ?: LibraryTrackAvailability.UNAVAILABLE,
                        download = downloads[track.id],
                        playlists = playlists,
                        onFavorite = { onFavorite(track) },
                        onAddToPlaylist = { playlistId -> onAddToPlaylist(track, playlistId) },
                        onGoToArtist = {},
                        onEnsureArtwork = { onEnsureArtwork(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartPlaylistEditor(
    state: RulePlaylistsUiState,
    onMatch: (RuleMatchMode) -> Unit,
    onSort: (RulePlaylistSort) -> Unit,
    onRemoveRule: (Int) -> Unit,
    onAddRule: (RuleKind, String?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val edit = state.editing ?: return
    var addMenu by remember { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<RuleKind?>(null) }
    var pendingValue by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().heightIn(min = 520.dp).padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text("Edit rules", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Changes update membership live.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(enabled = !state.busy && edit.rules.isNotEmpty(), onClick = onSave) { Text("Save") }
        }

        Text("Match", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RuleMatchMode.entries.forEach { mode -> ChoicePill(mode.name.lowercase().replaceFirstChar(Char::uppercase), edit.matchMode == mode) { onMatch(mode) } }
        }

        Text("Sort", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RulePlaylistSort.entries.forEach { sort -> ChoicePill(sortLabel(sort), edit.sort == sort) { onSort(sort) } }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Rules", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Box {
                TextButton(onClick = { addMenu = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Add rule")
                }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    RuleKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(ruleKindLabel(kind)) },
                            onClick = {
                                addMenu = false
                                if (kind.needsValue()) {
                                    pendingKind = kind
                                    pendingValue = if (kind == RuleKind.ADDED_WITHIN_DAYS || kind == RuleKind.NOT_LISTENED_WITHIN_DAYS) "30" else ""
                                } else {
                                    onAddRule(kind, null)
                                }
                            },
                        )
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(edit.rules.size, key = { index -> "$index:${edit.rules[index].kind}:${edit.rules[index].value}" }) { index ->
                val rule = edit.rules[index]
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(ruleKindLabel(rule.kind), style = MaterialTheme.typography.titleSmall)
                            rule.value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        TextButton(enabled = edit.rules.size > 1, onClick = { onRemoveRule(index) }) { Text("Remove") }
                    }
                }
            }
        }
        state.error?.let { Text(it, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
    }

    pendingKind?.let { kind ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingKind = null },
            title = { Text(ruleKindLabel(kind)) },
            text = {
                OutlinedTextField(
                    value = pendingValue,
                    onValueChange = { pendingValue = it },
                    label = { Text(if (kind == RuleKind.ARTIST_IS) "Artist" else if (kind == RuleKind.ALBUM_IS) "Album" else "Days") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddRule(kind, pendingValue)
                    pendingKind = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { pendingKind = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

private fun ruleSummary(rules: List<PlaylistRule>, mode: RuleMatchMode): String {
    val joiner = if (mode == RuleMatchMode.ALL) " · AND · " else " · OR · "
    return rules.joinToString(joiner) { rule ->
        when (rule.kind) {
            RuleKind.FAVORITE -> "Favorite"
            RuleKind.OFFLINE -> "Offline"
            RuleKind.ADDED_WITHIN_DAYS -> "Added in ${rule.value ?: "?"}d"
            RuleKind.NOT_LISTENED_WITHIN_DAYS -> "Not listened in ${rule.value ?: "?"}d"
            RuleKind.ARTIST_IS -> "Artist = ${rule.value.orEmpty()}"
            RuleKind.ALBUM_IS -> "Album = ${rule.value.orEmpty()}"
        }
    }
}

private fun ruleKindLabel(kind: RuleKind): String = when (kind) {
    RuleKind.FAVORITE -> "Favorite"
    RuleKind.OFFLINE -> "Offline"
    RuleKind.ADDED_WITHIN_DAYS -> "Added within days"
    RuleKind.NOT_LISTENED_WITHIN_DAYS -> "Not listened within days"
    RuleKind.ARTIST_IS -> "Artist is"
    RuleKind.ALBUM_IS -> "Album is"
}

private fun RuleKind.needsValue(): Boolean = when (this) {
    RuleKind.FAVORITE, RuleKind.OFFLINE -> false
    RuleKind.ADDED_WITHIN_DAYS, RuleKind.NOT_LISTENED_WITHIN_DAYS, RuleKind.ARTIST_IS, RuleKind.ALBUM_IS -> true
}

private fun sortLabel(sort: RulePlaylistSort): String = when (sort) {
    RulePlaylistSort.RECENTLY_ADDED -> "Recently added"
    RulePlaylistSort.TITLE -> "Title"
    RulePlaylistSort.ARTIST -> "Artist"
    RulePlaylistSort.LAST_PLAYED -> "Last played"
}