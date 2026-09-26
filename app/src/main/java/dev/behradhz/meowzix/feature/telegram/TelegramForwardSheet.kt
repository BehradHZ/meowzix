package dev.behradhz.meowzix.feature.telegram

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.EntryPoint
import androidx.hilt.InstallIn
import androidx.hilt.android.EntryPointAccessors
import androidx.hilt.components.SingletonComponent
import dev.behradhz.meowzix.domain.playback.NowPlayingTrack
import dev.behradhz.meowzix.domain.settings.TelegramForwardSettings
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardOptions
import dev.behradhz.meowzix.domain.telegram.TelegramForwardRepository
import dev.behradhz.meowzix.domain.telegram.TelegramSendJob
import dev.behradhz.meowzix.domain.telegram.TelegramSendState
import dev.behradhz.meowzix.ui.components.ChatAvatar
import dev.behradhz.meowzix.ui.components.GlassSurface
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID
import kotlinx.coroutines.launch

private val TelegramGlass = Color(0xFF151413)
private val TelegramGlassStroke = Color.White.copy(alpha = 0.10f)
private val TelegramGlassFill = Color.White.copy(alpha = 0.055f)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TelegramForwardSheetEntryPoint {
    fun telegramForwardRepository(): TelegramForwardRepository
}

@Composable
fun TelegramForwardSheet(
    track: NowPlayingTrack,
    query: String,
    chats: List<TelegramChatSummary>,
    isSearching: Boolean,
    isSending: Boolean,
    errorMessage: String?,
    defaults: TelegramForwardSettings,
    onQueryChange: (String) -> Unit,
    onForward: (Long, TelegramForwardOptions, Boolean) -> Unit,
    onDismiss: () -> Unit,
) = TelegramForwardSheet(
    trackId = track.id,
    title = track.title,
    artist = track.artist,
    query = query,
    chats = chats,
    isSearching = isSearching,
    isSending = isSending,
    errorMessage = errorMessage,
    defaults = defaults,
    onQueryChange = onQueryChange,
    onForward = onForward,
    onDismiss = onDismiss,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramForwardSheet(
    trackId: UUID,
    title: String,
    artist: String?,
    query: String,
    chats: List<TelegramChatSummary>,
    isSearching: Boolean,
    isSending: Boolean,
    errorMessage: String?,
    defaults: TelegramForwardSettings,
    onQueryChange: (String) -> Unit,
    onForward: (Long, TelegramForwardOptions, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeSourceAttribution by rememberSaveable(trackId.toString()) {
        mutableStateOf(defaults.includeSourceAttribution)
    }
    var keepCaption by rememberSaveable(trackId.toString()) {
        mutableStateOf(defaults.keepCaption)
    }
    var rememberDefaults by rememberSaveable(trackId.toString()) { mutableStateOf(false) }
    var actionError by remember(trackId) { mutableStateOf<String?>(null) }

    val appContext = LocalContext.current.applicationContext
    val repository = remember(appContext) {
        EntryPointAccessors.fromApplication(
            appContext,
            TelegramForwardSheetEntryPoint::class.java,
        ).telegramForwardRepository()
    }
    val sendJobs by repository.sendJobs.collectAsState(initial = emptyList())
    val trackJobs = remember(sendJobs, trackId) {
        sendJobs.filter { it.trackId == trackId }.take(4)
    }
    val scope = rememberCoroutineScope()
    val hazeState = rememberHazeState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        contentColor = Color.White,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
    ) {
        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
            fallbackColor = TelegramGlass.copy(alpha = 0.94f),
            tint = Color.White.copy(alpha = 0.075f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 42.dp, height = 4.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(99.dp)),
                )

                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Send, contentDescription = null)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            "Send to Telegram",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "$title · ${artist ?: "Unknown artist"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }

                if (trackJobs.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        trackJobs.forEach { job ->
                            TelegramSendStatusCard(
                                job = job,
                                onRetry = {
                                    scope.launch {
                                        actionError = runCatching { repository.retrySend(job.id) }
                                            .exceptionOrNull()?.message
                                    }
                                },
                                onCancel = {
                                    scope.launch {
                                        actionError = runCatching { repository.cancelSend(job.id) }
                                            .exceptionOrNull()?.message
                                    }
                                },
                            )
                        }
                    }
                }

                val visibleMessage = actionError ?: errorMessage
                if (!visibleMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    val informational = visibleMessage.startsWith("Already", ignoreCase = true) ||
                        visibleMessage.startsWith("Added", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (informational) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.13f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = visibleMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (informational) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                GlassOptionGroup(
                    includeSourceAttribution = includeSourceAttribution,
                    keepCaption = keepCaption,
                    rememberDefaults = rememberDefaults,
                    onSourceAttributionChange = {
                        includeSourceAttribution = it
                        if (it) keepCaption = true
                    },
                    onKeepCaptionChange = { keepCaption = it },
                    onRememberDefaultsChange = { rememberDefaults = it },
                )

                Spacer(Modifier.height(16.dp))
                Text(
                    "Send to",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )

                // Search intentionally sits immediately above account/chat names.
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.62f),
                        )
                    },
                    placeholder = { Text("Search accounts and chats") },
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = TelegramGlassFill,
                        unfocusedContainerColor = TelegramGlassFill,
                        disabledContainerColor = TelegramGlassFill,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.42f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.42f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, TelegramGlassStroke, RoundedCornerShape(22.dp)),
                )

                Spacer(Modifier.height(8.dp))

                when {
                    isSearching && chats.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    }

                    chats.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (query.isBlank()) "No Telegram chats available." else "No chats match your search.",
                            color = Color.White.copy(alpha = 0.58f),
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 330.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(chats, key = { it.chatId }) { chat ->
                            val latestJob = trackJobs.firstOrNull {
                                it.targetChatId == chat.chatId && it.state != TelegramSendState.CANCELED
                            }
                            TelegramDestinationRow(
                                chat = chat,
                                job = latestJob,
                                onClick = {
                                    actionError = null
                                    onForward(
                                        chat.chatId,
                                        TelegramForwardOptions(
                                            includeSourceAttribution = includeSourceAttribution,
                                            keepCaption = keepCaption,
                                        ),
                                        rememberDefaults,
                                    )
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun GlassOptionGroup(
    includeSourceAttribution: Boolean,
    keepCaption: Boolean,
    rememberDefaults: Boolean,
    onSourceAttributionChange: (Boolean) -> Unit,
    onKeepCaptionChange: (Boolean) -> Unit,
    onRememberDefaultsChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelegramGlassStroke, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = TelegramGlassFill,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            ForwardOptionRow(
                title = "Keep source attribution",
                subtitle = "Preserve the original Telegram source when one exists.",
                checked = includeSourceAttribution,
                enabled = true,
                onCheckedChange = onSourceAttributionChange,
            )
            ForwardOptionRow(
                title = "Keep caption",
                subtitle = if (includeSourceAttribution) {
                    "Telegram keeps the original caption on attributed forwards."
                } else {
                    "Keep the original caption when sending a Telegram copy."
                },
                checked = keepCaption,
                enabled = !includeSourceAttribution,
                onCheckedChange = onKeepCaptionChange,
            )
            ForwardOptionRow(
                title = "Use as default",
                subtitle = "Remember these send options for the next track.",
                checked = rememberDefaults,
                enabled = true,
                onCheckedChange = onRememberDefaultsChange,
            )
        }
    }
}

@Composable
private fun TelegramDestinationRow(
    chat: TelegramChatSummary,
    job: TelegramSendJob?,
    onClick: () -> Unit,
) {
    val blocksNewSend = job != null && job.state != TelegramSendState.CANCELED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.055f), RoundedCornerShape(20.dp))
            .clickable(enabled = !blocksNewSend) { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat.profilePhotoRef != null) {
            ChatAvatar(chat.profilePhotoRef, chat.title, size = 46.dp)
        } else {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                color = TelegramGlassFill,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = Color.White.copy(alpha = 0.70f))
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                chat.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (chat.kind == TelegramChatKind.SAVED_MESSAGES) {
                    "Saved Messages"
                } else {
                    chat.kind.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() }
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.50f),
            )
        }

        when (job?.state) {
            TelegramSendState.SENT -> Icon(
                Icons.Rounded.Check,
                contentDescription = "Sent",
                tint = MaterialTheme.colorScheme.primary,
            )
            TelegramSendState.FAILED,
            TelegramSendState.VERIFYING,
            -> Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = "Send needs attention",
                tint = MaterialTheme.colorScheme.error,
            )
            null,
            TelegramSendState.CANCELED,
            -> Icon(
                Icons.Rounded.Send,
                contentDescription = "Send to ${chat.title}",
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun TelegramSendStatusCard(
    job: TelegramSendJob,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val canRetry = job.state == TelegramSendState.FAILED || job.state == TelegramSendState.VERIFYING
    val canCancel = job.state == TelegramSendState.QUEUED ||
        job.state == TelegramSendState.RETRYING ||
        job.state == TelegramSendState.WAITING_FOR_NETWORK ||
        job.state == TelegramSendState.WAITING_FOR_TELEGRAM ||
        job.state == TelegramSendState.FAILED ||
        job.state == TelegramSendState.VERIFYING

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TelegramGlassStroke, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = TelegramGlassFill,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        job.targetTitle ?: "Telegram chat",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        job.statusLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (job.state) {
                            TelegramSendState.FAILED, TelegramSendState.VERIFYING -> MaterialTheme.colorScheme.error
                            TelegramSendState.SENT -> MaterialTheme.colorScheme.primary
                            else -> Color.White.copy(alpha = 0.58f)
                        },
                    )
                }
                when (job.state) {
                    TelegramSendState.SENT -> Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    TelegramSendState.FAILED,
                    TelegramSendState.VERIFYING,
                    -> Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    TelegramSendState.CANCELED -> Icon(
                        Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.50f),
                    )
                    else -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            if (job.state == TelegramSendState.UPLOADING) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { job.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!job.errorMessage.isNullOrBlank() && job.state != TelegramSendState.SENT) {
                Spacer(Modifier.height(6.dp))
                Text(
                    job.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.50f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (canRetry || canCancel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (canCancel) {
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                    if (canRetry) {
                        TextButton(onClick = onRetry) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Retry", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun TelegramSendJob.statusLabel(): String = when (state) {
    TelegramSendState.QUEUED -> "Queued"
    TelegramSendState.CHECKING -> "Checking Telegram"
    TelegramSendState.WAITING_FOR_NETWORK -> "Waiting for connection"
    TelegramSendState.WAITING_FOR_TELEGRAM -> "Waiting for Telegram reconnect"
    TelegramSendState.PREPARING_FILE -> "Preparing audio"
    TelegramSendState.UPLOADING -> "Uploading $progressPercent%"
    TelegramSendState.SENDING -> "Sending"
    TelegramSendState.VERIFYING -> "Delivery needs verification"
    TelegramSendState.RETRYING -> "Retrying"
    TelegramSendState.SENT -> "Sent"
    TelegramSendState.FAILED -> "Couldn't send"
    TelegramSendState.CANCELED -> "Canceled"
}

@Composable
private fun ForwardOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.48f),
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
