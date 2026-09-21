package dev.behradhz.meowzix.feature.telegramauth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramMusicSourceState
import dev.behradhz.meowzix.ui.components.ChatAvatar

@Composable
internal fun TelegramMusicSourcePicker(
    state: TelegramMusicSourceState,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onSetSelected: (Long, Boolean) -> Unit,
    onSync: () -> Unit,
    onClearError: () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Music sources",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.selectedChatIds.size} selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            OutlinedButton(
                onClick = onRefresh,
                enabled = enabled && !state.isLoadingChats,
                shape = RoundedCornerShape(18.dp),
            ) {
                androidx.compose.material3.Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("Refresh")
            }
        }

        Text(
            text = "Select a channel, chat, group, or Saved Messages. Selecting a source starts its initial scan automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )

        state.errorMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onClearError) { Text("Dismiss") }
                }
            }
        }

        if (state.isLoadingChats) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(10.dp))
                Text("Loading Telegram chats…")
            }
        } else if (state.chats.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ) {
                Text(
                    text = "No selectable chats loaded yet. Tap Refresh.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            state.chats.forEach { chat ->
                Surface(
                    onClick = { if (enabled) onSetSelected(chat.chatId, !chat.selected) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = if (chat.selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = chat.selected,
                            onCheckedChange = { checked -> onSetSelected(chat.chatId, checked) },
                            enabled = enabled,
                        )
                        ChatAvatar(chat.profilePhotoRef, chat.title, size = 44.dp)
                        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (chat.selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                text = chat.kind.displayName(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
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
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text("Syncing…")
            } else {
                Text("Sync selected sources")
            }
        }

        state.lastSyncResult?.let { result ->
            Text(
                text = "Last sync: ${result.chatsSynced} chats · ${result.messagesScanned} messages · " +
                    "${result.tracksImported} imported · ${result.tracksUpdated} updated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
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
