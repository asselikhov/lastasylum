package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatConnectionState
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.RoleBadge
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ChatScreen(
    state: ChatState,
    onSendMessage: (String) -> Unit,
    onSelectRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onLoadOlder: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val selectedIndex = remember(state.rooms, state.selectedRoomId) {
        state.rooms.indexOfFirst { it.id == state.selectedRoomId }.coerceAtLeast(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SquadRelayDimens.screenPaddingHorizontal - 4.dp),
    ) {
        if (state.rooms.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 1.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    divider = {},
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
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            },
                        )
                    }
                }
            }
        }

        when (state.connectionState) {
            ChatConnectionState.Connecting,
            ChatConnectionState.Reconnecting,
            -> {
                Text(
                    text = when (state.connectionState) {
                        ChatConnectionState.Connecting ->
                            stringResource(R.string.chat_connection_connecting)
                        else ->
                            stringResource(R.string.chat_connection_reconnecting)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            ChatConnectionState.Disconnected -> {
                if (state.rooms.isNotEmpty() && !state.isLoading && !state.isRoomsLoading) {
                    Text(
                        text = stringResource(R.string.chat_connection_offline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            ChatConnectionState.Connected -> Unit
        }

        LaunchedEffect(listState) {
            snapshotFlow {
                val info = listState.layoutInfo
                val lastIdx = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = info.totalItemsCount
                intArrayOf(
                    lastIdx,
                    total,
                    if (state.hasMoreOlder) 1 else 0,
                    if (state.isLoadingOlder || state.isLoading) 1 else 0,
                )
            }
                .distinctUntilChanged()
                .collect { sig ->
                    val lastIdx = sig[0]
                    val total = sig[1]
                    val hasMore = sig[2] == 1
                    val busy = sig[3] == 1
                    if (total > 4 && lastIdx >= total - 2 && hasMore && !busy) {
                        onLoadOlder()
                    }
                }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp, top = 4.dp),
        ) {
            when {
                state.isRoomsLoading && state.rooms.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                state.rooms.isEmpty() -> {
                    item {
                        Text(
                            text = state.error?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.chat_no_rooms),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (!state.error.isNullOrBlank()) {
                        item {
                            TextButton(onClick = onClearError) {
                                Text(stringResource(R.string.admin_dismiss_error))
                            }
                        }
                    }
                }
                state.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                state.messages.isEmpty() -> {
                    item {
                        Text(
                            text = stringResource(R.string.chat_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    items(
                        items = state.messages,
                        key = { msg -> chatMessageKey(msg) },
                    ) { message ->
                        ChatBubbleRow(
                            message = message,
                            isMine = message.senderId == state.currentUserId,
                        )
                    }
                    if (state.isLoadingOlder) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(4.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            if (state.rooms.isNotEmpty() && !state.error.isNullOrBlank()) {
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
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(top = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(SquadRelayDimens.composerInnerPadding),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(stringResource(R.string.chat_message_hint))
                    },
                    minLines = 1,
                    maxLines = 5,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
                FilledTonalButton(
                    onClick = {
                        onSendMessage(draft.trim())
                        draft = ""
                    },
                    enabled = draft.isNotBlank(),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.padding(bottom = 2.dp),
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
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp,
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
