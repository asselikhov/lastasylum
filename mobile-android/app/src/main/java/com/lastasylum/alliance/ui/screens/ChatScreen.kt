package com.lastasylum.alliance.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.ScrollableDefaults
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.chat.ChatAllianceIds
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatAttachment
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomKind
import com.lastasylum.alliance.data.chat.ChatRoomKindResolver
import com.lastasylum.alliance.data.chat.stickers.StickerPacks
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.data.chat.chatImageAttachments
import com.lastasylum.alliance.data.chat.hasVisibleText
import com.lastasylum.alliance.data.chat.isChatImage
import com.lastasylum.alliance.overlay.OverlayInteractionSuppressEffect
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.data.chat.chatSenderDisplayWithTag
import com.lastasylum.alliance.ui.chat.ChatState
import com.lastasylum.alliance.ui.chat.ChatListPaneState
import com.lastasylum.alliance.ui.chat.ChatChromePaneState
import com.lastasylum.alliance.ui.chat.PinnedMessagesCompactChip
import com.lastasylum.alliance.ui.chat.PinnedMessageBar
import com.lastasylum.alliance.overlay.OverlayMarkAllReadConfirmDialog
import com.lastasylum.alliance.overlay.OverlayMarkAsReadIconButton
import com.lastasylum.alliance.ui.chat.PinnedMessagesSheet
import com.lastasylum.alliance.ui.chat.chatPinPreviewDisplayState
import com.lastasylum.alliance.ui.chat.formatPinnedMetaLine
import com.lastasylum.alliance.ui.chat.isPinnedPreviewLikelyDeleted
import com.lastasylum.alliance.ui.chat.isPinnedPreviewUnavailable
import com.lastasylum.alliance.ui.chat.resolvedThumbnailUrl
import com.lastasylum.alliance.ui.util.formatForumTopicTimeRu
import com.lastasylum.alliance.ui.chat.canPinChatMessage
import com.lastasylum.alliance.ui.chat.resolveChatPinAllianceId
import com.lastasylum.alliance.ui.chat.ChatComposerPaneState
import com.lastasylum.alliance.ui.chat.toListPane
import com.lastasylum.alliance.ui.chat.toChromePane
import com.lastasylum.alliance.ui.chat.toComposerPane
import com.lastasylum.alliance.ui.chat.toChatListUiState
import com.lastasylum.alliance.ui.chat.SquadRelayImageRequests
import com.lastasylum.alliance.ui.chat.chatAuthedImageRequest
import com.lastasylum.alliance.ui.chat.ChatVoicePhase
import com.lastasylum.alliance.ui.chat.chatMessageSemanticsPreview
import com.lastasylum.alliance.ui.chat.replyPreviewText
import com.lastasylum.alliance.ui.chat.canDeleteChatMessage
import com.lastasylum.alliance.ui.chat.RoleBadge
import com.lastasylum.alliance.ui.chat.chatDayKey
import com.lastasylum.alliance.ui.chat.formatChatDaySeparator
import com.lastasylum.alliance.ui.chat.formatChatTime
import com.lastasylum.alliance.ui.chat.ChatBubbleAttachmentsWithCaption
import com.lastasylum.alliance.ui.chat.MessengerImagesPreviewHost
import com.lastasylum.alliance.ui.components.ChatRoomTabAccents
import com.lastasylum.alliance.ui.components.ChatRoomTabSpec
import com.lastasylum.alliance.ui.components.ChatRoomVisualKind
import com.lastasylum.alliance.ui.components.ChatRoomsSwitcher
import com.lastasylum.alliance.ui.chat.ChatViewModel
import com.lastasylum.alliance.ui.chat.ChatBubbleAuthorHeader
import com.lastasylum.alliance.ui.chat.ChatMessengerStyle
import com.lastasylum.alliance.ui.chat.ChatMessageBodyText
import com.lastasylum.alliance.ui.chat.chatBubbleSurfaceWidth
import com.lastasylum.alliance.ui.chat.ChatMessageTimeOverlayChip
import com.lastasylum.alliance.ui.chat.ChatMessageTimeWithReadStatus
import com.lastasylum.alliance.ui.chat.isChatMessageReadByPeer
import com.lastasylum.alliance.ui.chat.MessageActionOpenRequest
import com.lastasylum.alliance.ui.chat.MessageContextMenuActions
import com.lastasylum.alliance.ui.chat.MessageContextMenuPopup
import com.lastasylum.alliance.ui.chat.MessageContextMenuScrim
import com.lastasylum.alliance.ui.chat.saveChatImagesToGallery
import com.lastasylum.alliance.ui.chat.handleMessageLongPressForSelection
import com.lastasylum.alliance.ui.chat.handleMessageTapForActions
import com.lastasylum.alliance.ui.chat.ChatScrollToLatestFab
import com.lastasylum.alliance.ui.chat.isAtReverseChatBottom
import com.lastasylum.alliance.ui.chat.scrollReverseChatRevealLatest
import com.lastasylum.alliance.ui.chat.scrollReverseChatToLatest
import com.lastasylum.alliance.ui.chat.scrollTimelineItemToViewportCenter
import com.lastasylum.alliance.ui.chat.ChatMessagesListDerived
import com.lastasylum.alliance.ui.chat.clusterTopSpacingAt
import com.lastasylum.alliance.ui.chat.chatTimelineDaySeparatorKey
import com.lastasylum.alliance.ui.chat.toChatListUiState
import com.lastasylum.alliance.ui.chat.ChatListUiState
import com.lastasylum.alliance.ui.chat.ChatTimelineEntry
import com.lastasylum.alliance.ui.chat.ChatTypingIndicator
import com.lastasylum.alliance.ui.chat.chatMessageIsOwn
import com.lastasylum.alliance.ui.chat.chatMessageKey
import com.lastasylum.alliance.ui.chat.chatLazyIndexForMessageId
import com.lastasylum.alliance.ui.chat.chatTimelineIndexForMessageId
import com.lastasylum.alliance.ui.chat.ChatSenderAvatar
import com.lastasylum.alliance.ui.chat.ChatSenderAvatarWithSquadRank
import com.lastasylum.alliance.ui.chat.ChatIncomingAvatarEndPad
import com.lastasylum.alliance.ui.chat.ChatIncomingAvatarSize
import com.lastasylum.alliance.ui.chat.AttachmentPreviewOverlay
import com.lastasylum.alliance.ui.chat.ChatComposer
import com.lastasylum.alliance.ui.chat.ChatComposerBar
import com.lastasylum.alliance.ui.chat.ChatFileAttachmentCard
import com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthCap
import com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthFraction
import com.lastasylum.alliance.ui.chat.ChatMessageBubbleRow
import com.lastasylum.alliance.ui.chat.ChatMessageClusterFlags
import com.lastasylum.alliance.ui.chat.ChatListImagePrefetchEffect
import com.lastasylum.alliance.ui.chat.LocalChatBubbleMaxWidth
import com.lastasylum.alliance.ui.chat.LocalMessageExpandScrollCompensation
import com.lastasylum.alliance.ui.chat.scrollReverseChatCompensateExpand
import com.lastasylum.alliance.ui.chat.LocalChatHighlightMessageId
import com.lastasylum.alliance.ui.chat.LocalOpenRemoteChatImagePreview
import com.lastasylum.alliance.ui.chat.ChatMessageReactionsRow
import com.lastasylum.alliance.ui.chat.chatBubbleExpandsToRowWidth
import com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthCap
import com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthFraction
import com.lastasylum.alliance.ui.chat.ChatOverlayBubbleMaxWidthCap
import com.lastasylum.alliance.ui.chat.ChatOverlayBubbleMaxWidthFraction
import com.lastasylum.alliance.ui.chat.chatBubbleWidth
import com.lastasylum.alliance.ui.chat.chatMessageShowsEditedLabel
import com.lastasylum.alliance.ui.chat.chatBubbleShapeIncoming
import com.lastasylum.alliance.ui.chat.chatBubbleShapeOutgoing
import com.lastasylum.alliance.ui.chat.TelegramImageCaptionBar
import com.lastasylum.alliance.ui.chat.TelegramLikeAttachmentsGrid
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramIncomingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramOutgoingOnBubble
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMuted
import com.lastasylum.alliance.ui.theme.ChatTelegramTimeMutedIncoming
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.theme.roleAccentColor
import com.lastasylum.alliance.ui.util.chatRoomTabLabelForServer
import com.lastasylum.alliance.ui.util.chatMessageHasMenuCopyAction
import com.lastasylum.alliance.ui.util.copyChatMessageToClipboard
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.lastasylum.alliance.ui.chat.resolvedChatAttachmentImageUrl

private fun openPickedImageInExternalViewer(context: Context, uri: Uri): Boolean {
    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val resolved = context.packageManager.resolveActivity(
        viewIntent,
        PackageManager.MATCH_DEFAULT_ONLY,
    ) ?: return false
    runCatching {
        context.grantUriPermission(
            resolved.activityInfo.packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
    return runCatching { context.startActivity(viewIntent) }.isSuccess
}

private data class ChatListLoadSignal(
    val lastVisibleIndex: Int,
    val totalItems: Int,
    val hasMoreOlder: Boolean,
    val isBusy: Boolean,
)

private data class ChatScrollAnchor(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val timelineSize: Int,
)

private fun findChatMessage(messages: List<ChatMessage>, id: String?): ChatMessage? {
    val key = id?.trim().orEmpty()
    if (key.isEmpty()) return null
    return messages.firstOrNull { it._id?.trim() == key }
}

private fun messageIdsForTimelineIndex(
    timeline: List<ChatTimelineEntry>,
    messages: List<ChatMessage>,
    index: Int,
): List<String> = when (val entry = timeline.getOrNull(index)) {
    is ChatTimelineEntry.ChatMessageItem ->
        listOfNotNull(entry.message._id?.trim()?.takeIf { it.isNotEmpty() })
    is ChatTimelineEntry.ChatAlbumItem ->
        entry.memberMessageIds.mapNotNull { it.trim().takeIf { id -> id.isNotEmpty() } }
    else -> emptyList()
}

@OptIn(FlowPreview::class)
@Composable
private fun ChatScreenMessagesHost(
    modifier: Modifier,
    topPadding: Modifier,
    compactOverlayMode: Boolean,
    overlayUi: Boolean,
    listPane: ChatListPaneState,
    chromePane: ChatChromePaneState,
    listDerived: ChatMessagesListDerived,
    listUiState: ChatListUiState,
    typingPeers: Map<String, String>,
    otherReadUptoMessageId: String?,
    onSelectRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onLoadOlder: () -> Unit,
    onJumpToQuotedMessage: (String) -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onOpenMessageActions: (MessageActionOpenRequest) -> Unit,
    onBeginMessageSelection: (String) -> Unit,
    onReplyToMessage: (String) -> Unit,
    onToggleMessageSelection: (String) -> Unit,
    onClearMessageSelection: () -> Unit,
    onRequestBulkDelete: () -> Unit,
    onScrollToLatest: () -> Unit,
    onJumpToFirstUnread: (() -> Unit)? = null,
    onMarkOverlayVisibleRead: ((List<String>) -> Unit)? = null,
    onConsumeScrollToMessage: () -> Unit,
    onClearHighlightMessage: () -> Unit,
    onConsumeTransientNotice: () -> Unit,
    onMessageListScrollInProgress: (Boolean) -> Unit = {},
    messageListKey: (ChatMessage) -> String,
    onRequestClearRoomHistory: (() -> Unit)? = null,
    clearRoomHistoryEnabled: Boolean = true,
    onRequestMarkAllRead: (() -> Unit)? = null,
    markAllReadEnabled: Boolean = true,
    markAllReadLoading: Boolean = false,
    onPinMessage: (String, ChatMessage?) -> Unit = { id, _ -> },
    onUnpinRoom: () -> Unit = {},
    onPinnedBarTap: () -> Unit = {},
    onJumpToPinnedMessage: (String) -> Unit = {},
    onUnpinOnePinned: (String) -> Unit = {},
    onDismissPinBar: () -> Unit = {},
    onRestorePinBar: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = remember(listPane.selectedRoomId) {
        LazyListState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0)
    }

    val onScrollProgressRef = rememberUpdatedState(onMessageListScrollInProgress)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { onScrollProgressRef.value(it) }
    }
    val messages = listPane.messages
    val selectedRoomId = listPane.selectedRoomId
    val selectedRoom = remember(selectedRoomId, chromePane.rooms) {
        selectedRoomId?.let { id -> chromePane.rooms.find { it.id == id } }
    }
    val pinnedPreview = chromePane.pinBarPreview
    val pinHistoryCount = chromePane.pinHistoryCount
    val pinnedMessageDeleted = remember(pinnedPreview, messages, selectedRoom) {
        pinnedPreview?.let {
            isPinnedPreviewLikelyDeleted(
                preview = it,
                messages = messages,
                serverPreview = selectedRoom?.pinnedMessage,
                pinnedMessageId = selectedRoom?.pinnedMessageId,
            )
        } == true
    }
    val pinnedMessageUnavailable = remember(pinnedPreview, messages, selectedRoom) {
        pinnedPreview?.let {
            isPinnedPreviewUnavailable(
                preview = it,
                messages = messages,
                serverPreview = selectedRoom?.pinnedMessage,
                pinnedMessageId = selectedRoom?.pinnedMessageId,
            )
        } == true
    }
    val pinnedYouLabel = stringResource(R.string.chat_pinned_meta_you)
    val pinnedMetaLine = remember(selectedRoom, pinnedPreview, pinnedYouLabel, listUiState.currentUserId) {
        val room = selectedRoom ?: return@remember null
        formatPinnedMetaLine(
            pinnedAt = room.pinnedAt,
            pinnedByUsername = pinnedPreview?.pinnedByUsername ?: room.pinnedMessage?.pinnedByUsername,
            pinnedByUserId = room.pinnedByUserId,
            currentUserId = listUiState.currentUserId,
            youLabel = pinnedYouLabel,
            userTemplate = { name -> context.getString(R.string.chat_pinned_meta_user, name) },
            formatTime = { formatForumTopicTimeRu(it) },
        )
    }
    val pinnedThumbnailUrl = remember(pinnedPreview) {
        pinnedPreview?.resolvedThumbnailUrl()
    }
    var showPinnedSheet by remember { mutableStateOf(false) }
    val pinnedSheetItems = remember(chromePane.pinnedMessages, pinnedPreview) {
        chromePane.pinnedMessages.ifEmpty {
            pinnedPreview?.let { listOf(it) } ?: emptyList()
        }
    }
    val pinBarDismissed = chromePane.isPinBarDismissed
    val compactPinCount = pinHistoryCount.coerceAtLeast(
        if (selectedRoom?.pinnedMessageId != null) 1 else 0,
    )
    val pinnedMessageIds = remember(selectedRoom?.pinnedMessageId, chromePane.pinnedMessages) {
        buildSet {
            selectedRoom?.pinnedMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            chromePane.pinnedMessages.forEach { pin ->
                pin.id.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            }
        }
    }
    val canUnpinPinned = canPinChatMessage(
        resolveChatPinAllianceId(selectedRoom, messages.firstOrNull()),
        chromePane.playerTeamSquadRole,
    )
    val inSelectionMode = listPane.selectedMessageIds.isNotEmpty()
    val timelineSize = listDerived.timeline.size

    val isNearLatest by remember(listState) {
        derivedStateOf { listState.isAtReverseChatBottom() }
    }

    var newMessagesWhileScrolledUp by remember { mutableStateOf(0) }
    var lastCountedNewestKey by remember(listPane.selectedRoomId) { mutableStateOf<String?>(null) }

    LaunchedEffect(listPane.selectedRoomId) {
        newMessagesWhileScrolledUp = 0
        lastCountedNewestKey = listPane.newestMessageKey
    }

    LaunchedEffect(isNearLatest) {
        if (isNearLatest) {
            newMessagesWhileScrolledUp = 0
            lastCountedNewestKey = listPane.newestMessageKey
        }
    }

    LaunchedEffect(listPane.newestMessageKey, isNearLatest) {
        val key = listPane.newestMessageKey ?: return@LaunchedEffect
        if (key == lastCountedNewestKey) return@LaunchedEffect
        lastCountedNewestKey = key
        val head = listPane.messages.firstOrNull()
        if (head != null && chatMessageIsOwn(head, listUiState.currentUserId)) {
            return@LaunchedEffect
        }
        if (!isNearLatest) {
            newMessagesWhileScrolledUp = (newMessagesWhileScrolledUp + 1).coerceAtMost(999)
        }
    }

    val selectedRoomUnread = remember(selectedRoomId, chromePane.rooms) {
        chromePane.rooms.find { it.id == selectedRoomId }?.unreadCount?.coerceAtLeast(0) ?: 0
    }
    val showScrollToLatestFab by remember(
        listState,
        inSelectionMode,
        listPane.messages.size,
        listPane.isLoading,
        listPane.selectedRoomId,
    ) {
        derivedStateOf {
            !listState.isAtReverseChatBottom() &&
                !inSelectionMode &&
                listPane.messages.isNotEmpty() &&
                !listPane.isLoading &&
                listPane.selectedRoomId != null
        }
    }
    val firstUnreadMessageId = remember(
        selectedRoomId,
        messages,
        chromePane.rooms,
        listUiState.currentUserId,
    ) {
        val room = chromePane.rooms.find { it.id == selectedRoomId } ?: return@remember null
        val lastRead = room.lastReadMessageId?.trim().orEmpty()
        val self = listUiState.currentUserId.trim()
        messages.lastOrNull { message ->
            val id = message._id?.trim().orEmpty()
            if (id.isEmpty() || id.startsWith("pending-")) return@lastOrNull false
            if (self.isNotBlank() && message.senderId.trim() == self) return@lastOrNull false
            lastRead.isEmpty() || com.lastasylum.alliance.data.isObjectIdNewer(id, lastRead)
        }?._id
    }
    val firstUnreadTimelineIndex = remember(firstUnreadMessageId, listDerived.timeline, messages) {
        val target = firstUnreadMessageId?.trim().orEmpty()
        if (target.isEmpty()) -1
        else chatTimelineIndexForMessageId(listDerived.timeline, messages, target)
    }
    val isFirstUnreadVisible by remember(listState, firstUnreadTimelineIndex) {
        derivedStateOf {
            if (firstUnreadTimelineIndex < 0) return@derivedStateOf true
            listState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadTimelineIndex }
        }
    }
    val showJumpToUnreadFab by remember(
        overlayUi,
        onJumpToFirstUnread,
        selectedRoomUnread,
        isNearLatest,
        isFirstUnreadVisible,
        inSelectionMode,
        listPane.messages.size,
        listPane.isLoading,
        listPane.selectedRoomId,
    ) {
        derivedStateOf {
            overlayUi &&
                onJumpToFirstUnread != null &&
                selectedRoomUnread > 0 &&
                isNearLatest &&
                !isFirstUnreadVisible &&
                !inSelectionMode &&
                listPane.messages.isNotEmpty() &&
                !listPane.isLoading &&
                listPane.selectedRoomId != null
        }
    }
    val markOverlayVisibleReadRef = rememberUpdatedState(onMarkOverlayVisibleRead)
    val timelineRef = rememberUpdatedState(listDerived.timeline)
    val messagesRef = rememberUpdatedState(messages)
    LaunchedEffect(listState, overlayUi, listPane.selectedRoomId) {
        if (!overlayUi) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .debounce(140)
            .collect { indices ->
                val markRead = markOverlayVisibleReadRef.value ?: return@collect
                val ids = indices.flatMap { index ->
                    messageIdsForTimelineIndex(timelineRef.value, messagesRef.value, index)
                }
                if (ids.isNotEmpty()) markRead(ids)
            }
    }

    var pendingScrollAnchor by remember(listPane.selectedRoomId) {
        mutableStateOf<ChatScrollAnchor?>(null)
    }

    val pendingJumpTarget = listPane.scrollToMessageId?.trim().orEmpty()

    LaunchedEffect(listPane.isLoadingOlder, pendingJumpTarget) {
        if (
            listPane.isLoadingOlder &&
            pendingScrollAnchor == null &&
            pendingJumpTarget.isEmpty()
        ) {
            pendingScrollAnchor = ChatScrollAnchor(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                timelineSize = timelineSize,
            )
        }
    }

    LaunchedEffect(listPane.isLoadingOlder, timelineSize, pendingJumpTarget) {
        if (listPane.isLoadingOlder) return@LaunchedEffect
        val anchor = pendingScrollAnchor ?: return@LaunchedEffect
        pendingScrollAnchor = null
        if (pendingJumpTarget.isNotEmpty()) return@LaunchedEffect
        val delta = timelineSize - anchor.timelineSize
        if (delta > 0) {
            listState.scrollToItem(
                anchor.firstVisibleItemIndex + delta,
                anchor.firstVisibleItemScrollOffset,
            )
        }
    }

    LaunchedEffect(listPane.scrollToMessageId, listDerived.timeline, messages.size) {
        val targetId = listPane.scrollToMessageId?.trim().orEmpty()
        if (targetId.isEmpty()) return@LaunchedEffect
        val idx = chatLazyIndexForMessageId(messagesRef.value, listDerived, targetId)
        if (idx < 0) return@LaunchedEffect
        runCatching { listState.scrollTimelineItemToViewportCenter(idx) }
            .onFailure { listState.scrollToItem(idx) }
        onConsumeScrollToMessage()
    }

    LaunchedEffect(listPane.highlightMessageId) {
        val highlightId = listPane.highlightMessageId?.trim().orEmpty()
        if (highlightId.isEmpty()) return@LaunchedEffect
        delay(700)
        onClearHighlightMessage()
    }

    LaunchedEffect(chromePane.transientNotice) {
        val notice = chromePane.transientNotice?.trim().orEmpty()
        if (notice.isEmpty()) return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        onConsumeTransientNotice()
    }

    val listStateRef = rememberUpdatedState(listState)
    val hasMoreOlderRef = rememberUpdatedState(listPane.hasMoreOlder)
    val isLoadingOlderRef = rememberUpdatedState(listPane.isLoadingOlder)
    val isLoadingRef = rememberUpdatedState(listPane.isLoading)
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
            .debounce(48)
            .collect { sig ->
                if (listStateRef.value.isScrollInProgress) return@collect
                if (sig.totalItems > 4 &&
                    sig.lastVisibleIndex >= sig.totalItems - 2 &&
                    sig.hasMoreOlder &&
                    !sig.isBusy
                ) {
                    onLoadOlderRef.value()
                }
            }
    }

    var lastHandledScrollNonce by remember(listPane.selectedRoomId) { mutableLongStateOf(0L) }
    var lastAutoScrolledNewestKey by remember(listPane.selectedRoomId) { mutableStateOf<String?>(null) }
    val listDerivedRef = rememberUpdatedState(listDerived)
    val messagesEmptyRef = rememberUpdatedState(listPane.messages.isEmpty())

    LaunchedEffect(listPane.selectedRoomId, listPane.scrollToLatestNonce) {
        val nonce = listPane.scrollToLatestNonce
        if (nonce <= lastHandledScrollNonce) return@LaunchedEffect
        if (messagesEmptyRef.value || listDerivedRef.value.timeline.isEmpty()) return@LaunchedEffect
        lastHandledScrollNonce = nonce
        lastAutoScrolledNewestKey = listPane.newestMessageKey
        newMessagesWhileScrolledUp = 0
        runCatching {
            listState.scrollReverseChatRevealLatest(
                animate = false,
                adjustViewport = false,
            )
        }
    }

    LaunchedEffect(listPane.newestMessageKey, isNearLatest) {
        if (listPane.scrollToLatestNonce > lastHandledScrollNonce) return@LaunchedEffect
        val key = listPane.newestMessageKey ?: return@LaunchedEffect
        if (listDerivedRef.value.timeline.isEmpty()) return@LaunchedEffect
        if (key == lastAutoScrolledNewestKey) return@LaunchedEffect
        val headId = listPane.messages.firstOrNull()?._id
        if (isNearLatest && headId != null && key == headId) {
            lastAutoScrolledNewestKey = key
            return@LaunchedEffect
        }
        lastAutoScrolledNewestKey = key
        if (!isNearLatest) return@LaunchedEffect
        runCatching {
            listState.scrollReverseChatRevealLatest(
                animate = false,
                adjustViewport = false,
            )
        }
    }

    val newestKeyForScroll = rememberUpdatedState(listPane.newestMessageKey)
    val scrollToLatest: () -> Unit = remember(scope, listState, onScrollToLatest) {
        {
            newMessagesWhileScrolledUp = 0
            lastCountedNewestKey = newestKeyForScroll.value
            onScrollToLatest()
            scope.launch {
                runCatching {
                    listState.scrollReverseChatRevealLatest(
                        animate = false,
                        adjustViewport = false,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(topPadding),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal),
        ) {
            if (!compactOverlayMode) {
                if (!overlayUi && onRequestMarkAllRead != null) {
                    ChatRoomActionsRow(
                        onRequestMarkRead = onRequestMarkAllRead,
                        markReadEnabled = markAllReadEnabled,
                        markReadLoading = markAllReadLoading,
                        onRequestClearHistory = onRequestClearRoomHistory,
                        clearHistoryEnabled = clearRoomHistoryEnabled,
                    )
                }
                ChatRoomsBar(
                    rooms = chromePane.rooms,
                    selectedRoomId = selectedRoomId,
                    onSelectRoom = onSelectRoom,
                    overlayUi = overlayUi,
                    isRoomsLoading = chromePane.isRoomsLoading,
                )
            }
            AnimatedVisibility(
                visible = pinnedPreview != null && selectedRoom?.pinnedMessageId != null && !pinBarDismissed,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                pinnedPreview?.let { preview ->
                    PinnedMessageBar(
                        preview = preview,
                        canUnpin = canUnpinPinned,
                        onTap = onPinnedBarTap,
                        onUnpin = { onUnpinOnePinned(preview.id) },
                        historyCount = pinHistoryCount,
                        messageDeleted = pinnedMessageDeleted,
                        messageUnavailable = pinnedMessageUnavailable,
                        thumbnailUrl = pinnedThumbnailUrl,
                        pinnedMetaLine = pinnedMetaLine,
                        onLongPress = { showPinnedSheet = true },
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            if (pinBarDismissed && compactPinCount > 0) {
                PinnedMessagesCompactChip(
                    count = compactPinCount,
                    onTap = { showPinnedSheet = true },
                    onLongPress = onRestorePinBar,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            PinnedMessagesSheet(
                visible = showPinnedSheet,
                items = pinnedSheetItems,
                canModerate = canUnpinPinned,
                activePinId = selectedRoom?.pinnedMessageId,
                onDismiss = { showPinnedSheet = false },
                onJumpTo = onJumpToPinnedMessage,
                onUnpinOne = onUnpinOnePinned,
                onUnpinAll = onUnpinRoom,
                onHideBar = {
                    onDismissPinBar()
                    showPinnedSheet = false
                },
                messageStateFor = { preview ->
                    chatPinPreviewDisplayState(
                        preview = preview,
                        messages = messages,
                        serverPreview = selectedRoom?.pinnedMessage,
                        pinnedMessageId = selectedRoom?.pinnedMessageId,
                    )
                },
            )
            if (inSelectionMode) {
                ChatSelectionToolbar(
                    selectedCount = listPane.selectedMessageIds.size,
                    isDeleting = chromePane.isDeletingSelection,
                    onClear = onClearMessageSelection,
                    onDelete = {
                        if (overlayUi) {
                            OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
                        }
                        onRequestBulkDelete()
                    },
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
            ) {
                ChatMessagesLazyList(
                    modifier = Modifier.fillMaxSize(),
                    messages = messages,
                    listDerived = listDerived,
                    listUiState = listUiState,
                    otherReadUptoMessageId = otherReadUptoMessageId,
                    listState = listState,
                    jumpToQuotedMessage = onJumpToQuotedMessage,
                    highlightMessageId = listPane.highlightMessageId,
                    onToggleReaction = onToggleReaction,
                    onOpenMessageActions = onOpenMessageActions,
                    onBeginMessageSelection = onBeginMessageSelection,
                    onReplyToMessage = onReplyToMessage,
                    onClearError = onClearError,
                    inSelectionMode = inSelectionMode,
                    selectedMessageIds = listPane.selectedMessageIds,
                    onToggleMessageSelection = onToggleMessageSelection,
                    messageListKey = messageListKey,
                    pinnedMessageIds = pinnedMessageIds,
                )
                ChatTypingIndicator(
                    typingPeers = typingPeers,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 10.dp)
                        .zIndex(3f),
                )
                if (onJumpToFirstUnread != null) {
                    com.lastasylum.alliance.overlay.OverlayReactionLogJumpToUnreadFab(
                        visible = showJumpToUnreadFab,
                        unreadCount = selectedRoomUnread,
                        onClick = onJumpToFirstUnread,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .zIndex(6f),
                    )
                }
                ChatScrollToLatestFab(
                    visible = showScrollToLatestFab,
                    newMessageCount = newMessagesWhileScrolledUp,
                    onClick = scrollToLatest,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 52.dp)
                        .zIndex(6f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatScreenComposerSection(
    composerPane: ChatComposerPaneState,
    chromePane: ChatChromePaneState,
    selectedRoomId: String?,
    globalComposerLocked: Boolean,
    draftMessage: String,
    pickedImageUris: List<Uri>,
    onAttachmentPreviewStartIndex: (Int) -> Unit,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onPickImages: (List<Uri>, Boolean) -> Unit,
    onRemovePickedImage: (Uri) -> Unit,
    onClearPickedImages: () -> Unit,
    onClearReply: () -> Unit,
    onClearEdit: () -> Unit,
    onRetrySendFailure: () -> Unit,
    onDismissSendFailure: () -> Unit,
) {
    val overlayUi = LocalOverlayUiMode.current
    val hideComposer = composerPane.sendFailure == null && when {
        selectedRoomId == null -> true
        chromePane.rooms.isNotEmpty() -> false
        overlayUi && chromePane.isRoomsLoading -> false
        else -> chromePane.rooms.isEmpty()
    }
    if (hideComposer) {
        return
    }
    val selectedRoom = remember(selectedRoomId, chromePane.rooms) {
        selectedRoomId?.let { id -> chromePane.rooms.find { it.id == id } }
    }
    val allowMediaAttachments = selectedRoom?.let {
        ChatRoomKindResolver.kindOf(it) != ChatRoomKind.Raid
    } != false
    ChatComposerBar {
        composerPane.sendFailure?.let { failure ->
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

        if (globalComposerLocked) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ) {
                Text(
                    text = stringResource(R.string.chat_global_team_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = SquadRelayDimens.contentPaddingHorizontal,
                        vertical = SquadRelayDimens.itemGap,
                    ),
                )
            }
        }

        if (selectedRoomId != null && chromePane.rooms.isNotEmpty()) {
            ChatComposer(
                draft = draftMessage,
                pickedImageUris = pickedImageUris,
                replyToMessage = composerPane.replyToMessage,
                editingMessage = composerPane.editingMessage,
                isSending = composerPane.isSending,
                sendEnabled = !globalComposerLocked,
                readOnly = false,
                allowMediaAttachments = allowMediaAttachments,
                enabledStickerPackKeys = composerPane.enabledStickerPackKeys,
                onDraftChange = onDraftChange,
                onSendDraft = {
                    if (!globalComposerLocked &&
                        (
                            !draftMessage.isBlank() ||
                                composerPane.editingMessage != null ||
                                (allowMediaAttachments && pickedImageUris.isNotEmpty())
                            ) &&
                        !composerPane.isSending
                    ) {
                        onSendDraft()
                    }
                },
                onSendStickerPayload = { payload ->
                    if (!globalComposerLocked && allowMediaAttachments && !composerPane.isSending) {
                        onSendStickerPayload(payload)
                    }
                },
                onPickImages = { uris, append ->
                    if (!globalComposerLocked && allowMediaAttachments && !composerPane.isSending) {
                        onPickImages(uris, append)
                    }
                },
                onRemovePickedImage = onRemovePickedImage,
                onClearPickedImages = onClearPickedImages,
                onClearReply = onClearReply,
                onClearEdit = onClearEdit,
                onOpenAttachmentPreview = onAttachmentPreviewStartIndex,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    listPane: ChatListPaneState,
    chromePane: ChatChromePaneState,
    composerPane: ChatComposerPaneState,
    listDerived: ChatMessagesListDerived,
    typingPeers: Map<String, String>,
    draftMessage: String,
    pickedImageUris: List<Uri>,
    otherReadUptoMessageId: String? = null,
    onSelectRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onLoadOlder: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onPickImages: (List<Uri>, append: Boolean) -> Unit,
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
    onBeginEditMessage: (String) -> Unit,
    onClearEditMessage: () -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onScrollToLatest: () -> Unit = {},
    onJumpToFirstUnread: (() -> Unit)? = null,
    onMarkOverlayVisibleRead: ((List<String>) -> Unit)? = null,
    onJumpToQuotedMessage: (String) -> Unit = {},
    onConsumeScrollToMessage: () -> Unit = {},
    onClearHighlightMessage: () -> Unit = {},
    onConsumeTransientNotice: () -> Unit = {},
    onMessageListScrollInProgress: (Boolean) -> Unit = {},
    messageListKey: (ChatMessage) -> String = { msg ->
        msg._id?.trim()?.takeIf { it.isNotEmpty() } ?: chatMessageKey(msg)
    },
    /** Overlay panel: hide room bar, rely on cached session + narrow socket subscriptions. */
    compactOverlayMode: Boolean = false,
    onClearRoomHistory: () -> Unit = {},
    onMarkAllRoomsRead: suspend () -> Unit = {},
    onPinMessage: (String, ChatMessage?) -> Unit = { id, _ -> },
    onUnpinRoom: () -> Unit = {},
    onPinnedBarTap: () -> Unit = {},
    onJumpToPinnedMessage: (String) -> Unit = {},
    onUnpinOnePinned: (String) -> Unit = {},
    onDismissPinBar: () -> Unit = {},
    onRestorePinBar: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val overlayUi = LocalOverlayUiMode.current
    var showClearRoomHistoryConfirm by remember { mutableStateOf(false) }
    var showMarkAllReadConfirm by remember { mutableStateOf(false) }
    var markAllReadBusy by remember { mutableStateOf(false) }
    val canHandleBack = LocalOnBackPressedDispatcherOwner.current != null

    val selectedRoomId = chromePane.selectedRoomId
    val selectedRoom = remember(selectedRoomId, chromePane.rooms) {
        selectedRoomId?.let { id -> chromePane.rooms.find { it.id == id } }
    }
    val showGlobalTeamNotice = selectedRoom?.allianceId == ChatAllianceIds.GLOBAL &&
        !chromePane.hasTeamProfileForGlobalChat
    val globalComposerLocked = showGlobalTeamNotice
    val dismissMessageActions: () -> Unit = {
        onDismissMessageActions()
    }
    val openMessageActionsFromBubble: (MessageActionOpenRequest) -> Unit = { request ->
        onOpenMessageActions(request.messageId)
    }
    val activeActionMessage = remember(chromePane.activeActionMessageId, listPane.messages) {
        findChatMessage(listPane.messages, chromePane.activeActionMessageId)
    }
    val confirmDeleteMessage = remember(chromePane.confirmDeleteMessageId, listPane.messages) {
        findChatMessage(listPane.messages, chromePane.confirmDeleteMessageId)
    }
    val inSelectionMode = listPane.selectedMessageIds.isNotEmpty()

    var remoteChatImagePreview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var attachmentPreviewStartIndex by remember { mutableStateOf<Int?>(null) }
    var externalGalleryGateHeld by remember { mutableStateOf(false) }
    LaunchedEffect(attachmentPreviewStartIndex, pickedImageUris.isEmpty()) {
        if (attachmentPreviewStartIndex != null && pickedImageUris.isEmpty()) {
            attachmentPreviewStartIndex = null
        }
    }

    if (canHandleBack) {
        BackHandler(enabled = inSelectionMode && !chromePane.isDeletingSelection) {
            onClearMessageSelection()
        }
    }
    if (canHandleBack) {
        BackHandler(
            enabled = remoteChatImagePreview != null || attachmentPreviewStartIndex != null,
        ) {
            when {
                remoteChatImagePreview != null -> remoteChatImagePreview = null
                attachmentPreviewStartIndex != null -> attachmentPreviewStartIndex = null
            }
        }
    }
    val listUiState = remember(
        chromePane.isRoomsLoading,
        chromePane.rooms,
        listPane.isLoading,
        listPane.isLoadingOlder,
        chromePane.error,
        chromePane.currentUserId,
        chromePane.isAppAdmin,
        chromePane.playerTeamSquadRole,
        listPane.deletingMessageId,
    ) {
        toChatListUiState(listPane, chromePane)
    }

    CompositionLocalProvider(
        LocalOpenRemoteChatImagePreview provides { urls, idx ->
            remoteChatImagePreview = urls to idx
        },
    ) {
        val messagesTopPadding = if (overlayUi) {
            Modifier.padding(top = 2.dp)
        } else {
            Modifier.padding(top = SquadRelayDimens.screenTopPadding)
        }
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChatScreenMessagesHost(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth(),
                topPadding = messagesTopPadding,
                compactOverlayMode = compactOverlayMode,
                overlayUi = overlayUi,
                listPane = listPane,
                chromePane = chromePane,
                listDerived = listDerived,
                listUiState = listUiState,
                typingPeers = typingPeers,
                otherReadUptoMessageId = otherReadUptoMessageId,
                onSelectRoom = onSelectRoom,
                onClearError = onClearError,
                onLoadOlder = onLoadOlder,
                onJumpToQuotedMessage = onJumpToQuotedMessage,
                onToggleReaction = onToggleReaction,
                onOpenMessageActions = openMessageActionsFromBubble,
                onBeginMessageSelection = onBeginMessageSelection,
                onReplyToMessage = onReplyToMessage,
                onToggleMessageSelection = onToggleMessageSelection,
                onClearMessageSelection = onClearMessageSelection,
                onRequestBulkDelete = onRequestBulkDelete,
                onScrollToLatest = onScrollToLatest,
                onJumpToFirstUnread = onJumpToFirstUnread,
                onMarkOverlayVisibleRead = onMarkOverlayVisibleRead,
                onConsumeScrollToMessage = onConsumeScrollToMessage,
                onClearHighlightMessage = onClearHighlightMessage,
                onConsumeTransientNotice = onConsumeTransientNotice,
                onMessageListScrollInProgress = onMessageListScrollInProgress,
                messageListKey = messageListKey,
                onRequestClearRoomHistory = if (!overlayUi) {
                    { showClearRoomHistoryConfirm = true }
                } else {
                    null
                },
                clearRoomHistoryEnabled = !listPane.isLoading &&
                    !chromePane.selectedRoomId.isNullOrBlank(),
                onRequestMarkAllRead = if (!overlayUi) {
                    { showMarkAllReadConfirm = true }
                } else {
                    null
                },
                markAllReadEnabled = chromePane.rooms.isNotEmpty() && !listPane.isLoading,
                markAllReadLoading = markAllReadBusy,
                onPinMessage = onPinMessage,
                onUnpinRoom = onUnpinRoom,
                onPinnedBarTap = onPinnedBarTap,
                onJumpToPinnedMessage = onJumpToPinnedMessage,
                onUnpinOnePinned = onUnpinOnePinned,
                onDismissPinBar = onDismissPinBar,
                onRestorePinBar = onRestorePinBar,
            )
            ChatScreenComposerSection(
                composerPane = composerPane,
                chromePane = chromePane,
                selectedRoomId = selectedRoomId,
                globalComposerLocked = globalComposerLocked,
                draftMessage = draftMessage,
                pickedImageUris = pickedImageUris,
                onAttachmentPreviewStartIndex = { attachmentPreviewStartIndex = it },
                onDraftChange = onDraftChange,
                onSendDraft = onSendDraft,
                onSendStickerPayload = onSendStickerPayload,
                onPickImages = onPickImages,
                onRemovePickedImage = onRemovePickedImage,
                onClearPickedImages = onClearPickedImages,
                onClearReply = onClearReply,
                onClearEdit = onClearEditMessage,
                onRetrySendFailure = onRetrySendFailure,
                onDismissSendFailure = onDismissSendFailure,
            )
        }


    if (!inSelectionMode) {
        activeActionMessage?.let { message ->
            val menuCanModerate = canDeleteChatMessage(
                message = message,
                currentUserId = chromePane.currentUserId,
                isAppAdmin = chromePane.isAppAdmin,
                playerTeamSquadRole = chromePane.playerTeamSquadRole,
            )
            val menuCanPin = canPinChatMessage(
                resolveChatPinAllianceId(selectedRoom, message),
                chromePane.playerTeamSquadRole,
            )
            val roomPinnedId = selectedRoom?.pinnedMessageId
            val pinnedMessageIds = remember(roomPinnedId, chromePane.pinnedMessages) {
                buildSet {
                    roomPinnedId?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
                    chromePane.pinnedMessages.forEach { pin ->
                        pin.id.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }
            val isRoomPinnedMessage =
                message._id != null && message._id in pinnedMessageIds
            val menuImageUrls = remember(message.attachments) {
                message.chatImageAttachments().map { resolvedChatAttachmentImageUrl(it.url) }
            }
            val menuHasImages = menuImageUrls.isNotEmpty()
            val menuHasMapCoordinate = remember(message.text) {
                com.lastasylum.alliance.game.MapCoordinateParser.parse(message.text) != null
            }
            val menuScope: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().zIndex(6f)) {
                    MessageContextMenuScrim(onDismiss = dismissMessageActions)
                    MessageContextMenuPopup(
                        showReactions = true,
                        canCopy = chatMessageHasMenuCopyAction(message),
                        canPin = menuCanPin && message.deletedAt == null,
                        isPinned = isRoomPinnedMessage,
                        pinActionsEnabled = !chromePane.pinInFlight,
                        mayEdit = message._id != null &&
                            message.deletedAt == null &&
                            menuCanModerate &&
                            message.text.isNotBlank(),
                        hasImages = menuHasImages,
                        hasMapCoordinate = menuHasMapCoordinate,
                        onDismiss = dismissMessageActions,
                        actions = MessageContextMenuActions(
                            onReply = {
                                message._id?.let(onReplyToMessage)
                                dismissMessageActions()
                            },
                            onCopy = {
                                copyChatMessageToClipboard(context, message)
                                dismissMessageActions()
                            },
                            onPin = {
                                val msgId = message._id ?: return@MessageContextMenuActions
                                onPinMessage(msgId, message)
                                dismissMessageActions()
                            },
                            onUnpin = {
                                message._id?.let(onUnpinOnePinned)
                                dismissMessageActions()
                            },
                            onEdit = {
                                message._id?.let(onBeginEditMessage)
                            },
                            onReact = { emoji ->
                                message._id?.let { id ->
                                    onToggleReaction(id, emoji)
                                    dismissMessageActions()
                                }
                            },
                            onViewImages = if (menuHasImages) {
                                {
                                    remoteChatImagePreview = menuImageUrls to 0
                                    dismissMessageActions()
                                }
                            } else {
                                null
                            },
                            onSaveToGallery = if (menuHasImages) {
                                {
                                    val urls = menuImageUrls
                                    dismissMessageActions()
                                    scope.launch {
                                        val result = saveChatImagesToGallery(context, urls)
                                        val toastRes = when {
                                            result.savedCount == 0 ->
                                                R.string.chat_gallery_save_failed_toast
                                            result.failedCount > 0 ->
                                                R.string.chat_gallery_save_partial_toast
                                            else ->
                                                R.string.chat_gallery_saved_toast
                                        }
                                        val text = when (toastRes) {
                                            R.string.chat_gallery_save_partial_toast ->
                                                context.getString(toastRes, result.savedCount, result.totalRequested)
                                            R.string.chat_gallery_saved_toast ->
                                                context.getString(toastRes, result.savedCount)
                                            else ->
                                                context.getString(toastRes)
                                        }
                                        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                null
                            },
                            onGoToMap = {
                                com.lastasylum.alliance.game.GameMapNavigator.openFromMessage(context, message.text)
                                dismissMessageActions()
                            },
                        ),
                    )
                }
            }
            if (overlayUi) {
                OverlayModalScope(preparedByCaller = true) {
                    menuScope()
                }
            } else {
                menuScope()
            }
        }
    }

    if (showClearRoomHistoryConfirm && !overlayUi) {
        AlertDialog(
            onDismissRequest = { showClearRoomHistoryConfirm = false },
            title = { Text(stringResource(R.string.chat_clear_room_confirm_title)) },
            text = {
                Text(
                    stringResource(R.string.chat_clear_room_confirm_message),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearRoomHistoryConfirm = false
                        onClearRoomHistory()
                    },
                ) {
                    Text(stringResource(R.string.chat_clear_room_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRoomHistoryConfirm = false }) {
                    Text(stringResource(R.string.chat_clear_room_cancel))
                }
            },
        )
    }

    if (showMarkAllReadConfirm && !overlayUi) {
        OverlayMarkAllReadConfirmDialog(
            title = stringResource(R.string.overlay_chat_mark_all_read_confirm_title),
            message = stringResource(R.string.overlay_chat_mark_all_read_confirm_message),
            onDismissRequest = { showMarkAllReadConfirm = false },
            onConfirm = {
                scope.launch {
                    markAllReadBusy = true
                    onMarkAllRoomsRead()
                    markAllReadBusy = false
                }
            },
        )
    }

    confirmDeleteMessage?.let {
        OverlayModalScope(preparedByCaller = true) {
        OverlayAwareAlertDialog(
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

    if (chromePane.confirmBulkDelete && listPane.selectedMessageIds.isNotEmpty()) {
        OverlayModalScope(preparedByCaller = true) {
        OverlayAwareAlertDialog(
            onDismissRequest = onDismissBulkDeleteConfirm,
            title = { Text(stringResource(R.string.chat_bulk_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.chat_bulk_delete_body,
                        listPane.selectedMessageIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmDeleteSelectedMessages,
                    enabled = !chromePane.isDeletingSelection,
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
                    enabled = !chromePane.isDeletingSelection,
                ) {
                    Text(stringResource(R.string.chat_delete_cancel))
                }
            },
        )
        }
    }

            remoteChatImagePreview?.let { (urls, start) ->
                if (urls.isNotEmpty()) {
                    MessengerImagesPreviewHost(
                        urls = urls,
                        startIndex = start,
                        onDismiss = { remoteChatImagePreview = null },
                    )
                }
            }
            attachmentPreviewStartIndex?.let { start ->
                if (pickedImageUris.isNotEmpty()) {
                    val ctx = LocalContext.current
                    AttachmentPreviewOverlay(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(8f),
                        uris = pickedImageUris,
                        startIndex = start,
                        onDismiss = {
                            if (externalGalleryGateHeld) {
                                OverlayChatInteractionHold.releaseGameForegroundSuppress()
                                externalGalleryGateHeld = false
                            }
                            attachmentPreviewStartIndex = null
                        },
                        onOpenExternal = { uri ->
                            if (overlayUi) {
                                OverlayChatInteractionHold.acquireGameForegroundSuppress()
                                externalGalleryGateHeld = true
                            }
                            if (!openPickedImageInExternalViewer(ctx, uri)) {
                                if (externalGalleryGateHeld) {
                                    OverlayChatInteractionHold.releaseGameForegroundSuppress()
                                    externalGalleryGateHeld = false
                                }
                                Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.chat_open_external_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onRemove = { uri ->
                            onRemovePickedImage(uri)
                            if (pickedImageUris.size <= 1) {
                                attachmentPreviewStartIndex = null
                            } else {
                                attachmentPreviewStartIndex =
                                    attachmentPreviewStartIndex?.coerceAtMost(pickedImageUris.lastIndex)
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: ChatState,
    listDerived: ChatMessagesListDerived,
    typingPeers: Map<String, String>,
    draftMessage: String,
    pickedImageUris: List<Uri>,
    otherReadUptoMessageId: String? = null,
    onSelectRoom: (String) -> Unit,
    onClearError: () -> Unit,
    onLoadOlder: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendDraft: () -> Unit,
    onSendStickerPayload: (String) -> Unit,
    onPickImages: (List<Uri>, append: Boolean) -> Unit,
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
    onBeginEditMessage: (String) -> Unit,
    onClearEditMessage: () -> Unit,
    onToggleReaction: (String, String) -> Unit,
    onScrollToLatest: () -> Unit = {},
    onJumpToQuotedMessage: (String) -> Unit = {},
    onConsumeScrollToMessage: () -> Unit = {},
    onClearHighlightMessage: () -> Unit = {},
    onConsumeTransientNotice: () -> Unit = {},
    onMessageListScrollInProgress: (Boolean) -> Unit = {},
    messageListKey: (ChatMessage) -> String = { msg ->
        msg._id?.trim()?.takeIf { it.isNotEmpty() } ?: chatMessageKey(msg)
    },
    compactOverlayMode: Boolean = false,
    onClearRoomHistory: () -> Unit = {},
    onMarkAllRoomsRead: suspend () -> Unit = {},
    onPinMessage: (String, ChatMessage?) -> Unit = { _, _ -> },
    onUnpinRoom: () -> Unit = {},
    onPinnedBarTap: () -> Unit = {},
    onJumpToPinnedMessage: (String) -> Unit = {},
    onUnpinOnePinned: (String) -> Unit = {},
    onDismissPinBar: () -> Unit = {},
    onRestorePinBar: () -> Unit = {},
) = ChatScreen(
    listPane = state.toListPane(),
    chromePane = state.toChromePane(),
    composerPane = state.toComposerPane(),
    listDerived = listDerived,
    typingPeers = typingPeers,
    draftMessage = draftMessage,
    pickedImageUris = pickedImageUris,
    otherReadUptoMessageId = otherReadUptoMessageId,
    onSelectRoom = onSelectRoom,
    onClearError = onClearError,
    onLoadOlder = onLoadOlder,
    onDraftChange = onDraftChange,
    onSendDraft = onSendDraft,
    onSendStickerPayload = onSendStickerPayload,
    onPickImages = onPickImages,
    onRemovePickedImage = onRemovePickedImage,
    onClearPickedImages = onClearPickedImages,
    onReplyToMessage = onReplyToMessage,
    onClearReply = onClearReply,
    onOpenMessageActions = onOpenMessageActions,
    onDismissMessageActions = onDismissMessageActions,
    onRequestDeleteMessage = onRequestDeleteMessage,
    onDismissDeleteMessage = onDismissDeleteMessage,
    onConfirmDeleteMessage = onConfirmDeleteMessage,
    onBeginMessageSelection = onBeginMessageSelection,
    onToggleMessageSelection = onToggleMessageSelection,
    onClearMessageSelection = onClearMessageSelection,
    onRequestBulkDelete = onRequestBulkDelete,
    onDismissBulkDeleteConfirm = onDismissBulkDeleteConfirm,
    onConfirmDeleteSelectedMessages = onConfirmDeleteSelectedMessages,
    onRetrySendFailure = onRetrySendFailure,
    onDismissSendFailure = onDismissSendFailure,
    onBeginEditMessage = onBeginEditMessage,
    onClearEditMessage = onClearEditMessage,
    onToggleReaction = onToggleReaction,
    onScrollToLatest = onScrollToLatest,
    onJumpToQuotedMessage = onJumpToQuotedMessage,
    onConsumeScrollToMessage = onConsumeScrollToMessage,
    onClearHighlightMessage = onClearHighlightMessage,
    onConsumeTransientNotice = onConsumeTransientNotice,
    onMessageListScrollInProgress = onMessageListScrollInProgress,
    messageListKey = messageListKey,
    compactOverlayMode = compactOverlayMode,
    onClearRoomHistory = onClearRoomHistory,
    onMarkAllRoomsRead = onMarkAllRoomsRead,
    onPinMessage = onPinMessage,
    onUnpinRoom = onUnpinRoom,
    onPinnedBarTap = onPinnedBarTap,
    onJumpToPinnedMessage = onJumpToPinnedMessage,
    onUnpinOnePinned = onUnpinOnePinned,
    onDismissPinBar = onDismissPinBar,
    onRestorePinBar = onRestorePinBar,
)

@Composable
private fun ChatRoomActionsRow(
    onRequestMarkRead: () -> Unit,
    markReadEnabled: Boolean,
    markReadLoading: Boolean,
    onRequestClearHistory: (() -> Unit)?,
    clearHistoryEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayMarkAsReadIconButton(
            onClick = onRequestMarkRead,
            enabled = markReadEnabled,
            loading = markReadLoading,
        )
        if (onRequestClearHistory != null) {
            IconButton(
                onClick = onRequestClearHistory,
                enabled = clearHistoryEnabled,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.chat_clear_room_history_cd),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ChatSelectionToolbar(
    selectedCount: Int,
    isDeleting: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = SquadRelayDimens.itemGap),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface.copy(alpha = 0.52f),
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.18f)),
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
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = scheme.surface.copy(alpha = 0.52f),
            tonalElevation = 0.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.22f)),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
private fun ChatMessagesLazyList(
    modifier: Modifier = Modifier,
    messages: List<ChatMessage>,
    listDerived: ChatMessagesListDerived,
    listUiState: ChatListUiState,
    otherReadUptoMessageId: String?,
    listState: LazyListState,
    jumpToQuotedMessage: (String) -> Unit,
    highlightMessageId: String?,
    onToggleReaction: (String, String) -> Unit,
    onOpenMessageActions: (MessageActionOpenRequest) -> Unit,
    onBeginMessageSelection: (String) -> Unit,
    onReplyToMessage: (String) -> Unit,
    onClearError: () -> Unit,
    inSelectionMode: Boolean,
    selectedMessageIds: Set<String>,
    onToggleMessageSelection: (String) -> Unit,
    messageListKey: (ChatMessage) -> String,
    pinnedMessageIds: Set<String> = emptySet(),
) {
    val overlayUi = LocalOverlayUiMode.current
    val minSystemViewport = (LocalConfiguration.current.screenHeightDp * 0.55f).dp.coerceAtLeast(280.dp)
    val timeline = listDerived.timeline
    val messageClusterFlags = listDerived.clusterFlags
    val configuration = LocalConfiguration.current
    val listBubbleMaxWidth = remember(configuration.screenWidthDp, overlayUi) {
        val rowWidth = configuration.screenWidthDp.dp
        val fraction = if (overlayUi) {
            com.lastasylum.alliance.ui.chat.ChatOverlayBubbleMaxWidthFraction
        } else {
            ChatBubbleMaxWidthFraction
        }
        val cap = if (overlayUi) {
            com.lastasylum.alliance.ui.chat.ChatOverlayBubbleMaxWidthCap
        } else {
            ChatBubbleMaxWidthCap
        }
        minOf(rowWidth * fraction, cap)
    }
    val onOpenActionsRef = rememberUpdatedState(onOpenMessageActions)
    val onBeginSelectionRef = rememberUpdatedState(onBeginMessageSelection)
    val onToggleReactionRef = rememberUpdatedState(onToggleReaction)
    val onReplyRef = rememberUpdatedState(onReplyToMessage)
    val onToggleSelectionRef = rememberUpdatedState(onToggleMessageSelection)
    val onJumpRef = rememberUpdatedState(jumpToQuotedMessage)
    val expandScrollScope = rememberCoroutineScope()
    val expandScrollCompensation = remember(listState, expandScrollScope) {
        { deltaPx: Int ->
            expandScrollScope.launch {
                listState.scrollReverseChatCompensateExpand(deltaPx)
            }
            Unit
        }
    }
    CompositionLocalProvider(
        LocalChatBubbleMaxWidth provides listBubbleMaxWidth,
        LocalChatHighlightMessageId provides highlightMessageId?.trim()?.takeIf { it.isNotEmpty() },
        LocalMessageExpandScrollCompensation provides expandScrollCompensation,
    ) {
    if (timeline.isNotEmpty()) {
        ChatListImagePrefetchEffect(listState = listState, timeline = timeline)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        reverseLayout = true,
        flingBehavior = ScrollableDefaults.flingBehavior(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(
            top = SquadRelayDimens.sectionGap,
            bottom = SquadRelayDimens.sectionGap + 6.dp,
        ),
    ) {
        when {
            listUiState.isRoomsLoading && listUiState.roomsEmpty -> {
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

            listUiState.roomsEmpty -> {
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
                                text = listUiState.error?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.chat_no_rooms),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = TextAlign.Center,
                            )
                            if (!listUiState.error.isNullOrBlank()) {
                                TextButton(onClick = onClearError) {
                                    Text(stringResource(R.string.admin_dismiss_error))
                                }
                            }
                        }
                    }
                }
            }

            listUiState.isLoading && messages.isEmpty() -> {
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

            messages.isEmpty() && !listUiState.isLoading -> {
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

            timeline.isEmpty() && messages.isNotEmpty() -> {
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

            else -> {
                items(
                    count = timeline.size,
                    key = { idx ->
                        when (val e = timeline[idx]) {
                            is ChatTimelineEntry.DaySeparator -> chatTimelineDaySeparatorKey(idx, e.label)
                            is ChatTimelineEntry.ChatMessageItem -> messageListKey(e.message)
                            is ChatTimelineEntry.ChatAlbumItem -> "album:${messageListKey(e.representativeMessage)}:${e.messageIndices.firstOrNull() ?: -1}:${e.messageIndices.lastOrNull() ?: -1}"
                        }
                    },
                    contentType = { idx ->
                        when (timeline[idx]) {
                            is ChatTimelineEntry.DaySeparator -> "chat_day"
                            is ChatTimelineEntry.ChatMessageItem -> "chat_message"
                            is ChatTimelineEntry.ChatAlbumItem -> "chat_album"
                        }
                    },
                ) { idx ->
                    when (val e = timeline[idx]) {
                        is ChatTimelineEntry.DaySeparator -> ChatDayDivider(e.label)
                        is ChatTimelineEntry.ChatMessageItem -> {
                            val message = e.message
                            val messageId = message._id
                            val cluster = messageClusterFlags.getOrNull(e.messageIndex)
                            val clusterTop = clusterTopSpacingAt(listDerived, idx).dp
                            val highlightId = LocalChatHighlightMessageId.current
                            val highlighted = messageId != null && highlightId == messageId
                            ChatMessageBubble(
                                message = message,
                                cluster = cluster,
                                isMine = chatMessageIsOwn(message, listUiState.currentUserId),
                                highlighted = highlighted,
                                showPinnedMarker = messageId != null && messageId in pinnedMessageIds,
                                clusterTopSpacing = clusterTop,
                                canDelete = canDeleteChatMessage(
                                    message = message,
                                    currentUserId = listUiState.currentUserId,
                                    isAppAdmin = listUiState.isAppAdmin,
                                    playerTeamSquadRole = listUiState.playerTeamSquadRole,
                                ),
                                deleting = listUiState.deletingMessageId == message._id,
                                inSelectionMode = inSelectionMode,
                                isSelected = message._id != null && message._id in selectedMessageIds,
                                otherReadUptoMessageId = otherReadUptoMessageId,
                                overlayUi = overlayUi,
                                inMessageList = true,
                                onToggleReaction = onToggleReactionRef.value,
                                onOpenActions = onOpenActionsRef.value,
                                onBeginSelection = onBeginSelectionRef.value,
                                onToggleSelection = onToggleSelectionRef.value,
                                onSwipeReply = onReplyRef.value,
                                onJumpToQuotedMessage = onJumpRef.value,
                            )
                        }
                        is ChatTimelineEntry.ChatAlbumItem -> {
                            val message = e.representativeMessage
                            val cluster = messageClusterFlags.getOrNull(e.firstMessageIndex)
                            val clusterTop = clusterTopSpacingAt(listDerived, idx).dp
                            val highlightId = LocalChatHighlightMessageId.current
                            val albumHighlighted = highlightId != null && (
                                highlightId == message._id || highlightId in e.memberMessageIds
                            )
                            ChatAlbumRow(
                                message = message,
                                cluster = cluster,
                                resolvedImageUrls = e.resolvedImageUrls,
                                caption = e.caption,
                                isMine = chatMessageIsOwn(message, listUiState.currentUserId),
                                highlighted = albumHighlighted,
                                clusterTopSpacing = clusterTop,
                                otherReadUptoMessageId = otherReadUptoMessageId,
                                canDelete = canDeleteChatMessage(
                                    message = message,
                                    currentUserId = listUiState.currentUserId,
                                    isAppAdmin = listUiState.isAppAdmin,
                                    playerTeamSquadRole = listUiState.playerTeamSquadRole,
                                ),
                                deleting = listUiState.deletingMessageId == message._id,
                                inSelectionMode = inSelectionMode,
                                isSelected = message._id != null && message._id in selectedMessageIds,
                                overlayUi = overlayUi,
                                inMessageList = true,
                                onToggleReaction = onToggleReactionRef.value,
                                onOpenActions = onOpenActionsRef.value,
                                onBeginSelection = onBeginSelectionRef.value,
                                onToggleSelection = onToggleSelectionRef.value,
                                onSwipeReply = onReplyRef.value,
                            )
                        }
                    }
                }
                if (listUiState.isLoadingOlder) {
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

        if (!listUiState.roomsEmpty && !listUiState.error.isNullOrBlank()) {
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
                            text = listUiState.error.orEmpty(),
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
}

private fun com.lastasylum.alliance.data.chat.ChatRoomDto.chatRoomVisualKind(): ChatRoomVisualKind =
    when (com.lastasylum.alliance.data.chat.ChatRoomKindResolver.kindOf(this)) {
        com.lastasylum.alliance.data.chat.ChatRoomKind.GlobalUnion -> ChatRoomVisualKind.GlobalUnion
        com.lastasylum.alliance.data.chat.ChatRoomKind.Server -> ChatRoomVisualKind.Server
        com.lastasylum.alliance.data.chat.ChatRoomKind.Raid -> ChatRoomVisualKind.Raid
        com.lastasylum.alliance.data.chat.ChatRoomKind.AllianceHub -> ChatRoomVisualKind.AllianceHub
        com.lastasylum.alliance.data.chat.ChatRoomKind.Other -> ChatRoomVisualKind.Other
    }

@Composable
private fun ChatRoomsBar(
    rooms: List<com.lastasylum.alliance.data.chat.ChatRoomDto>,
    selectedRoomId: String?,
    onSelectRoom: (String) -> Unit,
    overlayUi: Boolean = false,
    isRoomsLoading: Boolean = false,
) {
    if (rooms.isEmpty()) {
        if (overlayUi && isRoomsLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(bottom = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        return
    }
    val roomsKey = remember(rooms) {
        rooms.joinToString("|") { "${it.id}:${it.unreadCount}:${it.pinnedMessageId}:${it.title}" }
    }
    val tabs = remember(roomsKey) {
        rooms.map { room ->
            val kind = room.chatRoomVisualKind()
            val (accent, accentEnd) = ChatRoomTabAccents.accentFor(kind, room.allianceId)
            val icon = when (kind) {
                ChatRoomVisualKind.GlobalUnion -> Icons.Outlined.Public
                ChatRoomVisualKind.Server -> Icons.Outlined.Tag
                ChatRoomVisualKind.Raid -> Icons.Outlined.Bolt
                ChatRoomVisualKind.AllianceHub -> Icons.Outlined.Shield
                ChatRoomVisualKind.Other -> Icons.Outlined.ChatBubbleOutline
            }
            val label = when (kind) {
                ChatRoomVisualKind.Server ->
                    chatRoomTabLabelForServer(room.title, room.allianceId)
                else -> room.title
            }
            ChatRoomTabSpec(
                id = room.id,
                label = label,
                icon = icon,
                accent = accent,
                accentEnd = accentEnd,
                unreadCount = room.unreadCount.coerceAtLeast(0),
                hasPinned = !room.pinnedMessageId.isNullOrBlank(),
                iconGlyph = if (kind == ChatRoomVisualKind.Server) "#" else null,
            )
        }
    }
    ChatRoomsSwitcher(
        tabs = tabs,
        selectedId = selectedRoomId,
        onSelect = onSelectRoom,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (overlayUi) 4.dp else 6.dp),
    )
}


@Composable
private fun ChatBubbleInnerColumn(
    message: ChatMessage,
    isMine: Boolean,
    isChainBottom: Boolean,
    readByPeer: Boolean,
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
    onImageLongPress: () -> Unit,
    bubbleBg: Color,
    onFileDownload: ((ChatMessage) -> Unit)? = null,
    downloadingFileUrl: String? = null,
    inMessageList: Boolean = false,
    overlayUi: Boolean = false,
    onImageGridTap: ((Int) -> Unit)? = null,
) {
    val openRemoteChatImagePreview = LocalOpenRemoteChatImagePreview.current
    val bubblePadH = ChatMessengerStyle.bubbleHorizontalPadding(stickerStem)
    val bubblePadBottom = ChatMessengerStyle.bubbleBottomPadding(stickerStem)
    val bubblePadTop = ChatMessengerStyle.bubbleTopPadding(stickerStem, tightClusterTop)
    val messageImageTapLabel = stringResource(R.string.cd_chat_message_image)
    val timeLabel = remember(formattedTime, message.editedAt, message.createdAt) {
        if (formattedTime.isBlank()) "" else {
            val editedSuffix = if (chatMessageShowsEditedLabel(message)) {
                " · ${bubbleContext.getString(R.string.chat_edited)}"
            } else {
                ""
            }
            formattedTime + editedSuffix
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = bubblePadH)
            .padding(top = bubblePadTop, bottom = bubblePadBottom)
            .then(swipeModifier),
        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
    ) {
        message.forwardedFrom?.let { fwd ->
            val sender = chatSenderDisplayWithTag(fwd.senderTeamTag, fwd.senderUsername, fwd.senderServerNumber)
            Text(
                text = stringResource(R.string.chat_forwarded_from, sender),
                style = MaterialTheme.typography.labelMedium,
                color = onBubble.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!isMine && showClusterHeader) {
            ChatBubbleAuthorHeader(
                teamTag = stemTag,
                serverNumber = message.senderServerNumber,
                nickname = nickname,
                nicknameColor = senderAccent,
                senderRole = message.senderRole,
                isMine = isMine,
                showRoleBadge = !overlayUi,
            )
        }

        message.replyTo?.let { reply ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
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
                    Color.White.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
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
                            reply.senderServerNumber,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = onBubble.copy(alpha = 0.92f),
                    )
                    Text(
                        text = replyPreviewText(reply),
                        style = MaterialTheme.typography.bodySmall,
                        color = onBubble.copy(alpha = 0.78f),
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
                        .data(StickerPacks.assetUriForMessage(message.text))
                        .size(384)
                        .crossfade(false)
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
            val fileAtt = message.attachments.firstOrNull { !it.isChatImage() && it.url.isNotBlank() }
            val imageAttachments = message.chatImageAttachments()
            if (fileAtt != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChatFileAttachmentCard(
                        attachment = fileAtt,
                        isMine = isMine,
                        onDownload = { onFileDownload?.invoke(message) },
                        isDownloading = downloadingFileUrl != null &&
                            downloadingFileUrl == fileAtt.url.trim(),
                    )
                    if (message.hasVisibleText()) {
                        ChatMessageBodyText(
                            text = message.text,
                            onBubble = onBubble,
                            timeLabel = timeLabel,
                            isMine = isMine,
                            isChainBottom = isChainBottom,
                            messageId = message._id,
                            readByPeer = readByPeer,
                            timeMuted = timeMuted,
                            textStyle = ChatMessengerStyle.messageTextStyle(MaterialTheme.typography),
                            fadeBaseColor = bubbleBg,
                        )
                    } else if (timeLabel.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = timeMuted,
                            )
                        }
                    }
                }
            } else if (imageAttachments.isNotEmpty()) {
                val fullResolvedUrls =
                    imageAttachments.map { resolvedChatAttachmentImageUrl(it.url) }
                val scheme = MaterialTheme.colorScheme
                val captionBarBg = if (isMine) {
                    lerp(scheme.primary, Color.Black, 0.22f).copy(alpha = 0.88f)
                } else {
                    lerp(scheme.surface, Color.Black, 0.28f).copy(alpha = 0.82f)
                }
                val hasCaption = message.hasVisibleText()
                val gridFrame = BorderStroke(
                    1.dp,
                    if (isMine) Color.White.copy(alpha = 0.12f) else scheme.outline.copy(alpha = 0.2f),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = scheme.surface.copy(alpha = if (isMine) 0.14f else 0.24f),
                        tonalElevation = 0.dp,
                        shadowElevation = 3.dp,
                        border = gridFrame,
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            TelegramLikeAttachmentsGrid(
                                urls = fullResolvedUrls,
                                contentDescription = messageImageTapLabel,
                                onOpen = { idx ->
                                    if (onImageGridTap != null) {
                                        onImageGridTap(idx)
                                    } else {
                                        openRemoteChatImagePreview(fullResolvedUrls, idx)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                roundTileCorners = false,
                                bottomRound = !hasCaption,
                                onLongPress = onImageLongPress,
                                inMessageList = inMessageList,
                            )
                            if (!hasCaption && timeLabel.isNotBlank()) {
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
                                        text = timeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (hasCaption) {
                        TelegramImageCaptionBar(
                            caption = message.text.trimEnd(),
                            formattedTime = timeLabel,
                            captionBarBg = captionBarBg,
                            onBubble = onBubble,
                            timeMuted = timeMuted,
                            captionExpandKey = message._id,
                            fadeBaseColor = captionBarBg,
                        )
                    }
                }
            } else {
                ChatMessageBodyText(
                    text = message.text,
                    onBubble = onBubble,
                    timeLabel = timeLabel,
                    isMine = isMine,
                    isChainBottom = isChainBottom,
                    messageId = message._id,
                    readByPeer = readByPeer,
                    timeMuted = timeMuted,
                    textStyle = ChatMessengerStyle.messageTextStyle(MaterialTheme.typography),
                    fadeBaseColor = bubbleBg,
                )
            }
        }

        if (stickerStem != null && (timeLabel.isNotBlank() || (isMine && isChainBottom))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                ChatMessageTimeWithReadStatus(
                    time = timeLabel,
                    isMine = isMine,
                    isChainBottom = isChainBottom,
                    messageId = message._id,
                    readByPeer = readByPeer,
                    timeColor = timeMuted,
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

// moved to ui/chat/ChatMessageComponents.kt

/** Сообщение только с фото: без «пузыря», как стикер; тап — полноэкранный просмотр. */
@Composable
private fun ChatFloatingImageAttachmentsBlock(
    maxBubble: androidx.compose.ui.unit.Dp,
    urls: List<String>,
    isMine: Boolean,
    showClusterHeader: Boolean,
    stemTag: String?,
    nickname: String,
    senderAccent: Color,
    message: ChatMessage,
    isChainBottom: Boolean,
    readByPeer: Boolean,
    highlighted: Boolean = false,
    formattedTime: String,
    caption: String? = null,
    captionBarBg: Color = Color.Transparent,
    onBubble: Color = Color.White,
    timeMuted: Color = Color.White.copy(alpha = 0.6f),
    bubbleClickModifier: Modifier,
    swipeModifier: Modifier,
    deleting: Boolean,
    canDelete: Boolean,
    onImageLongPress: () -> Unit,
    inMessageList: Boolean = false,
    overlayUi: Boolean = false,
    onImageGridTap: ((Int) -> Unit)? = null,
) {
    val openRemote = LocalOpenRemoteChatImagePreview.current
    val label = stringResource(R.string.cd_chat_message_image)
    val scheme = MaterialTheme.colorScheme
    val floatMod = Modifier
        .chatBubbleWidth(
            maxBubble = maxBubble,
            expandToMax = true,
            compactMax = minOf(maxBubble, 320.dp),
        )
        .then(bubbleClickModifier)
        .then(swipeModifier)
    val clipShape = if (isMine) {
        chatBubbleShapeOutgoing(isChainBottom)
    } else {
        chatBubbleShapeIncoming(isChainBottom)
    }
    val frameBorder = BorderStroke(
        1.dp,
        when {
            highlighted -> scheme.primary.copy(alpha = 0.55f)
            isMine -> Color.White.copy(alpha = 0.12f)
            else -> scheme.outline.copy(alpha = 0.2f)
        },
    )
    val albumSurfaceColor = scheme.surface.copy(alpha = if (isMine) 0.22f else 0.38f)
    val albumBg = if (highlighted) {
        lerp(albumSurfaceColor, scheme.primary.copy(alpha = 0.32f), 0.45f)
    } else {
        albumSurfaceColor
    }
    Column(modifier = floatMod) {
        if (!isMine && showClusterHeader) {
            ChatBubbleAuthorHeader(
                teamTag = stemTag,
                serverNumber = message.senderServerNumber,
                nickname = nickname,
                nicknameColor = senderAccent,
                senderRole = message.senderRole,
                isMine = isMine,
                showRoleBadge = !overlayUi,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (!isMine && showClusterHeader) 2.dp else 0.dp),
            shape = clipShape,
            color = albumBg,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = frameBorder,
        ) {
            val hasCaption = !caption.isNullOrBlank()
            Column {
                Box(modifier = Modifier.fillMaxWidth()) {
                    TelegramLikeAttachmentsGrid(
                        urls = urls,
                        contentDescription = label,
                        onOpen = { idx ->
                            if (onImageGridTap != null) {
                                onImageGridTap(idx)
                            } else {
                                openRemote(urls, idx)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        roundTileCorners = true,
                        bottomRound = !hasCaption,
                        onLongPress = onImageLongPress,
                        inMessageList = inMessageList,
                    )
                    if (!hasCaption && (formattedTime.isNotBlank() || (isMine && isChainBottom))) {
                        ChatMessageTimeOverlayChip(
                            time = formattedTime,
                            isMine = isMine,
                            isChainBottom = isChainBottom,
                            messageId = message._id,
                            readByPeer = readByPeer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
                        )
                    }
                }
                if (hasCaption) {
                    TelegramImageCaptionBar(
                        caption = caption.orEmpty().trimEnd(),
                        formattedTime = formattedTime,
                        captionBarBg = captionBarBg,
                        onBubble = onBubble,
                        timeMuted = timeMuted,
                        captionExpandKey = message._id,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatAlbumRow(
    message: ChatMessage,
    cluster: ChatMessageClusterFlags?,
    resolvedImageUrls: List<String>,
    caption: String?,
    isMine: Boolean,
    highlighted: Boolean = false,
    otherReadUptoMessageId: String?,
    clusterTopSpacing: Dp,
    canDelete: Boolean,
    deleting: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    overlayUi: Boolean,
    inMessageList: Boolean = false,
    onToggleReaction: (String, String) -> Unit,
    onOpenActions: (MessageActionOpenRequest) -> Unit,
    onBeginSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSwipeReply: (String) -> Unit,
) {
    val messageId = message._id
    var anchorBounds by remember(messageId) { mutableStateOf(Rect.Zero) }
    val selectionHighlight = highlighted || isSelected
    val isChainBottom = cluster?.isChainBottom ?: true
    val readByPeer = remember(message._id, otherReadUptoMessageId, isMine, isChainBottom) {
        isMine && isChainBottom && isChatMessageReadByPeer(message._id, otherReadUptoMessageId)
    }
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val swipePx = remember(density) { with(density) { 56.dp.toPx() } }
    val onReactionChip: ((String) -> Unit)? = messageId?.let { mid ->
        { emoji: String -> onToggleReaction(mid, emoji) }
    }
    val senderLine = chatSenderDisplayWithTag(message.senderTeamTag, message.senderUsername, message.senderServerNumber)
    val bubbleDescription = stringResource(
        R.string.cd_chat_message,
        senderLine,
        message.senderRole,
        stringResource(R.string.cd_chat_message_image),
    )
    val telegramUrl = message.senderAvatarRelativeUrl
    val senderAccent = roleAccentColor(message.senderRole)
    val stemTag = message.senderTeamTag?.trim()?.takeIf { it.isNotEmpty() }
    val displayName = message.senderUsername.trim().ifBlank { senderLine }
    val nickname = message.senderUsername.trim().ifBlank { displayName }
    val showClusterHeader = cluster?.showHeader ?: true
    val formattedTime = remember(message.createdAt, message.editedAt) {
        formatChatTime(message.createdAt)
    }
    val scheme = MaterialTheme.colorScheme
    val onBubble = if (isMine) Color.White else scheme.onSurface
    val timeMuted = if (isMine) Color.White.copy(alpha = 0.72f) else scheme.onSurfaceVariant.copy(alpha = 0.88f)
    val captionBarBg = if (isMine) {
        lerp(scheme.primary, Color.Black, 0.22f).copy(alpha = 0.88f)
    } else {
        lerp(scheme.surface, Color.Black, 0.28f).copy(alpha = 0.82f)
    }

    val swipeModifier = if (!inMessageList && messageId != null) {
        Modifier.pointerInput(messageId, layoutDirection, swipePx) {
            var accX = 0f
            detectHorizontalDragGestures(
                onDragEnd = {
                    val fired = kotlin.math.abs(accX) > swipePx
                    if (fired) {
                        val towardReply = if (layoutDirection == LayoutDirection.Rtl) {
                            accX < 0
                        } else {
                            accX > 0
                        }
                        if (towardReply) onSwipeReply(messageId)
                    }
                    accX = 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    accX += dragAmount
                    change.consume()
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
                handleMessageTapForActions(
                    messageId = messageId,
                    anchorBounds = anchorBounds,
                    inSelectionMode = inSelectionMode,
                    canDelete = canDelete,
                    overlayUi = overlayUi,
                    onOpenActions = onOpenActions,
                    onToggleSelection = onToggleSelection,
                )
            },
            onLongClick = {
                handleMessageLongPressForSelection(
                    messageId = messageId,
                    canDelete = canDelete,
                    inSelectionMode = inSelectionMode,
                    haptics = haptics,
                    onBeginSelection = onBeginSelection,
                    onToggleSelection = onToggleSelection,
                )
            },
        )

    val imageLongPress: () -> Unit = {
        handleMessageLongPressForSelection(
            messageId = messageId,
            canDelete = canDelete,
            inSelectionMode = inSelectionMode,
            haptics = haptics,
            onBeginSelection = onBeginSelection,
            onToggleSelection = onToggleSelection,
        )
    }
    val imageGridTap: ((Int) -> Unit)? = if (inMessageList) {
        {
            handleMessageTapForActions(
                messageId = messageId,
                anchorBounds = anchorBounds,
                inSelectionMode = inSelectionMode,
                canDelete = canDelete,
                overlayUi = overlayUi,
                onOpenActions = onOpenActions,
                onToggleSelection = onToggleSelection,
            )
        }
    } else {
        null
    }

    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            anchorBounds = coordinates.boundsInRoot()
        },
    ) {
        ChatMessageBubbleRow(
            isMine = isMine,
            clusterTopSpacing = clusterTopSpacing,
            inSelectionMode = inSelectionMode,
            canDelete = canDelete,
            bubbleWidthFraction = ChatMessengerStyle.bubbleWidthFraction,
            bubbleWidthCap = ChatMessengerStyle.bubbleWidthCap,
            showIncomingAvatar = !isMine && isChainBottom,
            reserveIncomingAvatarSpace = !isMine && !isChainBottom,
            leadingAvatar = {
                ChatSenderAvatar(
                    avatarRelativeUrl = telegramUrl,
                    size = ChatIncomingAvatarSize,
                    modifier = Modifier.padding(end = ChatIncomingAvatarEndPad),
                    fallbackName = displayName,
                )
            },
            selectionControl = {
                if (inSelectionMode && canDelete) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = {
                            if (!messageId.isNullOrBlank()) onToggleSelection(messageId)
                        },
                        enabled = !messageId.isNullOrBlank(),
                    )
                }
            },
        ) { maxBubble ->
            ChatFloatingImageAttachmentsBlock(
                maxBubble = maxBubble,
                urls = resolvedImageUrls,
                isMine = isMine,
                showClusterHeader = showClusterHeader,
                stemTag = stemTag,
                nickname = nickname,
                senderAccent = senderAccent,
                message = message,
                isChainBottom = isChainBottom,
                readByPeer = readByPeer,
                highlighted = selectionHighlight,
                formattedTime = formattedTime,
                caption = caption,
                captionBarBg = captionBarBg,
                onBubble = onBubble,
                timeMuted = timeMuted,
                inMessageList = inMessageList,
                bubbleClickModifier = bubbleClickModifier,
                swipeModifier = swipeModifier,
                deleting = deleting,
                canDelete = canDelete,
                onImageLongPress = imageLongPress,
                overlayUi = overlayUi,
                onImageGridTap = imageGridTap,
            )
            ChatMessageReactionsRow(
                reactions = message.reactions,
                onReactionToggle = onReactionChip,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatMessageBubble(
    message: ChatMessage,
    cluster: ChatMessageClusterFlags?,
    isMine: Boolean,
    highlighted: Boolean = false,
    showPinnedMarker: Boolean = false,
    clusterTopSpacing: Dp,
    canDelete: Boolean,
    deleting: Boolean,
    inSelectionMode: Boolean,
    isSelected: Boolean,
    overlayUi: Boolean,
    otherReadUptoMessageId: String?,
    /** Lazy list: skip horizontal swipe detector to reduce scroll jank. */
    inMessageList: Boolean = false,
    onToggleReaction: ((String, String) -> Unit)?,
    onOpenActions: (MessageActionOpenRequest) -> Unit,
    onBeginSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSwipeReply: (String) -> Unit,
    onJumpToQuotedMessage: (String) -> Unit,
    onFileDownload: ((ChatMessage) -> Unit)? = null,
    downloadingFileUrl: String? = null,
    @androidx.annotation.StringRes deletedPlaceholderRes: Int = R.string.team_forum_message_deleted,
) {
    val messageId = message._id
    var anchorBounds by remember(messageId) { mutableStateOf(Rect.Zero) }
    val selectionHighlight = highlighted || isSelected
    val isDeleted = !message.deletedAt.isNullOrBlank() &&
        !message.deletedAt.equals("null", ignoreCase = true)
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val swipePx = remember(density) { with(density) { 56.dp.toPx() } }
    val stickerStem = remember(message.text) { StickerPacks.stemForMessage(message.text) }
    val textPreview = chatMessageSemanticsPreview(message.text)
    val senderLine = chatSenderDisplayWithTag(message.senderTeamTag, message.senderUsername, message.senderServerNumber)
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
    val telegramUrl = message.senderAvatarRelativeUrl
    val floatingSticker = stickerStem != null && message.replyTo == null
    val imageAttachments = remember(message.attachments) {
        message.chatImageAttachments()
    }
    val resolvedImageUrls = remember(imageAttachments) {
        imageAttachments.map { resolvedChatAttachmentImageUrl(it.url) }
    }
    val floatingImages = stickerStem == null &&
        resolvedImageUrls.isNotEmpty() &&
        !message.hasVisibleText() &&
        message.replyTo == null
    val senderAccent = roleAccentColor(message.senderRole)
    val scheme = MaterialTheme.colorScheme
    val highlightTint = scheme.primary.copy(alpha = 0.35f)
    val bubbleBg = ChatMessengerStyle.bubbleBackground(isMine, selectionHighlight, highlightTint)
    val onBubble = ChatMessengerStyle.bubbleContentColor(isMine)
    val timeMuted = ChatMessengerStyle.timeMutedColor(isMine)
    val bubbleBorder = BorderStroke(
        1.dp,
        ChatMessengerStyle.bubbleBorderColor(
            isMine = isMine,
            highlighted = selectionHighlight,
            highlightBorder = scheme.primary.copy(alpha = 0.55f),
        ),
    )
    val pinMarkerModifier = if (showPinnedMarker) {
        Modifier.drawBehind {
            drawRect(
                color = scheme.primary.copy(alpha = 0.85f),
                size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height),
            )
        }
    } else {
        Modifier
    }
    val stemTag = message.senderTeamTag?.trim()?.takeIf { it.isNotEmpty() }
    val displayName = message.senderUsername.trim().ifBlank { senderLine }
    val nickname = message.senderUsername.trim().ifBlank { displayName }
    val showClusterHeader = cluster?.showHeader ?: true
    val isChainBottom = cluster?.isChainBottom ?: true
    val tightClusterTop = cluster?.tightInnerTop ?: false
    val bubbleShape = if (isMine) {
        chatBubbleShapeOutgoing(isChainBottom)
    } else {
        chatBubbleShapeIncoming(isChainBottom)
    }
    val bubbleContext = LocalContext.current
    val formattedTime = remember(message.createdAt, message.editedAt) {
        formatChatTime(message.createdAt)
    }
    val readByPeer = remember(message._id, otherReadUptoMessageId, isMine, isChainBottom) {
        isMine && isChainBottom && isChatMessageReadByPeer(message._id, otherReadUptoMessageId)
    }

    val swipeModifier = if (!inMessageList && messageId != null && !isDeleted) {
        Modifier.pointerInput(messageId, layoutDirection, swipePx) {
            var accX = 0f
            detectHorizontalDragGestures(
                onDragEnd = {
                    val fired = kotlin.math.abs(accX) > swipePx
                    if (fired) {
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
                },
                onHorizontalDrag = { change, dragAmount ->
                    accX += dragAmount
                    change.consume()
                },
            )
        }
    } else {
        Modifier
    }

    val bubbleClickModifier = pinMarkerModifier
        .semantics(mergeDescendants = true) {
            contentDescription = bubbleDescription
            role = Role.Button
        }
        .combinedClickable(
            onClick = {
                handleMessageTapForActions(
                    messageId = messageId,
                    anchorBounds = anchorBounds,
                    inSelectionMode = inSelectionMode,
                    canDelete = canDelete,
                    overlayUi = overlayUi,
                    onOpenActions = onOpenActions,
                    onToggleSelection = onToggleSelection,
                )
            },
            onLongClick = {
                handleMessageLongPressForSelection(
                    messageId = messageId,
                    canDelete = canDelete,
                    inSelectionMode = inSelectionMode,
                    haptics = haptics,
                    onBeginSelection = onBeginSelection,
                    onToggleSelection = onToggleSelection,
                )
            },
        )

    val imageLongPress: () -> Unit = {
        handleMessageLongPressForSelection(
            messageId = messageId,
            canDelete = canDelete,
            inSelectionMode = inSelectionMode,
            haptics = haptics,
            onBeginSelection = onBeginSelection,
            onToggleSelection = onToggleSelection,
        )
    }
    val imageGridTap: ((Int) -> Unit)? = if (inMessageList) {
        {
            handleMessageTapForActions(
                messageId = messageId,
                anchorBounds = anchorBounds,
                inSelectionMode = inSelectionMode,
                canDelete = canDelete,
                overlayUi = overlayUi,
                onOpenActions = onOpenActions,
                onToggleSelection = onToggleSelection,
            )
        }
    } else {
        null
    }

    val expandBubble = when {
        overlayUi && stickerStem == null && !floatingSticker && !floatingImages -> true
        else -> chatBubbleExpandsToRowWidth(
            floatingSticker = floatingSticker,
            floatingImages = floatingImages,
            stickerStem = stickerStem,
        )
    }
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            anchorBounds = coordinates.boundsInRoot()
        },
    ) {
        ChatMessageBubbleRow(
            isMine = isMine,
            clusterTopSpacing = clusterTopSpacing,
            inSelectionMode = inSelectionMode,
            canDelete = canDelete,
            bubbleWidthFraction = ChatMessengerStyle.bubbleWidthFraction,
            bubbleWidthCap = ChatMessengerStyle.bubbleWidthCap,
            showIncomingAvatar = !isMine && isChainBottom,
            reserveIncomingAvatarSpace = !isMine && !isChainBottom,
            leadingAvatar = {
                if (overlayUi && !isMine) {
                    ChatSenderAvatarWithSquadRank(
                    avatarRelativeUrl = telegramUrl,
                    squadRole = message.senderRole,
                    size = ChatIncomingAvatarSize,
                    modifier = Modifier.padding(end = ChatIncomingAvatarEndPad),
                    fallbackName = displayName,
                )
            } else {
                ChatSenderAvatar(
                    avatarRelativeUrl = telegramUrl,
                    size = ChatIncomingAvatarSize,
                    modifier = Modifier.padding(end = ChatIncomingAvatarEndPad),
                    fallbackName = displayName,
                )
            }
        },
        selectionControl = {
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
        },
    ) { maxBubble ->
        val floatStickerMax = minOf(maxBubble, 240.dp)
        val stickerInBubbleMax = minOf(maxBubble, 280.dp)
        val onReactionChip: ((String) -> Unit)? = messageId?.let { mid ->
            onToggleReaction?.let { toggle -> { emoji: String -> toggle(mid, emoji) } }
        }
        when {
            isDeleted -> {
                Surface(
                    modifier = Modifier
                        .chatBubbleWidth(maxBubble = maxBubble, expandToMax = true)
                        .then(bubbleClickModifier),
                    color = bubbleBg,
                    shape = bubbleShape,
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.28f)),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(deletedPlaceholderRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = timeMuted,
                        )
                        val timeStr = formatChatTime(message.createdAt)
                        if (timeStr.isNotBlank()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    text = timeStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = timeMuted,
                                )
                            }
                        }
                    }
                }
            }

            floatingSticker -> {
                val floatMod = Modifier
                    .chatBubbleWidth(
                        maxBubble = maxBubble,
                        expandToMax = false,
                        compactMax = floatStickerMax,
                    )
                    .then(bubbleClickModifier)
                    .then(swipeModifier)
                Column(modifier = floatMod) {
                    if (!isMine && showClusterHeader) {
                        ChatBubbleAuthorHeader(
                            teamTag = stemTag,
                            serverNumber = message.senderServerNumber,
                            nickname = nickname,
                            nicknameColor = senderAccent,
                            senderRole = message.senderRole,
                            isMine = isMine,
                            showRoleBadge = !overlayUi,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (!isMine && showClusterHeader) 2.dp else 0.dp),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(bubbleContext)
                                .data(StickerPacks.assetUriForMessage(message.text))
                                .size(384)
                                .crossfade(false)
                                .build(),
                            contentDescription = stringResource(R.string.cd_chat_sticker),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(
                                    if (isMine) {
                                        chatBubbleShapeOutgoing(isChainBottom)
                                    } else {
                                        chatBubbleShapeIncoming(isChainBottom)
                                    },
                                ),
                            contentScale = ContentScale.Fit,
                        )
                        if (formattedTime.isNotBlank() || (isMine && isChainBottom)) {
                            ChatMessageTimeOverlayChip(
                                time = formattedTime,
                                isMine = isMine,
                                isChainBottom = isChainBottom,
                                messageId = message._id,
                                readByPeer = readByPeer,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp),
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
                    if (onReactionChip != null) {
                        ChatMessageReactionsRow(
                            reactions = message.reactions,
                            onReactionToggle = onReactionChip,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            floatingImages -> {
                ChatFloatingImageAttachmentsBlock(
                    maxBubble = maxBubble,
                    urls = resolvedImageUrls,
                    isMine = isMine,
                    showClusterHeader = showClusterHeader,
                    stemTag = stemTag,
                    nickname = nickname,
                    senderAccent = senderAccent,
                    message = message,
                    isChainBottom = isChainBottom,
                    readByPeer = readByPeer,
                    highlighted = selectionHighlight,
                    formattedTime = formattedTime,
                    inMessageList = inMessageList,
                    bubbleClickModifier = bubbleClickModifier,
                    swipeModifier = swipeModifier,
                    deleting = deleting,
                    canDelete = canDelete,
                    onImageLongPress = imageLongPress,
                    overlayUi = overlayUi,
                    onImageGridTap = imageGridTap,
                )
                if (onReactionChip != null) {
                    ChatMessageReactionsRow(
                        reactions = message.reactions,
                        onReactionToggle = onReactionChip,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier.chatBubbleWidth(
                        maxBubble = maxBubble,
                        expandToMax = expandBubble,
                        compactMax = if (stickerStem != null) {
                            stickerInBubbleMax
                        } else {
                            maxBubble
                        },
                    ),
                ) {
                    Surface(
                        modifier = Modifier
                            .chatBubbleSurfaceWidth(expandToMax = expandBubble)
                            .then(bubbleClickModifier)
                            .then(swipeModifier),
                        shape = bubbleShape,
                        color = bubbleBg,
                        tonalElevation = 0.dp,
                        shadowElevation = if (isMine) {
                            ChatMessengerStyle.bubbleElevationMine
                        } else {
                            ChatMessengerStyle.bubbleElevationOther
                        },
                        border = bubbleBorder,
                    ) {
                        ChatBubbleInnerColumn(
                            message = message,
                            isMine = isMine,
                            isChainBottom = isChainBottom,
                            readByPeer = readByPeer,
                            showClusterHeader = showClusterHeader,
                            tightClusterTop = tightClusterTop,
                            stickerStem = stickerStem,
                            senderAccent = senderAccent,
                            stemTag = stemTag,
                            nickname = nickname,
                            onBubble = onBubble,
                            timeMuted = timeMuted,
                            formattedTime = formattedTime,
                            swipeModifier = Modifier,
                            bubbleContext = bubbleContext,
                            replyQuoteInteraction = replyQuoteInteraction,
                            quotedJumpLabel = quotedJumpLabel,
                            onJumpToQuotedMessage = onJumpToQuotedMessage,
                            deleting = deleting,
                            canDelete = canDelete,
                            onImageLongPress = imageLongPress,
                            bubbleBg = bubbleBg,
                            onFileDownload = onFileDownload,
                            downloadingFileUrl = downloadingFileUrl,
                            inMessageList = inMessageList,
                            overlayUi = overlayUi,
                            onImageGridTap = imageGridTap,
                        )
                    }
                    if (onReactionChip != null) {
                        ChatMessageReactionsRow(
                            reactions = message.reactions,
                            onReactionToggle = onReactionChip,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
    }
}
