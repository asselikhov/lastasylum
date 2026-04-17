package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.RoleBadge
import com.lastasylum.alliance.ui.chat.formatChatTime

@Composable
fun ChatScreen(
    contentPadding: PaddingValues,
    username: String,
    role: String,
    state: ChatState,
    onSendMessage: (String) -> Unit,
    onSelectRoom: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val selectedIndex = remember(state.rooms, state.selectedRoomId) {
        state.rooms.indexOfFirst { it.id == state.selectedRoomId }.coerceAtLeast(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.chat_logged_in, username, role),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stringResource(R.string.chat_voice_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )

        if (state.rooms.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.rooms.forEachIndexed { index, room ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onSelectRoom(room.id) },
                        text = {
                            Text(
                                text = room.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (state.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.chat_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    items(
                        items = state.messages,
                        key = { msg -> chatMessageKey(msg) },
                    ) { message ->
                        ChatBubbleRow(
                            message = message,
                            isMine = message.senderId == state.currentUserId,
                        )
                    }
                }
                if (!state.error.isNullOrBlank()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = state.error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = onClearError) {
                                Text(stringResource(R.string.admin_dismiss_error))
                            }
                        }
                    }
                }
            }

        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 4.dp,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.chat_message_hint)) },
                    singleLine = true,
                )
                TextButton(
                    onClick = {
                        onSendMessage(draft)
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                ) {
                    Text(stringResource(R.string.chat_send))
                }
            }
        }
    }
}

private fun chatMessageKey(message: ChatMessage): String {
    return message._id
        ?: "${message.senderId}_${message.createdAt}_${message.text.hashCode()}"
}

@Composable
private fun ChatBubbleRow(
    message: ChatMessage,
    isMine: Boolean,
) {
    val bubbleDesc = stringResource(
        R.string.cd_chat_message,
        message.senderUsername,
        message.senderRole,
        message.text,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .semantics { contentDescription = bubbleDesc },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isMine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = message.senderUsername,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isMine) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    RoleBadge(role = message.senderRole)
                }
                val time = formatChatTime(message.createdAt)
                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMine) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMine) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
