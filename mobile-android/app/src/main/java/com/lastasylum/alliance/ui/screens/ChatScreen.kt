package com.lastasylum.alliance.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.stickers.ZlobyakaStickerPack
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.chatMessageSemanticsPreview
import com.lastasylum.alliance.ui.chat.replyPreviewText
import com.lastasylum.alliance.ui.chat.canDeleteChatMessage
import com.lastasylum.alliance.ui.chat.RoleBadge
import com.lastasylum.alliance.ui.chat.chatDayKey
import com.lastasylum.alliance.ui.chat.formatChatDaySeparator
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMuted
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMutedIncoming
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.util.telegramAvatarUrl
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private data class ChatListLoadSignal(
    val lastVisibleIndex: Int,
    val totalItems: Int,
    val hasMoreOlder: Boolean,
    val isBusy: Boolean,
)

private sealed interface ChatTimelineEntry {
    data class DaySeparator(val label: String) : ChatTimelineEntry
    data class ChatMessageItem(val message: ChatMessage, val messageIndex: Int) : ChatTimelineEntry
}

/**
 * Newest-first list: true when this bubble sits at the **visual bottom** of a same-sender streak
 * (newer neighbor in list is missing or another user / another day) — Telegram "tail" corner.
 */
private fun chatMessageIsClusterChainBottom(messages: List<ChatMessage>, messageIndex: Int): Boolean {
    if (messages.isEmpty() || messageIndex !in messages.indices) return true
    if (messageIndex == 0) return true
    val m = messages[messageIndex]
    val newer = messages[messageIndex - 1]
    val sid = m.senderId.trim()
    val nid = newer.senderId.trim()
    if (sid.isEmpty() || nid.isEmpty() || sid != nid) return true
    val d0 = chatDayKey(m.createdAt)
    val d1 = chatDayKey(newer.createdAt)
    if (d0 != null && d1 != null && d0 != d1) return true
    return false
}

/** Same streak as the visually newer message (tighter inner top padding). */
private fun chatMessageClusterTightInnerTop(messages: List<ChatMessage>, messageIndex: Int): Boolean {
    if (messageIndex <= 0 || messageIndex !in messages.indices) return false
    val m = messages[messageIndex]
    val newer = messages[messageIndex - 1]
    val sid = m.senderId.trim()
    val nid = newer.senderId.trim()
    if (sid.isEmpty() || nid.isEmpty() || sid != nid) return false
    val d0 = chatDayKey(m.createdAt)
    val d1 = chatDayKey(newer.createdAt)
    return d0 == null || d1 == null || d0 == d1
}

/**
 * Telegram-style: show `[TAG] nick` + role row only on the oldest message of a same-sender run
 * (newest-first list). Avatar column is always shown for incoming messages — otherwise at the bottom
 * of the chat every bubble looks like a "continuation" and the photo never appears in view.
 */
private fun chatMessageShowsClusterHeader(messages: List<ChatMessage>, messageIndex: Int): Boolean {
    if (messages.isEmpty() || messageIndex !in messages.indices) return true
    if (messageIndex == messages.lastIndex) return true
    val m = messages[messageIndex]
    val older = messages[messageIndex + 1]
    val sid = m.senderId.trim()
    val oid = older.senderId.trim()
    if (sid.isEmpty() || oid.isEmpty() || sid != oid) return true
    val d0 = chatDayKey(m.createdAt)
    val d1 = chatDayKey(older.createdAt)
    if (d0 != null && d1 != null && d0 != d1) return true
    return false
}

/** Tighter vertical gap when the visually older neighbor is the same sender (Telegram stack). */
private fun chatBubbleClusterTopSpacing(
    timeline: List<ChatTimelineEntry>,
    timelineIndex: Int,
    message: ChatMessage,
): Dp {
    val sid = message.senderId.trim()
    if (sid.isEmpty()) return 8.dp
    var i = timelineIndex + 1
    while (i < timeline.size) {
        val e = timeline[i]
        if (e is ChatTimelineEntry.DaySeparator) return 10.dp
        if (e is ChatTimelineEntry.ChatMessageItem) {
            val o = e.message
            val same = o.senderId.trim() == sid && o.senderId.trim().isNotEmpty()
            return if (same) 1.dp else 10.dp
        }
        i++
    }
    return 6.dp
}

/** Own message only when both IDs are non-blank and equal (avoids treating every message as own). */
private fun chatMessageIsOwn(message: ChatMessage, currentUserId: String): Boolean {
    val sid = message.senderId.trim()
    val cid = currentUserId.trim()
    if (sid.isEmpty() || cid.isEmpty()) return false
    return sid == cid
}

private fun buildChatTimeline(messages: List<ChatMessage>): List<ChatTimelineEntry> {
    if (messages.isEmpty()) return emptyList()
    val out = ArrayList<ChatTimelineEntry>(messages.size + 8)
    for (i in messages.indices) {
        if (i > 0) {
            val newer = messages[i - 1]
            val older = messages[i]
            val d0 = chatDayKey(newer.createdAt)
            val d1 = chatDayKey(older.createdAt)
            if (d0 != null && d1 != null && d0 != d1) {
                val label = formatChatDaySeparator(older.createdAt)
                if (label.isNotBlank()) {
                    out.add(ChatTimelineEntry.DaySeparator(label))
                }
            }
        }
        out.add(ChatTimelineEntry.ChatMessageItem(messages[i], i))
    }
    return out
}

private enum class MediaPickerTab { Stickers, Gif }

private val ChatIncomingAvatarSize = 38.dp
private val ChatIncomingAvatarEndPad = 6.dp

private val ChatBubbleChainRadius = 18.dp
private val ChatBubbleTailCorner = 3.dp

private fun bubbleShapeOutgoing(isChainBottom: Boolean): RoundedCornerShape =
    if (isChainBottom) {
        RoundedCornerShape(
            topStart = ChatBubbleChainRadius,
            topEnd = ChatBubbleChainRadius,
            bottomStart = ChatBubbleChainRadius,
            bottomEnd = ChatBubbleTailCorner,
        )
    } else {
        RoundedCornerShape(ChatBubbleChainRadius)
    }

private fun bubbleShapeIncoming(isChainBottom: Boolean): RoundedCornerShape =
    if (isChainBottom) {
        RoundedCornerShape(
            topStart = ChatBubbleChainRadius,
            topEnd = ChatBubbleChainRadius,
            bottomStart = ChatBubbleTailCorner,
            bottomEnd = ChatBubbleChainRadius,
        )
    } else {
        RoundedCornerShape(ChatBubbleChainRadius)
    }

private fun readClipboardPlainText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatState,
    typingPeers: Map<String, String>,
    draftMessage: String,
    pickedImageUris: List<Uri>,
    onSelectRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onLoadOlder: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onPickImages: (List<Uri>) -> Unit,
    onRemovePickedImage: (Uri) -> Unit,
    onClearPickedImages: () -> Unit,
    onReplyToMessage: (String) -> Unit,
    onClearReply: () -> Unit,
    onOpenMessageActions: (String) -> Unit,
    onDismissMessageActions: () -> Unit,
    onRequestDeleteMessage: (String) -> Unit,
    onDismissDeleteMessage: () -> Unit,
    onConfirmDeleteMessage: () -> Unit,
    onBeginMessageSelection: (String) -> Unit,
    onToggleMessageSelection: (String) -> Unit,
    onClearMessageSelection: () -> Unit,
    onRequestBulkDelete: () -> Unit,
    onDismissBulkDeleteConfirm: () -> Unit,
    onConfirmDeleteSelectedMessages: () -> Unit,
    onRetrySendFailure: () -> Unit,
    onDismissSendFailure: () -> Unit,
) {
    val listState = remember(state.selectedRoomId) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedRoomId = state.selectedRoomId
    val selectedRoom = remember(selectedRoomId, state.rooms) {
        selectedRoomId?.let { id -> state.rooms.find { it.id == id } }
    }
    val showGlobalTeamNotice = selectedRoom?.allianceId == ChatAllianceIds.GLOBAL &&
        !state.hasTeamProfileForGlobalChat
    val globalComposerLocked = showGlobalTeamNotice
    val activeActionMessage = remember(state.activeActionMessageId, state.messages) {
        state.messages.find { it._id == state.activeActionMessageId }
    }
    val confirmDeleteMessage = remember(state.confirmDeleteMessageId, state.messages) {
        state.messages.find { it._id == state.confirmDeleteMessageId }
    }
    val inSelectionMode = state.selectedMessageIds.isNotEmpty()

    BackHandler(enabled = inSelectionMode && !state.isDeletingSelection) {
        onClearMessageSelection()
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
            val lastIdx = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            val hasMore = hasMoreOlderRef.value
            val busy = isLoadingOlderRef.value || isLoadingRef.value
            ChatListLoadSignal(lastIdx, total, hasMore, busy)
        }
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

    val messagesRef = rememberUpdatedState(state.messages)
    val jumpToQuotedMessage = remember(scope, listState) {
        { targetId: String ->
            val idx = messagesRef.value.indexOfFirst { it._id == targetId }
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

            if (showGlobalTeamNotice) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SquadRelayDimens.itemGap),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
                ) {
                    Text(
                        text = stringResource(R.string.chat_global_team_notice),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            ChatTypingBanner(typingPeers = typingPeers)

            if (inSelectionMode) {
                ChatSelectionToolbar(
                    selectedCount = state.selectedMessageIds.size,
                    isDeleting = state.isDeletingSelection,
                    onClear = onClearMessageSelection,
                    onDelete = onRequestBulkDelete,
                )
            }

            ChatMessagesLazyList(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
                state = state,
                listState = listState,
                jumpToQuotedMessage = jumpToQuotedMessage,
                onOpenMessageActions = onOpenMessageActions,
                onReplyToMessage = onReplyToMessage,
                onClearError = onClearError,
                inSelectionMode = inSelectionMode,
                selectedMessageIds = state.selectedMessageIds,
                onBeginMessageSelection = onBeginMessageSelection,
                onToggleMessageSelection = onToggleMessageSelection,
            )
        }

        if (state.sendFailure != null || (selectedRoomId != null && state.rooms.isNotEmpty())) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // adjustNothing + edge-to-edge: window does not resize; lift composer with IME
                    // insets only (no adjustResize, so no double gap with this padding).
                    .imePadding()
                    .padding(bottom = 6.dp),
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
                        pickedImageUris = pickedImageUris,
                        replyToMessage = state.replyToMessage,
                        isSending = state.isSending,
                        sendEnabled = !globalComposerLocked,
                        readOnly = globalComposerLocked,
                        onDraftChange = onDraftChange,
                        onSendDraft = {
                            if (!globalComposerLocked &&
                                !draftMessage.isBlank() &&
                                !state.isSending
                            ) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onSendDraft()
                            }
                        },
                        onSendStickerPayload = { payload ->
                            if (!globalComposerLocked && !state.isSending) {
                                onSendStickerPayload(payload)
                            }
                        },
                        onPickImages = { uris ->
                            if (!globalComposerLocked && !state.isSending) {
                                onPickImages(uris)
                            }
                        },
                        onRemovePickedImage = onRemovePickedImage,
                        onClearPickedImages = onClearPickedImages,
                        onClearReply = onClearReply,
                    )
                }
            }
        }
    }

    if (!inSelectionMode) {
        activeActionMessage?.let { message ->
            ChatMessageActionsSheet(
                message = message,
                canDelete = canDeleteChatMessage(
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

    if (state.confirmBulkDelete && state.selectedMessageIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissBulkDeleteConfirm,
            title = { Text(stringResource(R.string.chat_bulk_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.chat_bulk_delete_body,
                        state.selectedMessageIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeleteSelectedMessages,
                    enabled = !state.isDeletingSelection,
                ) {
                    Text(
                        text = stringResource(R.string.chat_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissBulkDeleteConfirm,
                    enabled = !state.isDeletingSelection,
                ) {
                    Text(stringResource(R.string.chat_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun ChatSelectionToolbar(
    selectedCount: Int,
    isDeleting: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SquadRelayDimens.itemGap),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClear, enabled = !isDeleting) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.chat_selection_clear_cd),
                )
            }
            Text(
                text = stringResource(R.string.chat_selection_count, selectedCount),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isDeleting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButton(onClick = onDelete, enabled = selectedCount > 0) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.chat_selection_delete_cd),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatDayDivider(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatTypingBanner(typingPeers: Map<String, String>) {
    if (typingPeers.isEmpty()) return
    val names = typingPeers.values.distinct().sorted()
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

@Composable
private fun ChatMessagesLazyList(
    modifier: Modifier = Modifier,
    state: ChatState,
    listState: LazyListState,
    jumpToQuotedMessage: (String) -> Unit,
    onOpenMessageActions: (String) -> Unit,
    onReplyToMessage: (String) -> Unit,
    onClearError: () -> Unit,
    inSelectionMode: Boolean,
    selectedMessageIds: Set<String>,
    onBeginMessageSelection: (String) -> Unit,
    onToggleMessageSelection: (String) -> Unit,
) {
    val minSystemViewport = (LocalConfiguration.current.screenHeightDp * 0.55f).dp.coerceAtLeast(280.dp)
    val timeline = remember(state.messages) { buildChatTimeline(state.messages) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(0.dp),
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
                            .heightIn(min = minSystemViewport),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            state.rooms.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minSystemViewport),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                        ) {
                            Text(
                                text = state.error?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.chat_no_rooms),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = TextAlign.Center,
                            )
                            if (!state.error.isNullOrBlank()) {
                                TextButton(onClick = onClearError) {
                                    Text(stringResource(R.string.admin_dismiss_error))
                                }
                            }
                        }
                    }
                }
            }

            state.isLoading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minSystemViewport),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            state.messages.isEmpty() -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = minSystemViewport),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_empty_state),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            else -> {
                items(
                    count = timeline.size,
                    key = { idx ->
                        when (val e = timeline[idx]) {
                            is ChatTimelineEntry.DaySeparator -> "day:$idx:${e.label}"
                            is ChatTimelineEntry.ChatMessageItem -> chatMessageKey(e.message)
                        }
                    },
                    contentType = { idx ->
                        when (timeline[idx]) {
                            is ChatTimelineEntry.DaySeparator -> "chat_day"
                            is ChatTimelineEntry.ChatMessageItem -> "chat_message"
                        }
                    },
                ) { idx ->
                    when (val e = timeline[idx]) {
                        is ChatTimelineEntry.DaySeparator -> ChatDayDivider(e.label)
                        is ChatTimelineEntry.ChatMessageItem -> {
                            val message = e.message
                            val showClusterHeader = chatMessageShowsClusterHeader(state.messages, e.messageIndex)
                            val clusterTop = chatBubbleClusterTopSpacing(timeline, idx, message)
                            ChatBubbleRow(
                                messages = state.messages,
                                messageIndex = e.messageIndex,
                                message = message,
                                isMine = chatMessageIsOwn(message, state.currentUserId),
                                showClusterHeader = showClusterHeader,
                                clusterTopSpacing = clusterTop,
                                canDelete = canDeleteChatMessage(
                                    message = message,
                                    currentUserId = state.currentUserId,
                                    currentUserRole = state.currentUserRole,
                                ),
                                deleting = state.deletingMessageId == message._id,
                                inSelectionMode = inSelectionMode,
                                isSelected = message._id != null && message._id in selectedMessageIds,
                                onOpenActions = { id -> onOpenMessageActions(id) },
                                onBeginSelection = onBeginMessageSelection,
                                onToggleSelection = onToggleMessageSelection,
                                onSwipeReply = onReplyToMessage,
                                onJumpToQuotedMessage = jumpToQuotedMessage,
                            )
                        }
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatComposer(
    draft: String,
    pickedImageUris: List<Uri>,
    replyToMessage: ChatMessage?,
    isSending: Boolean,
    sendEnabled: Boolean = true,
    readOnly: Boolean = false,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onPickImages: (List<Uri>) -> Unit,
    onRemovePickedImage: (Uri) -> Unit,
    onClearPickedImages: () -> Unit,
    onClearReply: () -> Unit,
) {
    var showMediaPanel by remember { mutableStateOf(false) }
    var mediaTab by remember { mutableStateOf(MediaPickerTab.Stickers) }
    var gifUrlDraft by remember { mutableStateOf("") }
    var showAttachmentsSheet by remember { mutableStateOf(false) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val zlobStems = remember(context) { ZlobyakaStickerPack.listSortedStems(context) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val gifScroll = rememberScrollState()
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (!readOnly && uris.isNotEmpty()) {
                onPickImages(uris)
            }
        },
    )

    LaunchedEffect(showMediaPanel) {
        if (!showMediaPanel) {
            mediaTab = MediaPickerTab.Stickers
            gifUrlDraft = ""
        }
    }

    BackHandler(enabled = showMediaPanel) {
        showMediaPanel = false
    }

    BackHandler(enabled = showAttachmentsSheet || previewUri != null) {
        when {
            previewUri != null -> previewUri = null
            showAttachmentsSheet -> showAttachmentsSheet = false
        }
    }

    if (showAttachmentsSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SquadRelayDimens.contentPaddingHorizontal,
                        vertical = SquadRelayDimens.itemGap,
                    ),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.chat_attachments_sheet_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(
                        onClick = onClearPickedImages,
                        enabled = !readOnly && !isSending,
                    ) {
                        Text(stringResource(R.string.chat_attachments_clear))
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 92.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pickedImageUris, key = { it.toString() }) { uri ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { previewUri = uri },
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(uri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            IconButton(
                                onClick = { onRemovePickedImage(uri) },
                                enabled = !readOnly && !isSending,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                                    .padding(4.dp),
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.38f),
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Cancel,
                                        contentDescription = stringResource(R.string.chat_attachments_remove),
                                        tint = Color.White,
                                        modifier = Modifier.padding(5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    previewUri?.let { uri ->
        ModalBottomSheet(onDismissRequest = { previewUri = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val i = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "image/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(i)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.chat_attachments_open))
                    }
                    OutlinedButton(
                        onClick = {
                            onRemovePickedImage(uri)
                            previewUri = null
                        },
                        enabled = !readOnly && !isSending,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.chat_attachments_remove))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SquadRelayDimens.itemGap),
    ) {
        Surface(
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (showMediaPanel) 0.dp else 20.dp,
                bottomEnd = if (showMediaPanel) 0.dp else 20.dp,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = SquadRelayDimens.composerInnerPadding,
                        bottom = 0.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
            ) {
                if (pickedImageUris.isNotEmpty()) {
                    val maxThumbs = 4
                    val visibleThumbs = pickedImageUris.take(maxThumbs)
                    val extraCount = (pickedImageUris.size - visibleThumbs.size).coerceAtLeast(0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_attachments_label, pickedImageUris.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = onClearPickedImages,
                            enabled = !readOnly && !isSending,
                        ) {
                            Text(stringResource(R.string.chat_attachments_clear))
                        }
                    }
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(visibleThumbs, key = { it.toString() }) { uri ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                                if (extraCount > 0 && uri == visibleThumbs.last()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "+$extraCount",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { showAttachmentsSheet = true },
                                )
                                IconButton(
                                    onClick = { onRemovePickedImage(uri) },
                                    enabled = !readOnly && !isSending,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .padding(2.dp),
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.38f),
                                        tonalElevation = 0.dp,
                                        shadowElevation = 0.dp,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Cancel,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.padding(4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
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
                                    text = stringResource(
                                        R.string.chat_replying_to,
                                        chatSenderDisplayWithTag(reply.senderTeamTag, reply.senderUsername),
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = replyPreviewText(reply.text),
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = SquadRelayDimens.composerMinHeight.coerceAtLeast(48.dp)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    if (readOnly) return@IconButton
                                    if (showMediaPanel) {
                                        showMediaPanel = false
                                        focusRequester.requestFocus()
                                        keyboard?.show()
                                    } else {
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                        mediaTab = MediaPickerTab.Stickers
                                        showMediaPanel = true
                                    }
                                },
                                enabled = !readOnly,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = if (showMediaPanel) {
                                        Icons.Outlined.Keyboard
                                    } else {
                                        Icons.Outlined.Mood
                                    },
                                    contentDescription = if (showMediaPanel) {
                                        stringResource(R.string.chat_show_keyboard_cd)
                                    } else {
                                        stringResource(R.string.chat_open_media_panel_cd)
                                    },
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            BasicTextField(
                                value = draft,
                                onValueChange = { if (!readOnly) onDraftChange(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp, horizontal = 4.dp)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { fc ->
                                        if (!readOnly && fc.isFocused && showMediaPanel) {
                                            showMediaPanel = false
                                            keyboard?.show()
                                        }
                                    },
                                readOnly = readOnly,
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
                            val showInlineSend = sendEnabled && !readOnly && draft.isNotBlank()
                            AnimatedVisibility(visible = showInlineSend || isSending) {
                                IconButton(
                                    onClick = { if (!isSending && showInlineSend) onSendDraft() },
                                    enabled = showInlineSend && !isSending,
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Send,
                                            contentDescription = stringResource(R.string.chat_send),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            if (readOnly) return@IconButton
                            focusManager.clearFocus()
                            keyboard?.hide()
                            pickImagesLauncher.launch("image/*")
                        },
                        enabled = !readOnly && !isSending,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = stringResource(R.string.chat_attach_gif),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showMediaPanel,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = mediaTab == MediaPickerTab.Stickers,
                            onClick = { mediaTab = MediaPickerTab.Stickers },
                            label = { Text(stringResource(R.string.chat_stickers_title)) },
                        )
                        FilterChip(
                            selected = mediaTab == MediaPickerTab.Gif,
                            onClick = { mediaTab = MediaPickerTab.Gif },
                            label = { Text(stringResource(R.string.chat_attach_gif)) },
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                    when (mediaTab) {
                        MediaPickerTab.Stickers -> {
                            Text(
                                text = stringResource(R.string.chat_stickers_pack_zlobyaka),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(zlobStems, key = { it }) { stem ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable(
                                                enabled = sendEnabled && !readOnly,
                                                onClick = {
                                                    onSendStickerPayload(ZlobyakaStickerPack.encode(stem))
                                                    showMediaPanel = false
                                                },
                                            ),
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(ZlobyakaStickerPack.assetUriForStem(stem))
                                                .size(192)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = stringResource(R.string.cd_chat_sticker),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                        }

                        MediaPickerTab.Gif -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .verticalScroll(gifScroll)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_gif_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                OutlinedTextField(
                                    value = gifUrlDraft,
                                    onValueChange = { gifUrlDraft = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.chat_gif_hint)) },
                                    singleLine = false,
                                    maxLines = 3,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            readClipboardPlainText(context)?.let { gifUrlDraft = it }
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentPaste,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .padding(end = 6.dp)
                                                .size(18.dp),
                                        )
                                        Text(
                                            text = stringResource(R.string.chat_gif_paste),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://giphy.com")),
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.chat_gif_open_giphy),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        val url = gifUrlDraft.trim()
                                        if (url.isEmpty()) return@TextButton
                                        val sep = when {
                                            draft.isEmpty() -> ""
                                            draft.endsWith(' ') || draft.endsWith('\n') -> ""
                                            else -> " "
                                        }
                                        onDraftChange(draft + sep + url)
                                        showMediaPanel = false
                                    },
                                    enabled = gifUrlDraft.trim().isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(stringResource(R.string.chat_gif_insert))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSenderAvatar(
    telegramUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    fallbackName: String? = null,
) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    val fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    val trimmed = fallbackName?.trim().orEmpty()
    val initialChar = trimmed.firstOrNull { it.isLetterOrDigit() }
        ?: trimmed.firstOrNull()
    val initial = initialChar?.uppercaseChar()?.toString() ?: "?"
    val ctx = LocalContext.current
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = fill,
        border = BorderStroke(1.dp, ring),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!telegramUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(telegramUrl)
                        .crossfade(220)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

/** Telegram-like header: `[TAG]` + nickname on the left, role badge on the right. */
@Composable
private fun ChatBubbleAuthorHeader(
    teamTag: String?,
    nickname: String,
    nicknameColor: Color,
    tagBracketColor: Color,
    senderRole: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val t = teamTag?.trim()?.takeIf { it.isNotEmpty() }
            if (t != null) {
                Text(
                    text = "[$t]",
                    style = MaterialTheme.typography.labelMedium,
                    color = tagBracketColor,
                    maxLines = 1,
                )
            }
            Text(
                text = nickname.trim().ifBlank { "—" },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = nicknameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        RoleBadge(role = senderRole)
    }
}

@Composable
private fun ChatBubbleInnerColumn(
    message: ChatMessage,
    isMine: Boolean,
    showClusterHeader: Boolean,
    tightClusterTop: Boolean,
    stickerStem: String?,
    senderAccent: Color,
    stemTag: String?,
    nickname: String,
    onBubble: Color,
    timeMuted: Color,
    formattedTime: String,
    swipeModifier: Modifier,
    bubbleContext: Context,
    replyQuoteInteraction: MutableInteractionSource,
    quotedJumpLabel: String,
    onJumpToQuotedMessage: (String) -> Unit,
    deleting: Boolean,
    canDelete: Boolean,
) {
    val bubblePadH = if (stickerStem != null) 8.dp else 12.dp
    val bubblePadBottom = if (stickerStem != null) 8.dp else 10.dp
    val bubblePadTop = when {
        tightClusterTop -> if (stickerStem != null) 5.dp else 6.dp
        stickerStem != null -> 8.dp
        else -> 10.dp
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = bubblePadH)
            .padding(top = bubblePadTop, bottom = bubblePadBottom)
            .then(swipeModifier),
        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
    ) {
        if (!isMine && showClusterHeader) {
            val tagMuted = ChatTelegramIncomingOnBubble.copy(alpha = 0.5f)
            ChatBubbleAuthorHeader(
                teamTag = stemTag,
                nickname = nickname,
                nicknameColor = senderAccent,
                tagBracketColor = tagMuted,
                senderRole = message.senderRole,
            )
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
                shape = RoundedCornerShape(10.dp),
                color = if (isMine) {
                    ChatTelegramOutgoingOnBubble.copy(alpha = 0.14f)
                } else {
                    ChatTelegramIncomingOnBubble.copy(alpha = 0.12f)
                },
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = SquadRelayDimens.itemGap,
                        vertical = SquadRelayDimens.headerSubtitleGap + 2.dp,
                    ),
                ) {
                    Text(
                        text = chatSenderDisplayWithTag(
                            reply.senderTeamTag,
                            reply.senderUsername,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isMine) {
                            ChatTelegramOutgoingOnBubble.copy(alpha = 0.92f)
                        } else {
                            ChatTelegramIncomingOnBubble.copy(alpha = 0.95f)
                        },
                    )
                    Text(
                        text = replyPreviewText(reply),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMine) {
                            ChatTelegramOutgoingOnBubble.copy(alpha = 0.78f)
                        } else {
                            ChatTelegramIncomingOnBubble.copy(alpha = 0.72f)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (stickerStem != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(bubbleContext)
                        .data(ZlobyakaStickerPack.assetUriForStem(stickerStem))
                        .size(384)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_chat_sticker),
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onBubble,
                    modifier = Modifier.weight(1f),
                )
                if (formattedTime.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = timeMuted,
                        modifier = Modifier.padding(bottom = 1.dp),
                    )
                }
            }
        }

        if (stickerStem != null && formattedTime.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = timeMuted,
                )
            }
        }

        if (deleting && canDelete) {
            Text(
                text = stringResource(R.string.chat_deleting_progress),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubbleRow(
    messages: List<ChatMessage>,
    messageIndex: Int,
    message: ChatMessage,
    isMine: Boolean,
    showClusterHeader: Boolean,
    clusterTopSpacing: Dp,
    canDelete: Boolean,
    deleting: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    onOpenActions: (String) -> Unit,
    onBeginSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSwipeReply: (String) -> Unit,
    onJumpToQuotedMessage: (String) -> Unit,
) {
    val messageId = message._id
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val swipePx = remember(density) { with(density) { 56.dp.toPx() } }
    val stickerStem = remember(message.text) { ZlobyakaStickerPack.parseStem(message.text) }
    val textPreview = chatMessageSemanticsPreview(message.text)
    val senderLine = chatSenderDisplayWithTag(message.senderTeamTag, message.senderUsername)
    val bubbleDescription = stringResource(
        R.string.cd_chat_message,
        senderLine,
        message.senderRole,
        textPreview,
    )
    val quotedJumpLabel = stringResource(R.string.chat_quoted_jump_cd)
    val replyQuoteInteraction = remember(message._id, message.replyTo?._id) {
        MutableInteractionSource()
    }
    val telegramUrl = telegramAvatarUrl(message.senderTelegramUsername)
    val floatingSticker = stickerStem != null && message.replyTo == null
    val senderAccent = roleAccentColor(message.senderRole)
    val bubbleBg = if (isMine) ChatTelegramOutgoingBubble else ChatTelegramIncomingBubble
    val onBubble = if (isMine) ChatTelegramOutgoingOnBubble else ChatTelegramIncomingOnBubble
    val timeMuted = if (isMine) ChatTelegramTimeMuted else ChatTelegramTimeMutedIncoming
    val stemTag = message.senderTeamTag?.trim()?.takeIf { it.isNotEmpty() }
    val displayName = message.senderUsername.trim().ifBlank { senderLine }
    val nickname = message.senderUsername.trim().ifBlank { displayName }
    val isChainBottom = chatMessageIsClusterChainBottom(messages, messageIndex)
    val tightClusterTop = chatMessageClusterTightInnerTop(messages, messageIndex)
    val bubbleShape = if (isMine) {
        bubbleShapeOutgoing(isChainBottom)
    } else {
        bubbleShapeIncoming(isChainBottom)
    }
    val bubbleContext = LocalContext.current
    val formattedTime = formatChatTime(message.createdAt)

    val swipeModifier = if (messageId != null) {
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
    }

    val bubbleClickModifier = Modifier
        .semantics(mergeDescendants = true) {
            contentDescription = bubbleDescription
            role = Role.Button
        }
        .combinedClickable(
            onClick = {
                if (inSelectionMode && canDelete && !messageId.isNullOrBlank()) {
                    onToggleSelection(messageId)
                }
            },
            onLongClick = {
                if (messageId.isNullOrBlank()) return@combinedClickable
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (canDelete) {
                    if (inSelectionMode) {
                        onToggleSelection(messageId)
                    } else {
                        onBeginSelection(messageId)
                    }
                } else {
                    onOpenActions(messageId)
                }
            },
        )

    if (isMine) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = clusterTopSpacing),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (inSelectionMode && canDelete) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        if (!messageId.isNullOrBlank()) {
                            onToggleSelection(messageId)
                        }
                    },
                    enabled = !messageId.isNullOrBlank(),
                )
            }
            if (floatingSticker) {
                val floatMod = Modifier
                    .widthIn(max = 232.dp)
                    .then(bubbleClickModifier)
                    .then(swipeModifier)
                Column(modifier = floatMod) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(bubbleContext)
                                .data(ZlobyakaStickerPack.assetUriForStem(stickerStem!!))
                                .size(384)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.cd_chat_sticker),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(bubbleShapeOutgoing(isChainBottom)),
                            contentScale = ContentScale.Fit,
                        )
                        if (formattedTime.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.45f),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp),
                            ) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                    if (deleting && canDelete) {
                        Text(
                            text = stringResource(R.string.chat_deleting_progress),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .widthIn(max = if (stickerStem != null) 280.dp else 300.dp)
                        .then(bubbleClickModifier),
                    shape = bubbleShape,
                    color = bubbleBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    ChatBubbleInnerColumn(
                        message = message,
                        isMine = isMine,
                        showClusterHeader = showClusterHeader,
                        tightClusterTop = tightClusterTop,
                        stickerStem = stickerStem,
                        senderAccent = senderAccent,
                        stemTag = stemTag,
                        nickname = nickname,
                        onBubble = onBubble,
                        timeMuted = timeMuted,
                        formattedTime = formattedTime,
                        swipeModifier = swipeModifier,
                        bubbleContext = bubbleContext,
                        replyQuoteInteraction = replyQuoteInteraction,
                        quotedJumpLabel = quotedJumpLabel,
                        onJumpToQuotedMessage = onJumpToQuotedMessage,
                        deleting = deleting,
                        canDelete = canDelete,
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = clusterTopSpacing),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (inSelectionMode && canDelete) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                        if (!messageId.isNullOrBlank()) {
                            onToggleSelection(messageId)
                        }
                    },
                    enabled = !messageId.isNullOrBlank(),
                )
            }
            ChatSenderAvatar(
                telegramUrl = telegramUrl,
                size = ChatIncomingAvatarSize,
                modifier = Modifier.padding(end = ChatIncomingAvatarEndPad),
                fallbackName = displayName,
            )
            if (floatingSticker) {
                val floatMod = Modifier
                    .widthIn(max = 232.dp)
                    .then(bubbleClickModifier)
                    .then(swipeModifier)
                Column(modifier = floatMod) {
                    if (showClusterHeader) {
                        ChatBubbleAuthorHeader(
                            teamTag = stemTag,
                            nickname = nickname,
                            nicknameColor = senderAccent,
                            tagBracketColor = ChatTelegramIncomingOnBubble.copy(alpha = 0.5f),
                            senderRole = message.senderRole,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(bubbleContext)
                                .data(ZlobyakaStickerPack.assetUriForStem(stickerStem!!))
                                .size(384)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.cd_chat_sticker),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(bubbleShapeIncoming(isChainBottom)),
                            contentScale = ContentScale.Fit,
                        )
                        if (formattedTime.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.45f),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp),
                            ) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                    if (deleting && canDelete) {
                        Text(
                            text = stringResource(R.string.chat_deleting_progress),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .widthIn(max = if (stickerStem != null) 280.dp else 300.dp)
                        .then(bubbleClickModifier),
                    shape = bubbleShape,
                    color = bubbleBg,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    ChatBubbleInnerColumn(
                        message = message,
                        isMine = isMine,
                        showClusterHeader = showClusterHeader,
                        tightClusterTop = tightClusterTop,
                        stickerStem = stickerStem,
                        senderAccent = senderAccent,
                        stemTag = stemTag,
                        nickname = nickname,
                        onBubble = onBubble,
                        timeMuted = timeMuted,
                        formattedTime = formattedTime,
                        swipeModifier = swipeModifier,
                        bubbleContext = bubbleContext,
                        replyQuoteInteraction = replyQuoteInteraction,
                        quotedJumpLabel = quotedJumpLabel,
                        onJumpToQuotedMessage = onJumpToQuotedMessage,
                        deleting = deleting,
                        canDelete = canDelete,
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
                text = chatSenderDisplayWithTag(message.senderTeamTag, message.senderUsername),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val sheetStickerStem = remember(message.text) { ZlobyakaStickerPack.parseStem(message.text) }
            val sheetContext = LocalContext.current
            if (sheetStickerStem != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(sheetContext)
                            .data(ZlobyakaStickerPack.assetUriForStem(sheetStickerStem))
                            .size(200)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.cd_chat_sticker),
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        text = replyPreviewText(message.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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

private fun chatMessageKey(message: ChatMessage): String {
    return message._id
        ?: "${message.senderId}_${message.createdAt}_${message.text.hashCode()}"
}
