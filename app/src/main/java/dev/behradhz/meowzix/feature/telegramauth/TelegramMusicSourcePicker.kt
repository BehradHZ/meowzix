package dev.behradhz.meowzix.feature.telegramauth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramMusicSourceMode
import dev.behradhz.meowzix.domain.telegram.TelegramMusicSourceState
import dev.behradhz.meowzix.domain.telegram.TelegramSourceRetry
import dev.behradhz.meowzix.domain.telegram.prioritizeTelegramSourceChats
import dev.behradhz.meowzix.ui.components.ChatAvatar
import dev.behradhz.meowzix.ui.components.TelegramGlassChrome
import dev.behradhz.meowzix.ui.components.TelegramGlassInput

/** Source selection cycles among None, Playlist only, and Library. */
@Composable
internal fun TelegramMusicSourcePicker(
    state: TelegramMusicSourceState,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onSetMode: (Long, TelegramMusicSourceMode) -> Unit,
    onSync: () -> Unit,
    onClearError: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = prioritizeTelegramSourceChats(state.chats, state.previouslySelectedChatIds).filter { chat ->
        query.isBlank() || chat.title.contains(query.trim(), ignoreCase = true) ||
            chat.kind.displayName().contains(query.trim(), ignoreCase = true)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Music sources", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("${state.selectedChatIds.size} selected", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onRefresh, enabled = enabled && !state.isLoadingChats) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(5.dp))
                Text("Refresh")
            }
        }
        Text("Tap a chat's mode to switch between None, Playlist only and Library. Offline music is kept.",
            style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted)
        TelegramGlassInput(
            value = query, onValueChange = { query = it },
            label = "Search chats and channels", modifier = Modifier.fillMaxWidth(),
        )

        state.errorMessage?.let { message ->
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.13f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Telegram needs attention", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End) {
                        state.retryAction?.let { retry ->
                            TextButton(
                                enabled = enabled && !state.isLoadingChats && !state.isSyncing,
                                onClick = {
                                    when (retry) {
                                        TelegramSourceRetry.REFRESH_CHATS -> onRefresh()
                                        TelegramSourceRetry.SYNC_SELECTED -> onSync()
                                    }
                                },
                            ) { Text("Try again") }
                        }
                        TextButton(onClick = onClearError) { Text("Dismiss") }
                    }
                }
            }
        }
        if (state.isLoadingChats) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text("Loading Telegram chats…", modifier = Modifier.padding(start = 10.dp),
                    color = TelegramGlassChrome.muted)
            }
        } else if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (query.isBlank()) "No chats yet. Tap Refresh." else "No chats match your search.",
                    style = MaterialTheme.typography.bodyMedium, color = TelegramGlassChrome.muted)
            }
        } else {
            filtered.forEach { chat ->
                val mode = state.sourceModes[chat.chatId] ?: TelegramMusicSourceMode.NONE
                val modeColor by animateColorAsState(
                    targetValue = when (mode) {
                        TelegramMusicSourceMode.NONE -> TelegramGlassChrome.muted
                        TelegramMusicSourceMode.PLAYLIST_ONLY -> MaterialTheme.colorScheme.secondary
                        TelegramMusicSourceMode.LIBRARY -> MaterialTheme.colorScheme.primary
                    },
                    animationSpec = tween(240),
                    label = "telegram-source-mode-color",
                )
                val shape = RoundedCornerShape(20.dp)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            if (chat.selected) modeColor.copy(alpha = 0.09f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f),
                            shape,
                        )
                        .border(
                            1.dp,
                            if (chat.selected) modeColor.copy(alpha = 0.56f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape,
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatAvatar(chat.profilePhotoRef, chat.title, size = 42.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(chat.title, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (chat.selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(chat.kind.displayName(), style = MaterialTheme.typography.bodySmall,
                            color = TelegramGlassChrome.muted)
                    }
                    Box(
                        modifier = Modifier.widthIn(min = 104.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(modeColor.copy(alpha = if (mode == TelegramMusicSourceMode.NONE) 0.08f else 0.18f))
                            .border(1.dp, modeColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .clickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClickLabel = "Switch to ${mode.next().label}",
                            ) { onSetMode(chat.chatId, mode.next()) }
                            .semantics { stateDescription = mode.label }
                            .testTag("telegram-source-mode-${chat.chatId}")
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = mode,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "telegram-source-mode",
                        ) { current ->
                            Text(
                                current.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                color = if (current == TelegramMusicSourceMode.NONE)
                                    MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onSync,
            enabled = enabled && state.selectedChatIds.isNotEmpty() && !state.isSyncing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            if (state.isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(8.dp))
                Text("Syncing…")
            } else Text("Sync selected sources")
        }
        state.lastSyncResult?.let { result ->
            Text(
                "Last sync: ${result.chatsSynced} chats · ${result.messagesScanned} messages · " +
                    "${result.tracksImported} imported · ${result.tracksUpdated} updated",
                style = MaterialTheme.typography.bodySmall, color = TelegramGlassChrome.muted,
            )
        }
    }
}

private fun TelegramChatKind.displayName(): String = when (this) {
    TelegramChatKind.SAVED_MESSAGES -> "Saved Messages"
    TelegramChatKind.PRIVATE -> "Private chat"
    TelegramChatKind.BASIC_GROUP -> "Group"
    TelegramChatKind.SUPERGROUP_OR_CHANNEL -> "Channel / supergroup"
    TelegramChatKind.SECRET -> "Secret chat"
}
