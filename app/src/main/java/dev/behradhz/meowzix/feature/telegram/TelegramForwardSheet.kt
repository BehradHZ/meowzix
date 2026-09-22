package dev.behradhz.meowzix.feature.telegram

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.behradhz.meowzix.domain.settings.TelegramForwardSettings
import dev.behradhz.meowzix.domain.telegram.TelegramChatSummary
import dev.behradhz.meowzix.domain.telegram.TelegramForwardOptions
import dev.behradhz.meowzix.ui.components.ChatAvatar
import java.util.UUID

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

    ModalBottomSheet(onDismissRequest = { if (!isSending) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
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
                    Text("Forward on Telegram", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "$title · ${artist ?: "Unknown artist"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isSending) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                enabled = !isSending,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search chats") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            ForwardOptionRow(
                title = "Keep source attribution",
                subtitle = "Forward the original Telegram message and show where it came from.",
                checked = includeSourceAttribution,
                enabled = !isSending,
                onCheckedChange = {
                    includeSourceAttribution = it
                    if (it) keepCaption = true
                },
            )
            ForwardOptionRow(
                title = "Keep caption",
                subtitle = if (includeSourceAttribution) {
                    "A true Telegram forward always keeps its caption. Send a copy to change this."
                } else {
                    "Keep the original message caption on the copied message."
                },
                checked = keepCaption,
                enabled = !includeSourceAttribution && !isSending,
                onCheckedChange = { keepCaption = it },
            )
            ForwardOptionRow(
                title = "Use as default",
                subtitle = "Remember these choices for the next song you forward.",
                checked = rememberDefaults,
                enabled = !isSending,
                onCheckedChange = { rememberDefaults = it },
            )

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))

            when {
                isSearching && chats.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                chats.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (query.isBlank()) "No Telegram chats available." else "No chats match your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp),
                ) {
                    items(chats, key = { it.chatId }) { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isSending) {
                                    onForward(
                                        chat.chatId,
                                        TelegramForwardOptions(
                                            includeSourceAttribution = includeSourceAttribution,
                                            keepCaption = keepCaption,
                                        ),
                                        rememberDefaults,
                                    )
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (chat.profilePhotoRef != null) {
                                ChatAvatar(chat.profilePhotoRef, chat.title, size = 46.dp)
                            } else {
                                Surface(
                                    modifier = Modifier.size(46.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Person, contentDescription = null)
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
                                    chat.kind.name.replace('_', ' ').lowercase().replaceFirstChar { it.titlecase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Rounded.Send,
                                contentDescription = "Forward to ${chat.title}",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
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
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}
