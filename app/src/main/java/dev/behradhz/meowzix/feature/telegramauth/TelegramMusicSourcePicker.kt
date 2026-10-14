package dev.behradhz.meowzix.feature.telegramauth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.telegram.TelegramChatKind
import dev.behradhz.meowzix.domain.telegram.TelegramMusicSourceState
import dev.behradhz.meowzix.domain.telegram.prioritizeTelegramSourceChats
import dev.behradhz.meowzix.ui.components.ChatAvatar
import dev.behradhz.meowzix.ui.components.TelegramGlassChrome
import dev.behradhz.meowzix.ui.components.TelegramGlassInput

/** Forward-style chat rows, but checkbox selection rather than forwarding on tap. */
@Composable
internal fun TelegramMusicSourcePicker(
    state: TelegramMusicSourceState,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onSetSelected: (Long, Boolean) -> Unit,
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
                    fontWeight = FontWeight.Bold, color = Color.White)
                Text("${state.selectedChatIds.size} selected", style = MaterialTheme.typography.bodySmall,
                    color = TelegramGlassChrome.muted)
            }
            TextButton(onClick = onRefresh, enabled = enabled && !state.isLoadingChats) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(5.dp))
                Text("Refresh")
            }
        }
        Text("Choose Telegram chats, channels and Saved Messages. Offline downloads are retained if a source is deselected.",
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
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onClearError) { Text("Dismiss") }
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
                val shape = RoundedCornerShape(20.dp)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(TelegramGlassChrome.base.copy(alpha = 0.28f), shape)
                        .border(
                            1.dp,
                            if (chat.selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.56f)
                            else Color.White.copy(alpha = 0.055f),
                            shape,
                        )
                        .toggleable(
                            value = chat.selected,
                            enabled = enabled,
                            role = Role.Checkbox,
                            onValueChange = { selected -> onSetSelected(chat.chatId, selected) },
                        )
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatAvatar(chat.profilePhotoRef, chat.title, size = 46.dp)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(chat.title, style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (chat.selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                        Text(chat.kind.displayName(), style = MaterialTheme.typography.bodySmall,
                            color = TelegramGlassChrome.muted)
                    }
                    Checkbox(
                        checked = chat.selected,
                        onCheckedChange = null,
                        enabled = enabled,
                    )
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
