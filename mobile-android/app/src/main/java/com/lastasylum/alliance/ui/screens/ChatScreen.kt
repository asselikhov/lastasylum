package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.RoleBadge
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class ChatListLoadSignal(
    val lastVisibleIndex: Int,
    val totalItems: Int,
    val hasMoreOlder: Boolean,
    val isBusy: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ChatScreen(
    state: ChatState,
    draftMessage: String,
    onSelectRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onLoadOlder: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onReplyToMessage: (String) -> Unit,
    onClearReply: () -> Unit,
    onOpenMessageActions: (String) -> Unit,
    onDismissMessageActions: () -> Unit,
    onRequestDeleteMessage: (String) -> Unit,
    onDismissDeleteMessage: () -> Unit,
    onConfirmDeleteMessage: () -> Unit,
    onRetrySendFailure: () -> Unit,
    onDismissSendFailure: () -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedRoomId = state.selectedRoomId
    val activeActionMessage = remember(state.activeActionMessageId, state.messages) {
        state.messages.find { it._id == state.activeActionMessageId }
    }
    val confirmDeleteMessage = remember(state.confirmDeleteMessageId, state.messages) {
        state.messages.find { it._id == state.confirmDeleteMessageId }
    }
    val isNearLatest by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex <= 1 &&
                listState.firstVisibleItemScrollOffset < 48
        }
    }

    val listStateRef = rememberUpdatedState(listState)
    val hasMoreOlderRef = rememberUpdatedState(state.hasMoreOlder)
    val isLoadingOlderRef = rememberUpdatedState(state.isLoadingOlder)
    val isLoadingRef = rememberUpdatedState(state.isLoading)
    val onLoadOlderRef = rememberUpdatedState(onLoadOlder)

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listStateRef.value.layoutInfo
            ChatListLoadSignal(
                lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1,
                totalItems = info.totalItemsCount,
                hasMoreOlder = hasMoreOlderRef.value,
                isBusy = isLoadingOlderRef.value || isLoadingRef.value,
            )
        }.debounce(48)
            .distinctUntilChanged()
            .collect { sig ->
                if (sig.totalItems > 4 &&
                    sig.lastVisibleIndex >= sig.totalItems - 2 &&
                    sig.hasMoreOlder &&
                    !sig.isBusy
                ) {
                    onLoadOlderRef.value()
                }
            }
    }

    LaunchedEffect(state.scrollToLatestNonce) {
        if (state.scrollToLatestNonce == 0L) return@LaunchedEffect
        listState.scrollToItem(0)
    }

    LaunchedEffect(state.newestMessageKey) {
        if (state.newestMessageKey.isNullOrBlank()) return@LaunchedEffect
        if (!isNearLatest) return@LaunchedEffect
        listState.scrollToItem(0)
    }

    val jumpToQuotedMessage = remember(scope, listState, state.messages) {
        { targetId: String ->
            val idx = state.messages.indexOfFirst { it._id == targetId }
            if (idx >= 0) {
                scope.launch {
                    listState.scrollToItem(idx)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = SquadRelayDimens.screenTopPadding),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
        ) {
            ChatRoomsBar(
                rooms = state.rooms,
                selectedRoomId = selectedRoomId,
                onSelectRoom = onSelectRoom,
            )

            if (state.typingPeers.isNotEmpty()) {
                val names = state.typingPeers.values.distinct().sorted()
                Text(
                    text = if (names.size == 1) {
                        stringResource(R.string.chat_typing_one, names.first())
                    } else {
                        stringResource(R.string.chat_typing_many, names.joinToString(", "))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = SquadRelayDimens.itemGap),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.blockGap),
                contentPadding = PaddingValues(
                    top = SquadRelayDimens.itemGap,
                    bottom = SquadRelayDimens.itemGap,
                ),
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
                        contentType = { _ -> "chat_message" },
                    ) { message ->
                        ChatBubbleRow(
                            message = message,
                            isMine = message.senderId == state.currentUserId,
                            canDelete = canDeleteMessage(
                                message = message,
                                currentUserId = state.currentUserId,
                                currentUserRole = state.currentUserRole,
                            ),
                            deleting = state.deletingMessageId == message._id,
                            onOpenActions = { id -> onOpenMessageActions(id) },
                            onSwipeReply = onReplyToMessage,
                            onJumpToQuotedMessage = jumpToQuotedMessage,
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
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = SquadRelayDimens.panelInnerPadding,
                                vertical = SquadRelayDimens.itemGap,
                            ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = onClearError) {
                                Text(stringResource(R.string.admin_dismiss_error))
                            }
                        }
                    }
                }
            }
            }
        }

        if (state.sendFailure != null || (selectedRoomId != null && state.rooms.isNotEmpty())) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.sendFailure?.let { failure ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                                    vertical = SquadRelayDimens.itemGap,
                                ),
                            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                        ) {
                            Text(
                                text = stringResource(R.string.chat_send_failed_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = failure.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = onDismissSendFailure) {
                                    Text(stringResource(R.string.chat_send_failed_dismiss))
                                }
                                TextButton(onClick = onRetrySendFailure) {
                                    Text(stringResource(R.string.chat_send_failed_retry))
                                }
                            }
                        }
                    }
                }

                if (selectedRoomId != null && state.rooms.isNotEmpty()) {
                    ChatComposer(
                        draft = draftMessage,
                        replyToMessage = state.replyToMessage,
                        isSending = state.isSending,
                        onDraftChange = onDraftChange,
                        onSendDraft = {
                            if (!draftMessage.isBlank() && !state.isSending) {
                                onSendDraft()
                            }
                        },
                        onClearReply = onClearReply,
                    )
                }
            }
        }
    }

    activeActionMessage?.let { message ->
        ChatMessageActionsSheet(
            message = message,
            canDelete = canDeleteMessage(
                message = message,
                currentUserId = state.currentUserId,
                currentUserRole = state.currentUserRole,
            ),
            onDismiss = onDismissMessageActions,
            onReply = {
                message._id?.let(onReplyToMessage)
                onDismissMessageActions()
            },
            onDelete = {
                message._id?.let(onRequestDeleteMessage)
            },
        )
    }

    confirmDeleteMessage?.let {
        AlertDialog(
            onDismissRequest = onDismissDeleteMessage,
            title = { Text(stringResource(R.string.chat_delete_title)) },
            text = { Text(stringResource(R.string.chat_delete_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmDeleteMessage) {
                    Text(
                        text = stringResource(R.string.chat_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteMessage) {
                    Text(stringResource(R.string.chat_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun ChatRoomsBar(
    rooms: List<com.lastasylum.alliance.data.chat.ChatRoomDto>,
    selectedRoomId: String?,
    onSelectRoom: (String) -> Unit,
) {
    if (rooms.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
        contentPadding = PaddingValues(bottom = SquadRelayDimens.itemGap),
    ) {
        items(rooms, key = { it.id }) { room ->
            FilterChip(
                selected = room.id == selectedRoomId,
                onClick = { onSelectRoom(room.id) },
                label = {
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

@Composable
private fun ChatComposer(
    draft: String,
    replyToMessage: ChatMessage?,
    isSending: Boolean,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onClearReply: () -> Unit,
) {
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        // Flush to screen edges; only top corners rounded (tab bar already separates from list).
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SquadRelayDimens.itemGap)
            // MainActivity uses adjustResize: the window height already ends above the IME; do not add
            // imePadding() here or it stacks with the resized window and leaves a gap above the keyboard.
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SquadRelayDimens.composerInnerPadding),
            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
        ) {
            replyToMessage?.let { reply ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = SquadRelayDimens.itemGap,
                            vertical = SquadRelayDimens.itemGap,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.chat_replying_to, reply.senderUsername),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = replyPreviewText(reply),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TextButton(onClick = onClearReply) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.chat_reply_cancel),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SquadRelayDimens.composerMinHeight),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            keyboardType = KeyboardType.Text,
                        ),
                        maxLines = 6,
                        decorationBox = { inner ->
                            Box {
                                if (draft.isBlank()) {
                                    Text(
                                        text = stringResource(R.string.chat_message_hint),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }

                FilledIconButton(
                    onClick = onSendDraft,
                    enabled = draft.isNotBlank() && !isSending,
                    modifier = Modifier.size(44.dp),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = stringResource(R.string.chat_send),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubbleRow(
    message: ChatMessage,
    isMine: Boolean,
    canDelete: Boolean,
    deleting: Boolean,
    onOpenActions: (String) -> Unit,
    onSwipeReply: (String) -> Unit,
    onJumpToQuotedMessage: (String) -> Unit,
) {
    val messageId = message._id
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val swipePx = remember(density) { with(density) { 56.dp.toPx() } }
    val bubbleDescription = stringResource(
        R.string.cd_chat_message,
        message.senderUsername,
        message.senderRole,
        message.text.take(120),
    )
    val quotedJumpLabel = stringResource(R.string.chat_quoted_jump_cd)
    val replyQuoteInteraction = remember(message._id, message.replyTo?._id) {
        MutableInteractionSource()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = bubbleDescription
                    role = Role.Button
                }
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (!messageId.isNullOrBlank()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenActions(messageId)
                        }
                    },
                ),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 6.dp,
                bottomEnd = if (isMine) 6.dp else 18.dp,
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
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .then(
                        if (messageId != null) {
                            Modifier.pointerInput(messageId, layoutDirection, swipePx) {
                                var accX = 0f
                                var accY = 0f
                                detectDragGestures(
                                    onDragEnd = {
                                        val dominantHorizontal = kotlin.math.abs(accX) > swipePx &&
                                            kotlin.math.abs(accX) > kotlin.math.abs(accY) * 1.15f
                                        if (dominantHorizontal) {
                                            val towardReply = if (layoutDirection == LayoutDirection.Rtl) {
                                                accX < 0
                                            } else {
                                                accX > 0
                                            }
                                            if (towardReply) {
                                                onSwipeReply(messageId)
                                            }
                                        }
                                        accX = 0f
                                        accY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        accX += dragAmount.x
                                        accY += dragAmount.y
                                        if (kotlin.math.abs(accX) > kotlin.math.abs(accY)) {
                                            change.consume()
                                        }
                                    },
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = message.senderUsername,
                        style = MaterialTheme.typography.labelLarge,
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
                    val time = formatChatTime(message.createdAt)
                    if (time.isNotBlank()) {
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMine) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.68f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                message.replyTo?.let { reply ->
                    Surface(
                        modifier = Modifier
                            .clickable(
                                interactionSource = replyQuoteInteraction,
                                indication = ripple(bounded = true),
                                onClick = { onJumpToQuotedMessage(reply._id) },
                            )
                            .semantics {
                                contentDescription = quotedJumpLabel
                                role = Role.Button
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isMine) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = SquadRelayDimens.itemGap,
                                vertical = SquadRelayDimens.headerSubtitleGap + 2.dp,
                            ),
                        ) {
                            Text(
                                text = reply.senderUsername,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isMine) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Text(
                                text = replyPreviewText(reply),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isMine) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
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

                if (deleting && canDelete) {
                    Text(
                        text = stringResource(R.string.chat_deleting_progress),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatMessageActionsSheet(
    message: ChatMessage,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SquadRelayDimens.contentPaddingHorizontal,
                    vertical = SquadRelayDimens.itemGap,
                ),
            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
        ) {
            Text(
                text = message.senderUsername,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedButton(
                onClick = onReply,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(stringResource(R.string.chat_action_reply))
            }
            if (canDelete) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(stringResource(R.string.chat_action_delete))
                }
            }
        }
    }
}

private fun canDeleteMessage(
    message: ChatMessage,
    currentUserId: String,
    currentUserRole: String,
): Boolean {
    if (message._id.isNullOrBlank()) return false
    return message.senderId == currentUserId || currentUserRole == "R5"
}

private fun chatMessageKey(message: ChatMessage): String {
    return message._id
        ?: "${message.senderId}_${message.createdAt}_${message.text.hashCode()}"
}

private fun replyPreviewText(message: ChatMessage): String = message.text

private fun replyPreviewText(reply: com.lastasylum.alliance.data.chat.ChatMessageReplyPreview): String =
    reply.text
