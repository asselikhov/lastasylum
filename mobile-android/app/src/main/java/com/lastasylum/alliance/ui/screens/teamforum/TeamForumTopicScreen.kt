package com.lastasylum.alliance.ui.screens.teamforum

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.auth.TokenStore
import com.lastasylum.alliance.data.chat.ChatReaction
import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.data.teams.ForumMessageStash
import com.lastasylum.alliance.data.teams.TeamForumMarkRead
import com.lastasylum.alliance.data.teams.TeamForumMessageDeletedEvent
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumMessageReactionEvent
import com.lastasylum.alliance.data.teams.TeamForumSocketManager
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent
import com.lastasylum.alliance.data.teams.TeamForumTopicReadEvent
import com.lastasylum.alliance.data.teams.TeamForumTypingEvent
import com.lastasylum.alliance.data.teams.forum.ForumRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayAwareAlertDialog
import com.lastasylum.alliance.overlay.OverlayChatInteractionHold
import com.lastasylum.alliance.overlay.OverlayModalScope
import com.lastasylum.alliance.overlay.OverlayReactionLogJumpToUnreadFab
import com.lastasylum.alliance.ui.chat.ACTIVE_FORUM_RECONCILE_INTERVAL_MS
import com.lastasylum.alliance.ui.chat.AttachmentPreviewOverlay
import com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthCap
import com.lastasylum.alliance.ui.chat.ChatBubbleMaxWidthFraction
import com.lastasylum.alliance.ui.chat.ChatComposer
import com.lastasylum.alliance.ui.chat.ChatComposerBar
import com.lastasylum.alliance.ui.chat.ChatScrollToLatestFab
import com.lastasylum.alliance.ui.chat.ForumPinCoordinator
import com.lastasylum.alliance.ui.chat.ForumTimelineEntry
import com.lastasylum.alliance.ui.chat.LocalChatBubbleMaxWidth
import com.lastasylum.alliance.ui.chat.LocalChatHighlightMessageId
import com.lastasylum.alliance.ui.chat.LocalMessageExpandScrollCompensation
import com.lastasylum.alliance.ui.chat.LocalOpenRemoteChatImagePreview
import com.lastasylum.alliance.ui.chat.MessageActionOpenRequest
import com.lastasylum.alliance.ui.chat.MessageContextMenuActions
import com.lastasylum.alliance.ui.chat.MessageContextMenuPopup
import com.lastasylum.alliance.ui.chat.MessageContextMenuScrim
import com.lastasylum.alliance.ui.chat.MessengerImagesPreviewHost
import com.lastasylum.alliance.ui.chat.PinnedMessageBar
import com.lastasylum.alliance.ui.chat.PinnedMessagesCompactChip
import com.lastasylum.alliance.ui.chat.PinnedMessagesSheet
import com.lastasylum.alliance.ui.chat.TopicPinSnapshot
import com.lastasylum.alliance.ui.chat.buildOptimisticForumMessage
import com.lastasylum.alliance.ui.chat.capForumMessagesOldestFirst
import com.lastasylum.alliance.ui.chat.capForumMessagesTrimNewestOnly
import com.lastasylum.alliance.ui.chat.forumLazyIndexForMessageId
import com.lastasylum.alliance.ui.chat.forumPinPreviewDisplayState
import com.lastasylum.alliance.ui.chat.formatPinnedMetaLine
import com.lastasylum.alliance.ui.chat.isAtReverseChatBottom
import com.lastasylum.alliance.ui.chat.isForumPendingId
import com.lastasylum.alliance.ui.chat.isForumPinnedPreviewLikelyDeleted
import com.lastasylum.alliance.ui.chat.isForumPinnedPreviewUnavailable
import com.lastasylum.alliance.ui.chat.jumpToForumPinnedMessage
import com.lastasylum.alliance.ui.chat.mergeForumMessagesPage
import com.lastasylum.alliance.ui.chat.mergePreservingForumMedia
import com.lastasylum.alliance.ui.chat.queryDisplayName
import com.lastasylum.alliance.ui.chat.removePendingForumOutgoing
import com.lastasylum.alliance.ui.chat.replaceMatchingPendingForumOutgoing
import com.lastasylum.alliance.ui.chat.resolvedChatAttachmentImageUrl
import com.lastasylum.alliance.ui.chat.resolvedThumbnailUrl
import com.lastasylum.alliance.ui.chat.saveChatImagesToGallery
import com.lastasylum.alliance.ui.chat.scrollReverseChatCompensateExpand
import com.lastasylum.alliance.ui.chat.scrollReverseChatRevealLatest
import com.lastasylum.alliance.ui.chat.scrollTimelineItemToViewportCenter
import com.lastasylum.alliance.ui.chat.shouldBlockOwnForumOutgoingRealtime
import com.lastasylum.alliance.ui.chat.FORUM_GAP_RECONCILE_THRESHOLD_MS
import com.lastasylum.alliance.ui.chat.shouldTriggerGapReconcile
import com.lastasylum.alliance.ui.chat.stabilizeComposerImageUris
import com.lastasylum.alliance.ui.chat.toDisplayChatMessage
import com.lastasylum.alliance.ui.components.CenteredScreenLoading
import com.lastasylum.alliance.ui.components.premium.PremiumGlassBar
import com.lastasylum.alliance.ui.components.team.ForumTopicCardTokens
import com.lastasylum.alliance.ui.teamforum.ForumListViewModel
import com.lastasylum.alliance.ui.teamforum.ForumTopicViewModel
import com.lastasylum.alliance.data.teams.forum.ForumOutboxEntry
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.SquadRelaySurfaces
import com.lastasylum.alliance.ui.util.copyForumMessageToClipboard
import com.lastasylum.alliance.ui.util.forumMessageHasMenuCopyAction
import com.lastasylum.alliance.ui.util.formatForumTopicTimeRu
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.util.Locale
import java.util.UUID

private fun forumMarkReadTopicKey(topicId: String) = "topic/${topicId.trim()}"

private data class ForumScrollAnchor(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val timelineSize: Int,
)

private data class ForumLoadOlderSignal(
    val lastVisibleIndex: Int,
    val totalItems: Int,
)

@Composable
private fun ForumTopicOverlayBackChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.team_detail_back_cd)
    PremiumGlassBar(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun TeamForumTopicScreen(
    teamId: String,
    topicId: String,
    @Suppress("UNUSED_PARAMETER") topicTitle: String,
    topicSnapshot: TeamForumTopicDto?,
    currentUserId: String,
    canModerateMessages: Boolean,
    forumRepository: ForumRepository,
    forumSocket: TeamForumSocketManager,
    tokenStore: TokenStore,
    sectionActive: Boolean = true,
    onBack: () -> Unit,
    enabledStickerPackKeys: Set<String> = emptySet(),
    onProvideMarkReadAction: (String, (() -> Unit)?) -> Unit = { _, _ -> },
    onTopicSnapshotUpdate: (TeamForumTopicDto) -> Unit = {},
    onInboxChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val res = context.resources
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()

    val listViewModel: ForumListViewModel = viewModel(key = "forum_list_$teamId") {
        ForumListViewModel.create(context.applicationContext as Application, teamId)
    }
    val topicViewModel: ForumTopicViewModel = viewModel(key = "forum_topic_${teamId}_$topicId") {
        ForumTopicViewModel(context.applicationContext as Application)
    }

    DisposableEffect(teamId, topicId) {
        listViewModel.setOpenTopicId(topicId)
        onDispose { listViewModel.setOpenTopicId(null) }
    }

    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf<TeamForumMessageDto>() }
    var loading by remember { mutableStateOf(true) }
    var hasMoreOlder by remember { mutableStateOf(false) }
    var loadingOlder by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var pickedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var attachmentPreviewStartIndex by remember { mutableStateOf<Int?>(null) }
    var pendingApkFileId by remember { mutableStateOf<String?>(null) }
    var pendingApkLabel by remember { mutableStateOf<String?>(null) }
    var uploadingImage by remember { mutableStateOf(false) }
    var uploadingFile by remember { mutableStateOf(false) }
    var downloadingForumFileUrl by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val forumSendMutex = remember { Mutex() }
    var typingHint by remember { mutableStateOf<String?>(null) }
    var knownForumMessageIds by remember(teamId, topicId) { mutableStateOf(setOf<String>()) }
    var inFlightForumClientMessageIds by remember { mutableStateOf(setOf<String>()) }
    var lastForumBackgroundSyncAtMs by remember(teamId, topicId) { mutableLongStateOf(0L) }
    var forceForumRefreshAfterReconnect by remember(teamId, topicId) { mutableStateOf(false) }
    // messages is maintained oldest-first; avoid per-update sorting allocations (jank/GC).
    val stableMessages = messages
    val selfForumUsername by remember(stableMessages, currentUserId) {
        derivedStateOf {
            val self = currentUserId.trim()
            stableMessages.lastOrNull { it.senderUserId.trim() == self }?.senderUsername.orEmpty()
        }
    }
    var messagesGeneration by remember(teamId, topicId) { mutableIntStateOf(0) }
    fun bumpMessagesGeneration() {
        messagesGeneration++
    }
    val listDerived = rememberForumMessagesListDerived(stableMessages, messagesGeneration, listState)
    var newMessagesWhileScrolledUp by remember(teamId, topicId) { mutableIntStateOf(0) }
    var lastCountedNewestId by remember(teamId, topicId) { mutableStateOf<String?>(null) }
    val isNearBottom by remember(listState) {
        derivedStateOf { listState.isAtReverseChatBottom() }
    }
    val showScrollToLatestFab by remember(listState, listDerived, hasMoreOlder, stableMessages.size, loading) {
        derivedStateOf {
            !isNearBottom &&
                stableMessages.isNotEmpty() &&
                !loading
        }
    }

    fun trimForumMessagesInMemory() {
        capForumMessagesOldestFirst(messages)
        bumpMessagesGeneration()
    }

    var pendingForumScrollAnchor by remember(teamId, topicId) {
        mutableStateOf<ForumScrollAnchor?>(null)
    }
    val timelineSize = listDerived.timeline.size
    val app = remember { AppContainer.from(context.applicationContext) }
    val forumPrefs = remember { app.teamForumPreferences }
    var lastReadCursor by remember { mutableStateOf<String?>(null) }
    var otherReadUptoMessageId by remember { mutableStateOf<String?>(null) }
    val otherReadUptoByTopic = remember { mutableMapOf<String, String>() }

    var editingForumMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var replyToMessage by remember { mutableStateOf<TeamForumMessageDto?>(null) }
    var activeActionMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageIds by remember { mutableStateOf(setOf<String>()) }
    val dismissMessageActions: () -> Unit = {
        activeActionMessageId = null
    }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var deletingSelection by remember { mutableStateOf(false) }
    var highlightMessageId by remember { mutableStateOf<String?>(null) }
    val pinHistoryPrefs = remember { AppContainer.from(context).pinHistoryPreferences }
    LaunchedEffect(currentUserId) {
        pinHistoryPrefs.bindUser(currentUserId)
    }
    val pinScopeKey = remember(teamId, topicId, currentUserId) {
        pinHistoryPrefs.forumScopeKey(teamId, topicId)
    }
    val pinCoordinator = remember(pinScopeKey) {
        ForumPinCoordinator(pinHistoryPrefs, pinScopeKey)
    }
    var pinRevision by remember(teamId, topicId) { mutableIntStateOf(0) }
    fun bumpPinUi() {
        pinRevision++
    }
    var pinNotice by remember { mutableStateOf<String?>(null) }
    var showForumPinnedSheet by remember { mutableStateOf(false) }
    var remoteImagePreview by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    val openImages = remember {
        { urls: List<String>, idx: Int -> remoteImagePreview = urls to idx }
    }

    BackHandler(enabled = selectedMessageIds.isNotEmpty() && !deletingSelection) {
        selectedMessageIds = emptySet()
        dismissMessageActions()
    }

    fun clearPendingAttachment() {
        pickedImageUris = emptyList()
        attachmentPreviewStartIndex = null
        pendingApkFileId = null
        pendingApkLabel = null
    }

    fun publishCoordinatorPinSnapshot() {
        val base = topicSnapshot ?: return
        onTopicSnapshotUpdate(
            base.copy(
                pinnedMessageId = pinCoordinator.pinnedMessageId,
                pinnedAt = pinCoordinator.pinnedAt,
                pinnedByUserId = pinCoordinator.pinnedByUserId,
                pinnedMessage = pinCoordinator.pinnedMessage,
                pinnedMessages = pinCoordinator.pinnedMessages,
            ),
        )
    }

    fun applyTopicPin(event: TeamForumTopicPinChangedEvent) {
        if (event.teamId != teamId || event.topicId != topicId) return
        pinCoordinator.applyTopicPin(event, stableMessages)
        publishCoordinatorPinSnapshot()
        bumpPinUi()
    }

    fun refreshTopicPinFromServer() {
        if (pinCoordinator.pinInFlight) return
        scope.launch {
            forumRepository.syncTopics(currentUserId, teamId, bypassCache = true)
                .onSuccess { topics ->
                    val topic = topics.find { it.id == topicId } ?: return@onSuccess
                    pinCoordinator.applyTopicFromServer(topic, stableMessages)
                    onTopicSnapshotUpdate(topic)
                    bumpPinUi()
                }
        }
    }

    LaunchedEffect(teamId, topicId) {
        pinCoordinator.onEnterTopic(topicSnapshot)
        topicSnapshot?.let { snapshot ->
            pinCoordinator.applyTopicFromServer(snapshot, stableMessages)
        }
        bumpPinUi()
    }

    LaunchedEffect(showForumPinnedSheet) {
        if (showForumPinnedSheet) {
            refreshTopicPinFromServer()
        }
    }

    LaunchedEffect(stableMessages.size, pinCoordinator.pinnedMessageId) {
        if (pinCoordinator.pinnedMessageId != null) {
            pinCoordinator.applyPinBarUi(stableMessages)
            bumpPinUi()
        }
    }

    fun pinForumMessage(messageId: String, previewSource: TeamForumMessageDto? = null) {
        val trimmedId = messageId.trim()
        if (trimmedId.isEmpty() || pinCoordinator.pinInFlight) return
        val snapshot = TopicPinSnapshot(
            pinnedMessageId = pinCoordinator.pinnedMessageId,
            pinnedAt = pinCoordinator.pinnedAt,
            pinnedByUserId = pinCoordinator.pinnedByUserId,
            pinnedMessage = pinCoordinator.pinnedMessage,
        )
        pinCoordinator.pinInFlight = true
        pinCoordinator.prepareOptimisticPin(trimmedId, previewSource, stableMessages, currentUserId)
        bumpPinUi()
        scope.launch {
            forumRepository.pinForumTopicMessage(teamId, topicId, trimmedId)
                .onSuccess { topic ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.onPinSuccess(topic, stableMessages)
                    onTopicSnapshotUpdate(topic)
                    bumpPinUi()
                    pinNotice = res.getString(R.string.forum_pinned_toast_pinned)
                }
                .onFailure { e ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.rollbackTo(snapshot, stableMessages)
                    bumpPinUi()
                    pinNotice = e.toUserMessageRu(res)
                }
        }
    }

    fun unpinOneForumMessage(messageId: String) {
        if (pinCoordinator.pinInFlight) return
        val trimmedId = messageId.trim()
        if (trimmedId.isEmpty()) return
        val snapshot = TopicPinSnapshot(
            pinnedMessageId = pinCoordinator.pinnedMessageId,
            pinnedAt = pinCoordinator.pinnedAt,
            pinnedByUserId = pinCoordinator.pinnedByUserId,
            pinnedMessage = pinCoordinator.pinnedMessage,
        )
        pinCoordinator.pinInFlight = true
        pinCoordinator.prepareOptimisticUnpinOne(trimmedId, stableMessages)
        bumpPinUi()
        scope.launch {
            forumRepository.unpinOneForumTopicMessage(teamId, topicId, trimmedId)
                .onSuccess { topic ->
                    pinCoordinator.pinInFlight = false
                    if (topic.pinnedMessageId.isNullOrBlank()) {
                        pinCoordinator.onUnpinSuccess(topic, stableMessages)
                    } else {
                        pinCoordinator.onPinSuccess(topic, stableMessages)
                    }
                    onTopicSnapshotUpdate(topic)
                    bumpPinUi()
                    pinNotice = res.getString(R.string.forum_pinned_toast_unpinned)
                }
                .onFailure { e ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.rollbackTo(snapshot, stableMessages)
                    bumpPinUi()
                    pinNotice = e.toUserMessageRu(res)
                }
        }
    }

    fun unpinForumTopic() {
        if (pinCoordinator.pinInFlight) return
        val snapshot = TopicPinSnapshot(
            pinnedMessageId = pinCoordinator.pinnedMessageId,
            pinnedAt = pinCoordinator.pinnedAt,
            pinnedByUserId = pinCoordinator.pinnedByUserId,
            pinnedMessage = pinCoordinator.pinnedMessage,
        )
        pinCoordinator.pinInFlight = true
        pinCoordinator.prepareOptimisticUnpin(stableMessages)
        bumpPinUi()
        scope.launch {
            forumRepository.pinForumTopicMessage(teamId, topicId, null)
                .onSuccess { topic ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.onUnpinSuccess(topic, stableMessages)
                    onTopicSnapshotUpdate(topic)
                    bumpPinUi()
                    pinNotice = res.getString(R.string.forum_pinned_toast_unpinned)
                }
                .onFailure { e ->
                    pinCoordinator.pinInFlight = false
                    pinCoordinator.rollbackTo(snapshot, stableMessages)
                    bumpPinUi()
                    pinNotice = e.toUserMessageRu(res)
                }
        }
    }

    fun persistForumMessagesToDisk() {
        if (currentUserId.isBlank()) return
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            app.launchDiskCache.saveForumMessages(
                currentUserId,
                teamId,
                topicId,
                messages.toList(),
                hasMoreOlder,
            )
        }
    }

    fun applyEdited(msg: TeamForumMessageDto) {
        forumRepository.invalidateForumMessagesCache(teamId, topicId)
        val i = messages.indexOfFirst { it.id == msg.id }
        if (i >= 0) {
            messages[i] = messages[i].mergePreservingForumMedia(msg)
        } else {
            messages.add(msg)
            trimForumMessagesInMemory()
        }
        persistForumMessagesToDisk()
        pinCoordinator.refreshPinAfterMessageEdit(msg.id, stableMessages)
        bumpPinUi()
    }

    fun applyForumMessageReactions(messageId: String, reactions: List<ChatReaction>) {
        val i = messages.indexOfFirst { it.id == messageId }
        if (i < 0) return
        val current = messages[i]
        if (current.reactions == reactions) return
        messages[i] = current.copy(reactions = reactions)
    }

    fun toggleForumReaction(messageId: String, emoji: String) {
        if (messageId.isBlank() || emoji.isBlank()) return
        val i = messages.indexOfFirst { it.id == messageId }
        if (i < 0) return
        val previous = messages[i]
        val optimistic = applyOptimisticForumReactionToggle(previous, emoji)
        if (optimistic === previous) return
        messages[i] = optimistic
        scope.launch {
            forumRepository.toggleForumMessageReaction(teamId, topicId, messageId, emoji)
                .onSuccess { updated -> applyForumMessageReactions(updated.id, updated.reactions) }
                .onFailure { e ->
                    val rollbackIndex = messages.indexOfFirst { it.id == messageId }
                    if (rollbackIndex >= 0) {
                        messages[rollbackIndex] = previous
                    }
                    error = e.toUserMessageRu(res)
                }
        }
    }

    fun removeMessage(messageId: String) {
        forumRepository.invalidateForumMessagesCache(teamId, topicId)
        messages.removeAll { it.id == messageId }
        bumpMessagesGeneration()
        persistForumMessagesToDisk()
        if (activeActionMessageId == messageId) {
            activeActionMessageId = null
        }
        selectedMessageIds = selectedMessageIds - messageId
        if (replyToMessage?.id == messageId) replyToMessage = null
        if (editingForumMessage?.id == messageId) editingForumMessage = null
    }

    fun applyDeleted(ev: TeamForumMessageDeletedEvent) {
        removeMessage(ev.messageId)
        pinCoordinator.clearPinIfMessageDeleted(ev.messageId, stableMessages)
        bumpPinUi()
    }

    fun mergeReadCursor(messageId: String) {
        if (messageId.isBlank()) return
        val prev = lastReadCursor
        if (prev != null && !isObjectIdNewer(messageId, prev)) return
        lastReadCursor = messageId
        forumPrefs.setLastReadMessageId(teamId, topicId, messageId)
    }

    val forumMarkReadCoalescer = remember { topicViewModel.markReadCoalescer }

    fun notifyForumInboxAfterRead(messageId: String) {
        listViewModel.applyTopicReadLocal(topicId, messageId)
        topicSnapshot?.let { snap ->
            onTopicSnapshotUpdate(snap.copy(unreadCount = 0, lastReadMessageId = messageId))
        }
        scope.launch {
            TeamForumMarkRead.afterTopicMarkedRead(
                forumRepository = forumRepository,
                userId = currentUserId,
                forumPrefs = forumPrefs,
                teamId = teamId,
                topicId = topicId,
                messageId = messageId,
                topicFallback = topicSnapshot,
                onInboxChanged = onInboxChanged,
                inboxBadgeCoordinator = app.inboxBadgeCoordinator,
            )
        }
    }

    fun scheduleMarkForumTopicRead(messageId: String) {
        forumMarkReadCoalescer.schedule(
            topicId = topicId,
            messageId = messageId,
            getCurrentCursor = { lastReadCursor },
            onOptimisticAdvance = { _, mid -> mergeReadCursor(mid) },
            onNetworkMarkRead = { _, mid ->
                forumRepository.markForumTopicRead(teamId, topicId, mid)
                    .onSuccess { notifyForumInboxAfterRead(mid) }
            },
        )
    }

    fun flushPendingMarkForumTopicRead() {
        scope.launch {
            forumMarkReadCoalescer.flushAndAwait(topicId)
        }
    }

    fun leaveTopic() {
        flushPendingMarkForumTopicRead()
        onBack()
    }

    BackHandler { leaveTopic() }

    fun markTopicReadToLatest(forceSync: Boolean = false) {
        val newestId = stableMessages.lastOrNull()?.id ?: return
        val prev = lastReadCursor
        if (!forceSync && prev != null && !isObjectIdNewer(newestId, prev)) return
        scope.launch {
            forumRepository.markForumTopicRead(teamId, topicId, newestId)
                .onSuccess {
                    mergeReadCursor(newestId)
                    notifyForumInboxAfterRead(newestId)
                }
        }
    }

    fun markVisibleForumMessages(lazyIndices: List<Int>) {
        if (!overlayUi) return
        val self = currentUserId.trim()
        val lastRead = lastReadCursor?.trim().orEmpty()
        var watermark: String? = null
        for (lazyIdx in lazyIndices) {
            val timelineIndex = listDerived.timeline.lastIndex - lazyIdx
            val entry = listDerived.timeline.getOrNull(timelineIndex) ?: continue
            val id = when (entry) {
                is ForumTimelineEntry.Message -> entry.messageId
                else -> continue
            }
            if (id.isBlank()) continue
            if (self.isNotBlank()) {
                val sender = stableMessages.find { it.id == id }?.senderUserId?.trim()
                if (sender == self) continue
            }
            if (lastRead.isNotEmpty() && !isObjectIdNewer(id, lastRead)) continue
            watermark = when (val prev = watermark) {
                null -> id
                else -> if (isObjectIdNewer(id, prev)) id else prev
            }
        }
        val markId = watermark ?: return
        scheduleMarkForumTopicRead(markId)
    }

    val markReadTopicKey = forumMarkReadTopicKey(topicId)
    LaunchedEffect(teamId, topicId, stableMessages.lastOrNull()?.id) {
        onProvideMarkReadAction(markReadTopicKey) {
            scope.launch {
                val newestId = stableMessages.lastOrNull()?.id
                if (!newestId.isNullOrBlank()) {
                    markTopicReadToLatest(forceSync = true)
                } else {
                    TeamForumMarkRead.markTopicReadToLatest(
                        teamsRepository = forumRepository.teams(),
                        forumPrefs = forumPrefs,
                        teamId = teamId,
                        topicId = topicId,
                    )
                }
            }
        }
    }
    DisposableEffect(markReadTopicKey) {
        onDispose { onProvideMarkReadAction(markReadTopicKey, null) }
    }

    fun canDeleteForumMessage(msg: TeamForumMessageDto): Boolean {
        val deleted = !msg.deletedAt.isNullOrBlank() &&
            !msg.deletedAt.equals("null", ignoreCase = true)
        if (deleted) return false
        return msg.senderUserId == currentUserId || canModerateMessages
    }

    fun uploadPickedApk(uri: Uri, displayName: String) {
        scope.launch {
            uploadingFile = true
            error = null
            pendingApkLabel = displayName.trim().ifBlank { "update.apk" }
            pickedImageUris = emptyList()
            attachmentPreviewStartIndex = null
            val result = uploadForumApkFromUri(context, res, forumRepository, teamId, uri, displayName)
            uploadingFile = false
            result.onSuccess { (fileId, label) ->
                pendingApkFileId = fileId
                pendingApkLabel = label
            }
            result.onFailure { e ->
                pendingApkFileId = null
                pendingApkLabel = null
                error = e.toUserMessageRu(res)
            }
        }
    }

    val pickApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                uploadPickedApk(uri, queryDisplayName(context, uri))
            }
        },
    )

    suspend fun loadOlderForumPage(): Boolean {
        if (!hasMoreOlder || loadingOlder) return false
        val oldestId = messages.firstOrNull()?.id?.trim().orEmpty()
        if (oldestId.isEmpty()) return false
        loadingOlder = true
        return try {
            val page = forumRepository.listForumMessages(currentUserId, teamId, topicId, before = oldestId, limit = 50)
                .getOrElse { return false }
            val visible = page.filter { m ->
                m.deletedAt.isNullOrBlank() ||
                    m.deletedAt.equals("null", ignoreCase = true)
            }
            val existingIds = messages.asSequence().map { it.id }.toHashSet()
            val older = visible.filter { it.id !in existingIds }
            messages.addAll(0, older)
            capForumMessagesTrimNewestOnly(messages)
            bumpMessagesGeneration()
            hasMoreOlder = page.size >= 50 && messages.isNotEmpty()
            true
        } finally {
            loadingOlder = false
        }
    }

    fun jumpToFirstUnreadInTopic() {
        val lastRead = lastReadCursor?.trim().orEmpty()
        if (lastRead.isEmpty()) return
        val self = currentUserId.trim()
        val targetId = stableMessages.lastOrNull { msg ->
            val id = msg.id.trim()
            if (id.isEmpty()) return@lastOrNull false
            if (self.isNotBlank() && msg.senderUserId.trim() == self) return@lastOrNull false
            isObjectIdNewer(id, lastRead)
        }?.id ?: return
        scope.launch {
            jumpToForumPinnedMessage(
                messageId = targetId,
                messageIdsOldestFirst = { stableMessages.map { it.id } },
                hasMoreOlder = { hasMoreOlder },
                isLoadingOlder = { loadingOlder },
                loadOlder = { loadOlderForumPage() },
                timelineIndexForMessageId = { id ->
                    forumLazyIndexForMessageId(stableMessages, listDerived, id)
                },
                scrollToTimelineIndex = { idx ->
                    runCatching { listState.scrollTimelineItemToViewportCenter(idx) }
                        .onFailure { listState.scrollToItem(idx) }
                },
                onHighlight = { id -> highlightMessageId = id },
            )
        }
    }

    fun visibleForumMessages(page: List<TeamForumMessageDto>): List<TeamForumMessageDto> =
        page.filter { m ->
            m.deletedAt.isNullOrBlank() ||
                m.deletedAt.equals("null", ignoreCase = true)
        }

    fun loadForumMessages(before: String?, appendOlder: Boolean, forceRefresh: Boolean = false) {
        scope.launch {
            if (appendOlder) {
                loadingOlder = true
            } else {
                error = null
                val knownEmpty = (topicSnapshot?.messageCount ?: -1) == 0
                val stashed = ForumMessageStash.drain(teamId, topicId)
                val diskSnapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (currentUserId.isNotBlank()) {
                        app.launchDiskCache.loadForumMessages(currentUserId, teamId, topicId)
                    } else {
                        null
                    }
                }
                if (diskSnapshot != null) {
                    messages.clear()
                    messages.addAll(visibleForumMessages(diskSnapshot.messages))
                    if (stashed.isNotEmpty()) {
                        val merged = mergeForumMessagesPage(messages.toList(), stashed)
                        messages.clear()
                        messages.addAll(merged)
                    }
                    trimForumMessagesInMemory()
                    hasMoreOlder = diskSnapshot.hasMoreOlder
                    loading = false
                } else if (knownEmpty) {
                    messages.clear()
                    if (stashed.isNotEmpty()) {
                        messages.addAll(stashed.sortedBy { it.id })
                    }
                    hasMoreOlder = false
                    loading = false
                } else {
                    loading = true
                }
            }
            val bypassCache = forceRefresh || forceForumRefreshAfterReconnect
            forumRepository.listForumMessages(currentUserId, teamId, topicId, before = before, limit = 50, bypassCache = bypassCache)
                .onSuccess { page ->
                    val visible = visibleForumMessages(page)
                    if (appendOlder) {
                        val existingIds = messages.asSequence().map { it.id }.toHashSet()
                        val older = visible.filter { it.id !in existingIds }
                        messages.addAll(0, older)
                        capForumMessagesTrimNewestOnly(messages)
                        bumpMessagesGeneration()
                    } else {
                        val stashed = if (messages.isEmpty()) {
                            ForumMessageStash.drain(teamId, topicId)
                        } else {
                            emptyList()
                        }
                        val merged = mergeForumMessagesPage(
                            mergeForumMessagesPage(messages.toList(), stashed),
                            visible,
                        )
                        messages.clear()
                        messages.addAll(merged)
                        capForumMessagesOldestFirst(messages)
                        bumpMessagesGeneration()
                        knownForumMessageIds = messages.map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()
                    }
                    hasMoreOlder = page.size >= 50 && messages.isNotEmpty()
                    if (!appendOlder) {
                        persistForumMessagesToDisk()
                        lastForumBackgroundSyncAtMs = System.currentTimeMillis()
                        forceForumRefreshAfterReconnect = false
                    }
                }
                .onFailure { e ->
                    if (!appendOlder && messages.isEmpty()) error = e.toUserMessageRu(res)
                }
            loading = false
            loadingOlder = false
        }
    }

    fun mergeNew(msg: TeamForumMessageDto, source: String = "socket") {
        if (shouldBlockOwnForumOutgoingRealtime(
                messages,
                msg,
                currentUserId,
                inFlightForumClientMessageIds,
            )
        ) {
            if (BuildConfig.DEBUG) {
                Log.d("SR_Forum", "drop team=$teamId topic=$topicId id=${msg.id} reason=own_echo")
            }
            return
        }
        if (source == "socket") {
            val visibleNewestId = messages.lastOrNull()?.id
            if (shouldTriggerGapReconcile(
                    visibleNewestId,
                    msg.id,
                    knownForumMessageIds,
                    thresholdMs = FORUM_GAP_RECONCILE_THRESHOLD_MS,
                )
            ) {
                if (BuildConfig.DEBUG) {
                    Log.i("SR_Forum", "gapReconcile team=$teamId topic=$topicId trigger=socket_jump")
                }
                loadForumMessages(before = null, appendOlder = false, forceRefresh = true)
                return
            }
        }
        forumRepository.invalidateForumMessagesCache(teamId, topicId)
        val replaced = replaceMatchingPendingForumOutgoing(messages, msg, currentUserId)
        if (!replaced) {
            val clientId = msg.clientMessageId?.trim().orEmpty()
            val i = messages.indexOfFirst { existing ->
                existing.id == msg.id ||
                    (clientId.isNotEmpty() && existing.clientMessageId?.trim() == clientId)
            }
            if (i >= 0) {
                messages[i] = messages[i].mergePreservingForumMedia(msg)
            } else {
                if (msg.senderUserId.trim() == currentUserId.trim()) {
                    if (clientId.isNotEmpty()) {
                        removePendingForumOutgoing(messages, clientId)
                    } else {
                        messages.removeAll {
                            isForumPendingId(it.id) &&
                                it.senderUserId.trim() == currentUserId.trim() &&
                                it.text.trim() == msg.text.trim()
                        }
                    }
                }
                messages.add(msg)
                trimForumMessagesInMemory()
            }
        }
        bumpMessagesGeneration()
        val msgId = msg.id.trim()
        if (msgId.isNotEmpty()) {
            knownForumMessageIds = knownForumMessageIds + msgId
        }
        msg.clientMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { clientId ->
            inFlightForumClientMessageIds = inFlightForumClientMessageIds - clientId
        }
        if (BuildConfig.DEBUG) {
            Log.d("SR_Forum", "merge team=$teamId topic=$topicId count=1 source=$source")
        }
        persistForumMessagesToDisk()
        if (!overlayUi || isNearBottom) {
            if (msg.senderUserId.trim() == currentUserId.trim()) {
                msg.id.trim().takeIf { it.isNotEmpty() && !isForumPendingId(it) }?.let { mergeReadCursor(it) }
            } else {
                markTopicReadToLatest()
            }
        }
    }

    LaunchedEffect(currentUserId, teamId, topicId) {
        topicViewModel.resumePendingOutbox(currentUserId) { confirmed ->
            mergeNew(confirmed, source = "outbox")
        }
    }

    LaunchedEffect(teamId, topicId) {
        clearPendingAttachment()
        draft = ""
        loadForumMessages(before = null, appendOlder = false)
    }

    LaunchedEffect(teamId, topicId) {
        lastReadCursor = forumPrefs.getLastReadMessageId(teamId, topicId)
        otherReadUptoMessageId = otherReadUptoByTopic[topicId]
        forumRepository.getForumPeerReadCursor(teamId, topicId)
            .onSuccess { peerUpto ->
                val published = ForumPeerReadCursorLogic.hydratePeerRead(
                    otherReadUptoByTopic = otherReadUptoByTopic,
                    topicId = topicId,
                    peerUptoMessageId = peerUpto,
                )
                if (published != null) {
                    otherReadUptoMessageId = published
                }
            }
    }

    LaunchedEffect(stableMessages.lastOrNull()?.id, isNearBottom) {
        if (!overlayUi && stableMessages.isNotEmpty() && isNearBottom) {
            val newest = stableMessages.lastOrNull() ?: return@LaunchedEffect
            if (newest.senderUserId.trim() == currentUserId.trim()) {
                newest.id.trim().takeIf { it.isNotEmpty() && !isForumPendingId(it) }?.let { mergeReadCursor(it) }
            } else {
                markTopicReadToLatest()
            }
        }
    }

    var forumSocketHadConnected by remember(teamId, topicId) { mutableStateOf(false) }
    LaunchedEffect(teamId, topicId, sectionActive) {
        if (!sectionActive) return@LaunchedEffect
        forumSocket.connectionState.collect { state ->
            if (state == com.lastasylum.alliance.data.teams.TeamForumSocketState.Connected) {
                if (forumSocketHadConnected) {
                    forceForumRefreshAfterReconnect = true
                    forumRepository.invalidateForumMessagesCache(teamId, topicId)
                    loadForumMessages(before = null, appendOlder = false, forceRefresh = true)
                    forumRepository.getForumPeerReadCursor(teamId, topicId)
                        .onSuccess { peerUpto ->
                            val published = ForumPeerReadCursorLogic.hydratePeerRead(
                                otherReadUptoByTopic = otherReadUptoByTopic,
                                topicId = topicId,
                                peerUptoMessageId = peerUpto,
                            )
                            if (published != null) {
                                otherReadUptoMessageId = published
                            }
                        }
                }
                forumSocketHadConnected = true
            }
        }
    }

    LaunchedEffect(teamId, topicId, sectionActive) {
        if (!sectionActive) return@LaunchedEffect
        while (true) {
            delay(ACTIVE_FORUM_RECONCILE_INTERVAL_MS)
            if (!sectionActive) break
            val elapsed = System.currentTimeMillis() - lastForumBackgroundSyncAtMs
            if (elapsed >= ACTIVE_FORUM_RECONCILE_INTERVAL_MS) {
                loadForumMessages(before = null, appendOlder = false, forceRefresh = false)
            }
        }
    }

    val topicUnreadEstimate = remember(stableMessages, lastReadCursor, currentUserId) {
        val lastRead = lastReadCursor?.trim().orEmpty()
        val self = currentUserId.trim()
        stableMessages.count { msg ->
            val id = msg.id.trim()
            if (id.isEmpty()) return@count false
            if (self.isNotBlank() && msg.senderUserId.trim() == self) return@count false
            lastRead.isEmpty() || isObjectIdNewer(id, lastRead)
        }
    }
    val firstUnreadMessageId = remember(stableMessages, lastReadCursor, currentUserId) {
        val lastRead = lastReadCursor?.trim().orEmpty()
        val self = currentUserId.trim()
        stableMessages.lastOrNull { msg ->
            val id = msg.id.trim()
            if (id.isEmpty()) return@lastOrNull false
            if (self.isNotBlank() && msg.senderUserId.trim() == self) return@lastOrNull false
            lastRead.isEmpty() || isObjectIdNewer(id, lastRead)
        }?.id
    }
    val firstUnreadLazyIndex = remember(firstUnreadMessageId, listDerived) {
        firstUnreadMessageId?.let { listDerived.fullLazyIndexForMessageId(it) } ?: -1
    }
    val isFirstUnreadVisible by remember(listState, firstUnreadLazyIndex) {
        derivedStateOf {
            if (firstUnreadLazyIndex < 0) return@derivedStateOf true
            listState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadLazyIndex }
        }
    }
    val showJumpToUnreadMessages by remember(
        overlayUi,
        topicUnreadEstimate,
        firstUnreadLazyIndex,
        isFirstUnreadVisible,
        isNearBottom,
    ) {
        derivedStateOf {
            overlayUi &&
                topicUnreadEstimate > 0 &&
                firstUnreadLazyIndex >= 0 &&
                isNearBottom &&
                !isFirstUnreadVisible
        }
    }
    val markVisibleForumRef = rememberUpdatedState(::markVisibleForumMessages)
    LaunchedEffect(listState, overlayUi, teamId, topicId) {
        if (!overlayUi) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .debounce(140)
            .collect { indices ->
                markVisibleForumRef.value(indices)
            }
    }

    var initialScrollApplied by remember(teamId, topicId) { mutableStateOf(false) }
    LaunchedEffect(stableMessages.lastOrNull()?.id, isNearBottom) {
        val newestId = stableMessages.lastOrNull()?.id ?: return@LaunchedEffect
        if (newestId != lastCountedNewestId) {
            lastCountedNewestId = newestId
            if (!isNearBottom && initialScrollApplied) {
                newMessagesWhileScrolledUp = (newMessagesWhileScrolledUp + 1).coerceAtMost(999)
            }
        }
    }
    LaunchedEffect(loadingOlder) {
        if (loadingOlder && pendingForumScrollAnchor == null) {
            pendingForumScrollAnchor = ForumScrollAnchor(
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                timelineSize = timelineSize,
            )
        }
    }

    LaunchedEffect(loadingOlder, timelineSize) {
        if (loadingOlder) return@LaunchedEffect
        val anchor = pendingForumScrollAnchor ?: return@LaunchedEffect
        pendingForumScrollAnchor = null
        val delta = timelineSize - anchor.timelineSize
        if (delta > 0) {
            listState.scrollToItem(
                anchor.firstVisibleItemIndex + delta,
                anchor.firstVisibleItemScrollOffset,
            )
        }
    }

    val hasMoreOlderRef = rememberUpdatedState(hasMoreOlder)
    val loadingOlderRef = rememberUpdatedState(loadingOlder)
    val loadingRef = rememberUpdatedState(loading)

    LaunchedEffect(listState, teamId, topicId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastIdx = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            ForumLoadOlderSignal(lastIdx, total)
        }
            .distinctUntilChanged()
            .debounce(48)
            .collect { sig ->
                if (listState.isScrollInProgress) return@collect
                if (sig.totalItems > 4 &&
                    sig.lastVisibleIndex >= sig.totalItems - 2 &&
                    hasMoreOlderRef.value &&
                    !loadingOlderRef.value &&
                    !loadingRef.value
                ) {
                    val oldestId = stableMessages.firstOrNull()?.id
                    if (oldestId != null) {
                        loadForumMessages(before = oldestId, appendOlder = true)
                    }
                }
            }
    }

    LaunchedEffect(stableMessages.lastOrNull()?.id, listDerived.timeline.size) {
        if (stableMessages.isEmpty() || listDerived.timeline.isEmpty()) return@LaunchedEffect
        if (!initialScrollApplied) {
            initialScrollApplied = true
            runCatching {
                listState.scrollReverseChatRevealLatest(animate = false, adjustViewport = false)
            }
            return@LaunchedEffect
        }
        if (isNearBottom && !listState.isScrollInProgress) {
            runCatching {
                listState.scrollReverseChatRevealLatest(animate = false, adjustViewport = false)
            }
        }
    }

    LaunchedEffect(typingHint) {
        val hint = typingHint ?: return@LaunchedEffect
        delay(4000)
        if (typingHint == hint) typingHint = null
    }

    DisposableEffect(teamId, topicId, sectionActive) {
        if (!sectionActive) {
            onDispose { }
        } else {
            val onNew: (TeamForumMessageDto) -> Unit = { mergeNew(it) }
            val onEdited: (TeamForumMessageDto) -> Unit = { applyEdited(it) }
            val onDeleted: (TeamForumMessageDeletedEvent) -> Unit = { applyDeleted(it) }
            val onTyping: (TeamForumTypingEvent) -> Unit = { ev ->
                if (ev.userId != currentUserId) {
                    typingHint = context.getString(R.string.team_forum_typing, ev.username)
                }
            }
            val onPin: (TeamForumTopicPinChangedEvent) -> Unit = { applyTopicPin(it) }
            val onReaction: (TeamForumMessageReactionEvent) -> Unit = { ev ->
                if (ev.teamId == teamId && ev.topicId == topicId) {
                    applyForumMessageReactions(ev.messageId, ev.reactions)
                }
            }
            val onTopicRead: (TeamForumTopicReadEvent) -> Unit = { ev ->
                if (ev.teamId == teamId && ev.topicId == topicId) {
                    val published = ForumPeerReadCursorLogic.mergeTopicReadEvent(
                        otherReadUptoByTopic = otherReadUptoByTopic,
                        topicId = topicId,
                        userId = ev.userId,
                        messageId = ev.messageId,
                        currentUserId = currentUserId,
                    )
                    if (published != null) {
                        otherReadUptoMessageId = published
                    }
                }
            }
            forumSocket.addMessageListener(onNew)
            forumSocket.addMessageEditedListener(onEdited)
            forumSocket.addMessageDeletedListener(onDeleted)
            forumSocket.addMessageReactionListener(onReaction)
            forumSocket.addTypingListener(onTyping)
            forumSocket.addTopicPinChangedListener(onPin)
            forumSocket.addTopicReadListener(onTopicRead)
            forumSocket.connect(
                BuildConfig.API_BASE_URL,
                teamId,
                topicId,
            ) { tokenStore.getAccessToken() }
            onDispose {
                if (overlayUi) {
                    scope.launch { forumMarkReadCoalescer.flushAndAwait(topicId) }
                } else {
                    markTopicReadToLatest(forceSync = true)
                }
                forumSocket.removeMessageListener(onNew)
                forumSocket.removeMessageEditedListener(onEdited)
                forumSocket.removeMessageDeletedListener(onDeleted)
                forumSocket.removeMessageReactionListener(onReaction)
                forumSocket.removeTypingListener(onTyping)
                forumSocket.removeTopicPinChangedListener(onPin)
                forumSocket.removeTopicReadListener(onTopicRead)
                forumSocket.connectTeamInbox(
                    BuildConfig.API_BASE_URL,
                    teamId,
                ) { tokenStore.getAccessToken() }
            }
        }
    }

    val forumListContentPadding = PaddingValues(
        start = SquadRelayDimens.contentPaddingHorizontal,
        end = SquadRelayDimens.contentPaddingHorizontal,
        top = 52.dp,
        bottom = 10.dp,
    )

    CompositionLocalProvider(
        LocalOpenRemoteChatImagePreview provides openImages,
    ) {
    Column(Modifier.fillMaxSize()) {
        error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        typingHint?.let { hint ->
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        pinNotice?.let { notice ->
            Text(
                notice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
            LaunchedEffect(notice) {
                delay(2500)
                if (pinNotice == notice) pinNotice = null
            }
        }
        val pinBarPreview = remember(pinRevision) { pinCoordinator.pinBarPreview }
        val pinHistoryCount = remember(pinRevision) { pinCoordinator.pinHistoryCount }
        val pinMessageId = remember(pinRevision) { pinCoordinator.pinnedMessageId }
        val pinnedYouLabel = stringResource(R.string.chat_pinned_meta_you)
        val pinnedMetaLine = remember(pinRevision, pinBarPreview) {
            formatPinnedMetaLine(
                pinnedAt = pinCoordinator.pinnedAt,
                pinnedByUsername = pinBarPreview?.pinnedByUsername,
                pinnedByUserId = pinCoordinator.pinnedByUserId,
                currentUserId = currentUserId,
                youLabel = pinnedYouLabel,
                userTemplate = { name -> res.getString(R.string.chat_pinned_meta_user, name) },
                formatTime = { formatForumTopicTimeRu(it) },
            )
        }
        val pinBarDismissed = remember(pinRevision, pinMessageId) {
            val pinId = pinCoordinator.pinnedMessageId?.trim().orEmpty()
            pinId.isNotEmpty() &&
                pinHistoryPrefs.isPinBarDismissed(pinHistoryPrefs.forumScopeKey(teamId, topicId), pinId)
        }
        if (pinMessageId != null && !pinBarDismissed) {
            pinBarPreview?.let { preview ->
            val pinnedDeleted = isForumPinnedPreviewLikelyDeleted(
                preview = preview,
                messages = stableMessages,
                serverPreview = pinCoordinator.pinnedMessage,
                pinnedMessageId = pinCoordinator.pinnedMessageId,
            )
            val pinnedUnavailable = isForumPinnedPreviewUnavailable(
                preview = preview,
                messages = stableMessages,
                serverPreview = pinCoordinator.pinnedMessage,
                pinnedMessageId = pinCoordinator.pinnedMessageId,
            )
            PinnedMessageBar(
                preview = preview,
                canUnpin = canModerateMessages,
                onTap = {
                    scope.launch {
                        val targetId = preview.id.trim().ifEmpty {
                            pinCoordinator.pinnedMessageId?.trim().orEmpty()
                        }
                        if (targetId.isEmpty()) return@launch
                        val jumped = jumpToForumPinnedMessage(
                            messageId = targetId,
                            messageIdsOldestFirst = { stableMessages.map { it.id } },
                            hasMoreOlder = { hasMoreOlder },
                            isLoadingOlder = { loadingOlder },
                            loadOlder = { loadOlderForumPage() },
                            timelineIndexForMessageId = { id ->
                                forumLazyIndexForMessageId(stableMessages, listDerived, id)
                            },
                            scrollToTimelineIndex = { idx ->
                                runCatching { listState.scrollTimelineItemToViewportCenter(idx) }
                                    .onFailure { listState.scrollToItem(idx) }
                            },
                            onHighlight = { id -> highlightMessageId = id },
                        )
                        if (!jumped) {
                            pinNotice = res.getString(R.string.chat_jump_quote_not_found)
                        } else {
                            delay(900)
                            if (highlightMessageId == targetId) highlightMessageId = null
                            if (pinCoordinator.pinHistoryCount > 1) {
                                pinCoordinator.advancePinBarIndex(stableMessages)
                                bumpPinUi()
                            }
                        }
                    }
                },
                onUnpin = {
                    val pinId = pinBarPreview.id.trim()
                        .ifEmpty { pinCoordinator.pinnedMessageId?.trim().orEmpty() }
                    if (pinId.isNotEmpty()) {
                        unpinOneForumMessage(pinId)
                    } else {
                        unpinForumTopic()
                    }
                },
                historyCount = pinHistoryCount,
                messageDeleted = pinnedDeleted,
                messageUnavailable = pinnedUnavailable,
                thumbnailUrl = preview.resolvedThumbnailUrl(),
                pinnedMetaLine = pinnedMetaLine,
                onLongPress = { showForumPinnedSheet = true },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            }
        }
        if (pinBarDismissed && pinHistoryCount > 0) {
            PinnedMessagesCompactChip(
                count = pinHistoryCount,
                onTap = { showForumPinnedSheet = true },
                onLongPress = {
                    pinCoordinator.restorePinBar()
                    bumpPinUi()
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        PinnedMessagesSheet(
            visible = showForumPinnedSheet,
            items = pinCoordinator.pinnedMessages.ifEmpty {
                pinBarPreview?.let { listOf(it) } ?: emptyList()
            },
            canModerate = canModerateMessages,
            activePinId = pinCoordinator.pinnedMessageId,
            onDismiss = { showForumPinnedSheet = false },
            onJumpTo = { messageId ->
                scope.launch {
                    val jumped = jumpToForumPinnedMessage(
                        messageId = messageId,
                        messageIdsOldestFirst = { stableMessages.map { it.id } },
                        hasMoreOlder = { hasMoreOlder },
                        isLoadingOlder = { loadingOlder },
                        loadOlder = { loadOlderForumPage() },
                        timelineIndexForMessageId = { mid ->
                            forumLazyIndexForMessageId(stableMessages, listDerived, mid)
                        },
                        scrollToTimelineIndex = { idx ->
                            runCatching { listState.scrollTimelineItemToViewportCenter(idx) }
                                .onFailure { listState.scrollToItem(idx) }
                        },
                        onHighlight = { mid -> highlightMessageId = mid },
                    )
                    if (!jumped) {
                        pinNotice = res.getString(R.string.chat_jump_quote_not_found)
                    }
                }
            },
            onUnpinOne = { unpinOneForumMessage(it) },
            onUnpinAll = { unpinForumTopic() },
            onHideBar = {
                val pinId = pinCoordinator.pinnedMessageId?.trim().orEmpty()
                if (pinId.isNotEmpty()) {
                    pinHistoryPrefs.setDismissedPinBar(pinHistoryPrefs.forumScopeKey(teamId, topicId), pinId)
                    bumpPinUi()
                }
                showForumPinnedSheet = false
            },
            messageStateFor = { preview ->
                forumPinPreviewDisplayState(
                    preview = preview,
                    messages = stableMessages,
                    serverPreview = pinCoordinator.pinnedMessage,
                    pinnedMessageId = pinCoordinator.pinnedMessageId,
                )
            },
        )
        if (selectedMessageIds.isNotEmpty()) {
            ForumSelectionToolbar(
                selectedCount = selectedMessageIds.size,
                isDeleting = deletingSelection,
                onClear = { if (!deletingSelection) selectedMessageIds = emptySet() },
                onDelete = {
                    if (!deletingSelection) {
                        OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                        confirmBulkDelete = true
                    }
                },
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (loading && messages.isEmpty()) {
                CenteredScreenLoading()
            } else {
                val configuration = LocalConfiguration.current
                val listBubbleMaxWidth = remember(configuration.screenWidthDp) {
                    minOf(
                        configuration.screenWidthDp.dp * ChatBubbleMaxWidthFraction,
                        ChatBubbleMaxWidthCap,
                    )
                }
                val expandScrollCompensation = remember(listState, scope) {
                    { deltaPx: Int ->
                        scope.launch {
                            listState.scrollReverseChatCompensateExpand(deltaPx)
                        }
                        Unit
                    }
                }
                CompositionLocalProvider(
                    LocalChatBubbleMaxWidth provides listBubbleMaxWidth,
                    LocalMessageExpandScrollCompensation provides expandScrollCompensation,
                ) {
                ForumTopicMessagesLazyList(
                    modifier = Modifier.fillMaxSize(),
                    messages = stableMessages,
                    listDerived = listDerived,
                    listState = listState,
                    hasMoreOlder = hasMoreOlder,
                    loadingOlder = loadingOlder,
                    highlightMessageId = highlightMessageId,
                    contentPadding = forumListContentPadding,
                ) { msg, idx ->
                    val mine = msg.senderUserId == currentUserId
                    val inSelectionMode = selectedMessageIds.isNotEmpty()
                    val isSelected = msg.id in selectedMessageIds
                    val canDeleteMsg = canDeleteForumMessage(msg)
                    val pinnedMessageIds = remember(pinRevision) { pinCoordinator.pinnedMessageIds() }
                    ForumMessageBubble(
                        message = msg,
                        teamId = teamId,
                        topicId = topicId,
                        cluster = listDerived.clusterFlags.getOrNull(idx),
                        isMine = mine,
                        canDelete = canDeleteMsg,
                        inSelectionMode = inSelectionMode,
                        isSelected = isSelected,
                        highlighted = LocalChatHighlightMessageId.current == msg.id,
                        showPinnedMarker = msg.id in pinnedMessageIds,
                        onJumpToMessage = { targetId ->
                            val lazyIdx = listDerived.fullLazyIndexForMessageId(targetId)
                            if (lazyIdx != null) {
                                scope.launch {
                                    runCatching { listState.scrollTimelineItemToViewportCenter(lazyIdx) }
                                        .onFailure { listState.scrollToItem(lazyIdx) }
                                    highlightMessageId = targetId
                                    delay(900)
                                    if (highlightMessageId == targetId) highlightMessageId = null
                                }
                            }
                        },
                        onToggleSelection = { id ->
                            selectedMessageIds =
                                if (id in selectedMessageIds) selectedMessageIds - id else selectedMessageIds + id
                        },
                        onBeginSelection = { id ->
                            selectedMessageIds = setOf(id)
                            dismissMessageActions()
                        },
                        onSwipeReply = {
                            if (msg.deletedAt.isNullOrBlank()) {
                                replyToMessage = msg
                            }
                        },
                        onOpenActions = { req ->
                            if (msg.deletedAt.isNullOrBlank()) {
                                if (overlayUi) {
                                    OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
                                }
                                activeActionMessageId = req.messageId
                            }
                        },
                        onToggleReaction = { messageId, emoji -> toggleForumReaction(messageId, emoji) },
                        otherReadUptoMessageId = otherReadUptoMessageId,
                        downloadingForumFileUrl = downloadingForumFileUrl,
                        onDownloadForumFile = { forumMsg ->
                            val url = forumMsg.fileRelativeUrl?.trim().orEmpty()
                            if (url.isNotBlank() && downloadingForumFileUrl == null) {
                                downloadingForumFileUrl = url
                                scope.launch {
                                    downloadAndInstallForumApk(
                                        context,
                                        url,
                                        forumMsg.fileFilename,
                                    ).onFailure { e ->
                                        error = e.message
                                            ?: res.getString(R.string.chat_apk_download_failed)
                                    }
                                    downloadingForumFileUrl = null
                                }
                            }
                        },
                    )
                }
                }
                ForumTopicOverlayBackChip(
                    onClick = { leaveTopic() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = SquadRelayDimens.contentPaddingHorizontal,
                            top = 8.dp,
                        )
                        .zIndex(5f),
                )
                if (overlayUi) {
                    OverlayReactionLogJumpToUnreadFab(
                        visible = showJumpToUnreadMessages,
                        unreadCount = topicUnreadEstimate,
                        onClick = { jumpToFirstUnreadInTopic() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .zIndex(6f),
                    )
                }
                ChatScrollToLatestFab(
                    visible = showScrollToLatestFab,
                    newMessageCount = newMessagesWhileScrolledUp,
                    onClick = {
                        newMessagesWhileScrolledUp = 0
                        lastCountedNewestId = stableMessages.lastOrNull()?.id
                        scope.launch {
                            runCatching {
                                listState.scrollReverseChatRevealLatest(animate = true)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                )
            }
        }
        val forumReplyChat = remember(replyToMessage, teamId, topicId) {
            replyToMessage?.toDisplayChatMessage(teamId, topicId)
        }
        val forumEditingChat = remember(editingForumMessage, teamId, topicId) {
            editingForumMessage?.toDisplayChatMessage(teamId, topicId)
        }
        ChatComposerBar {
        ChatComposer(
            draft = draft,
            pickedImageUris = pickedImageUris,
            replyToMessage = forumReplyChat,
            editingMessage = forumEditingChat,
            isSending = sending,
            sendEnabled = !sending && !uploadingImage && !uploadingFile,
            readOnly = uploadingImage || uploadingFile,
            enabledStickerPackKeys = enabledStickerPackKeys,
            onDraftChange = {
                draft = it
                forumSocket.emitTyping()
            },
            onSendDraft = {
                scope.launch {
                    forumSendMutex.withLock {
                        val editing = editingForumMessage
                        if (editing != null) {
                            val trimmed = draft.trim()
                            if (trimmed.isEmpty() &&
                                editing.imageRelativeUrls.isEmpty() &&
                                editing.imageRelativeUrl.isNullOrBlank()
                            ) {
                                return@withLock
                            }
                            if (sending) return@withLock
                            sending = true
                            try {
                                forumRepository.patchForumMessage(teamId, topicId, editing.id, trimmed)
                                    .onSuccess {
                                        applyEdited(it)
                                        draft = ""
                                        editingForumMessage = null
                                    }
                                    .onFailure { e -> error = e.toUserMessageRu(res) }
                            } finally {
                                sending = false
                            }
                            return@withLock
                        }
                        val trimmed = draft.trim()
                        val urisToUpload = pickedImageUris.toList()
                        val apkFileId = pendingApkFileId
                        if (trimmed.isEmpty() && urisToUpload.isEmpty() && apkFileId == null) {
                            return@withLock
                        }
                        if (uploadingImage || uploadingFile || sending) return@withLock
                        sending = true
                        val clientMessageId = UUID.randomUUID().toString()
                        val replyId = replyToMessage?.id
                        val nowIso = Instant.now().toString()
                        val optimistic = buildOptimisticForumMessage(
                            teamId = teamId,
                            topicId = topicId,
                            senderUserId = currentUserId,
                            senderUsername = selfForumUsername,
                            text = trimmed,
                            clientMessageId = clientMessageId,
                            replyToMessageId = replyId,
                            nowIso = nowIso,
                        )
                        inFlightForumClientMessageIds = inFlightForumClientMessageIds + clientMessageId
                        mergeNew(optimistic, source = "optimistic")
                        draft = ""
                        replyToMessage = null
                        clearPendingAttachment()
                        sending = false
                        scope.launch {
                            var imageFileIds: List<String>? = null
                            if (urisToUpload.isNotEmpty()) {
                                uploadingImage = true
                                uploadForumImagesFromUris(
                                    context,
                                    res,
                                    forumRepository,
                                    teamId,
                                    urisToUpload,
                                )
                                    .onSuccess { (ids, _) -> imageFileIds = ids.takeIf { it.isNotEmpty() } }
                                    .onFailure { e ->
                                        uploadingImage = false
                                        removePendingForumOutgoing(messages, clientMessageId)
                                        inFlightForumClientMessageIds =
                                            inFlightForumClientMessageIds - clientMessageId
                                        bumpMessagesGeneration()
                                        error = e.toUserMessageRu(res)
                                        return@launch
                                    }
                                uploadingImage = false
                            }
                            val outboxEntry = ForumOutboxEntry(
                                clientMessageId = clientMessageId,
                                userId = currentUserId,
                                teamId = teamId,
                                topicId = topicId,
                                pendingMessageId = optimistic.id,
                                text = trimmed,
                                replyToMessageId = replyId,
                                imageFileIds = imageFileIds,
                                fileFileId = apkFileId,
                                state = "pending",
                                attempts = 0,
                                createdAtMs = System.currentTimeMillis(),
                                lastError = null,
                            )
                            topicViewModel.persistOutbox(outboxEntry)
                            forumRepository.postForumMessageWithRetries(
                                currentUserId,
                                teamId,
                                topicId,
                                trimmed,
                                replyToMessageId = replyId,
                                imageFileId = null,
                                imageFileIds = imageFileIds,
                                fileFileId = apkFileId,
                                clientMessageId = clientMessageId,
                            )
                                .onSuccess {
                                    topicViewModel.markOutboxSent(clientMessageId)
                                    mergeNew(it, source = "http")
                                }
                                .onFailure { e ->
                                    topicViewModel.markOutboxFailed(
                                        clientMessageId,
                                        e.message ?: "send_failed",
                                        currentUserId,
                                    )
                                    removePendingForumOutgoing(messages, clientMessageId)
                                    inFlightForumClientMessageIds =
                                        inFlightForumClientMessageIds - clientMessageId
                                    bumpMessagesGeneration()
                                    error = e.toUserMessageRu(res)
                                }
                        }
                    }
                }
            },
            onSendStickerPayload = { payload ->
                scope.launch {
                    forumSendMutex.withLock {
                        if (sending) return@withLock
                        sending = true
                        clearPendingAttachment()
                        val clientMessageId = UUID.randomUUID().toString()
                        val nowIso = Instant.now().toString()
                        val optimistic = buildOptimisticForumMessage(
                            teamId = teamId,
                            topicId = topicId,
                            senderUserId = currentUserId,
                            senderUsername = selfForumUsername,
                            text = payload,
                            clientMessageId = clientMessageId,
                            replyToMessageId = replyToMessage?.id,
                            nowIso = nowIso,
                        )
                        val replyId = replyToMessage?.id
                        inFlightForumClientMessageIds = inFlightForumClientMessageIds + clientMessageId
                        mergeNew(optimistic, source = "optimistic")
                        replyToMessage = null
                        sending = false
                        scope.launch {
                            val outboxEntry = ForumOutboxEntry(
                                clientMessageId = clientMessageId,
                                userId = currentUserId,
                                teamId = teamId,
                                topicId = topicId,
                                pendingMessageId = optimistic.id,
                                text = payload,
                                replyToMessageId = replyId,
                                imageFileIds = null,
                                fileFileId = null,
                                state = "pending",
                                attempts = 0,
                                createdAtMs = System.currentTimeMillis(),
                                lastError = null,
                            )
                            topicViewModel.persistOutbox(outboxEntry)
                            forumRepository.postForumMessageWithRetries(
                                userId = currentUserId,
                                teamId = teamId,
                                topicId = topicId,
                                text = payload,
                                replyToMessageId = replyId,
                                imageFileId = null,
                                imageFileIds = null,
                                clientMessageId = clientMessageId,
                            )
                                .onSuccess {
                                    topicViewModel.markOutboxSent(clientMessageId)
                                    mergeNew(it, source = "http")
                                }
                                .onFailure { e ->
                                    topicViewModel.markOutboxFailed(
                                        clientMessageId,
                                        e.message ?: "send_failed",
                                        currentUserId,
                                    )
                                    removePendingForumOutgoing(messages, clientMessageId)
                                    inFlightForumClientMessageIds =
                                        inFlightForumClientMessageIds - clientMessageId
                                    bumpMessagesGeneration()
                                    error = e.toUserMessageRu(res)
                                }
                        }
                    }
                }
            },
            onPickImages = { uris, append ->
                val stable = stabilizeComposerImageUris(context, uris)
                if (stable.isEmpty()) return@ChatComposer
                pickedImageUris = if (append) {
                    (pickedImageUris + stable).distinctBy { it.toString() }
                } else {
                    stable.distinctBy { it.toString() }
                }.take(12)
            },
            onRemovePickedImage = { uri ->
                pickedImageUris = pickedImageUris.filterNot { it == uri }
            },
            onClearPickedImages = { clearPendingAttachment() },
            onClearReply = {
                replyToMessage = null
            },
            onClearEdit = {
                editingForumMessage = null
                draft = ""
            },
            onOpenAttachmentPreview = { idx -> attachmentPreviewStartIndex = idx },
            pendingApkLabel = pendingApkLabel,
            onClearPendingApk = { clearPendingAttachment() },
            onPickApk = if (canModerateMessages) {
                {
                    OverlayChatInteractionHold.prepareOverlayModalInteraction(overlayUi)
                    pickApkLauncher.launch("application/*")
                }
            } else {
                null
            },
            hasReadyFileAttachment = !pendingApkFileId.isNullOrBlank(),
            isUploadingFile = uploadingFile,
        )
        }
    }

    remoteImagePreview?.let { (urls, start) ->
        if (urls.isNotEmpty()) {
            MessengerImagesPreviewHost(
                urls = urls,
                startIndex = start,
                onDismiss = { remoteImagePreview = null },
            )
        }
    }
    attachmentPreviewStartIndex?.let { start ->
        if (pickedImageUris.isNotEmpty()) {
            AttachmentPreviewOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(8f),
                uris = pickedImageUris,
                startIndex = start,
                onDismiss = { attachmentPreviewStartIndex = null },
                onOpenExternal = { uri ->
                    val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val resolved = context.packageManager.resolveActivity(
                        viewIntent,
                        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                    )
                    if (resolved != null) {
                        context.startActivity(viewIntent)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.chat_open_external_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onRemove = { uri ->
                    pickedImageUris = pickedImageUris.filterNot { it == uri }
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

    // legacy menu dialog removed (replaced with context menu popup)


    val activeActionMessage = remember(activeActionMessageId, messages) {
        activeActionMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    }
    if (selectedMessageIds.isEmpty()) {
        activeActionMessage?.let { msg ->
            val menuCanPin = canModerateMessages && msg.deletedAt.isNullOrBlank()
            val pinnedMessageIds = remember(pinRevision) { pinCoordinator.pinnedMessageIds() }
            val isTopicPinnedMessage = msg.id in pinnedMessageIds
            val menuImageUrls = remember(msg.id, msg.imageRelativeUrl, msg.imageRelativeUrls) {
                buildList {
                    msg.imageRelativeUrl?.trim()?.takeIf { it.isNotBlank() }?.let {
                        add(resolvedChatAttachmentImageUrl(it))
                    }
                    msg.imageRelativeUrls.forEach { raw ->
                        val t = raw.trim()
                        if (t.isNotBlank()) add(resolvedChatAttachmentImageUrl(t))
                    }
                }.distinct()
            }
            val menuHasImages = menuImageUrls.isNotEmpty()
            val menuHasMapCoordinate = remember(msg.text) {
                com.lastasylum.alliance.game.MapCoordinateParser.parse(msg.text) != null
            }
            val menuMayEdit = (msg.senderUserId == currentUserId || canModerateMessages) &&
                msg.deletedAt.isNullOrBlank() &&
                (
                    msg.text.isNotBlank() ||
                        msg.imageRelativeUrls.isNotEmpty() ||
                        !msg.imageRelativeUrl.isNullOrBlank()
                    )
            val menuScope: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().zIndex(6f)) {
                    MessageContextMenuScrim(onDismiss = dismissMessageActions)
                    MessageContextMenuPopup(
                        showReactions = true,
                        canCopy = forumMessageHasMenuCopyAction(msg),
                        canPin = menuCanPin,
                        isPinned = isTopicPinnedMessage,
                        pinActionsEnabled = !pinCoordinator.pinInFlight,
                        mayEdit = menuMayEdit,
                        hasImages = menuHasImages,
                        hasMapCoordinate = menuHasMapCoordinate,
                        onDismiss = dismissMessageActions,
                        actions = MessageContextMenuActions(
                            onReply = {
                                replyToMessage = msg
                                editingForumMessage = null
                                dismissMessageActions()
                            },
                            onCopy = {
                                copyForumMessageToClipboard(context, msg)
                                dismissMessageActions()
                            },
                            onPin = {
                                pinForumMessage(msg.id, msg)
                                dismissMessageActions()
                            },
                            onUnpin = {
                                unpinOneForumMessage(msg.id)
                                dismissMessageActions()
                            },
                            onEdit = {
                                editingForumMessage = msg
                                draft = msg.text
                                replyToMessage = null
                                clearPendingAttachment()
                            },
                            onReact = { emoji -> toggleForumReaction(msg.id, emoji) },
                            onViewImages = if (menuHasImages) {
                                {
                                    remoteImagePreview = menuImageUrls to 0
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
                                        android.widget.Toast.makeText(context.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                null
                            },
                            onGoToMap = {
                                com.lastasylum.alliance.game.GameMapNavigator.openFromMessage(context, msg.text)
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

    if (confirmBulkDelete && selectedMessageIds.isNotEmpty()) {
        OverlayModalScope(preparedByCaller = true) {
        OverlayAwareAlertDialog(
            onDismissRequest = { if (!deletingSelection) confirmBulkDelete = false },
            title = { Text(stringResource(R.string.chat_bulk_delete_title)) },
            text = {
                Text(stringResource(R.string.chat_bulk_delete_body, selectedMessageIds.size))
            },
            confirmButton = {
                TextButton(
                    enabled = !deletingSelection,
                    onClick = {
                        scope.launch {
                            deletingSelection = true
                            val ids = selectedMessageIds.toList()
                            forumRepository.bulkDeleteForumMessages(teamId, topicId, ids)
                                .onSuccess { result ->
                                    ids.forEach { removeMessage(it) }
                                    ids.forEach { id ->
                                        pinCoordinator.clearPinIfMessageDeleted(id, stableMessages)
                                    }
                                    result.pinChanged?.let { applyTopicPin(it) }
                                }
                                .onFailure { e -> error = e.toUserMessageRu(res) }
                            selectedMessageIds = emptySet()
                            confirmBulkDelete = false
                            deletingSelection = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.chat_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deletingSelection,
                    onClick = { confirmBulkDelete = false },
                ) { Text(stringResource(R.string.chat_delete_cancel)) }
            },
        )
        }
    }
    }
}

private suspend fun uploadForumImagesFromUris(
    context: android.content.Context,
    res: android.content.res.Resources,
    forumRepository: ForumRepository,
    teamId: String,
    uris: List<Uri>,
): Result<Pair<List<String>, String?>> = coroutineScope {
    val cr = context.contentResolver
    val cacheDir = context.cacheDir
    val uploads = uris.map { uri ->
        async(Dispatchers.IO) {
            uploadSingleForumImageFromUri(cr, cacheDir, res, forumRepository, teamId, uri)
        }
    }.awaitAll()
    for (result in uploads) {
        if (result.isFailure) {
            return@coroutineScope Result.failure(
                result.exceptionOrNull()
                    ?: IllegalStateException(res.getString(R.string.chat_attachment_read_failed)),
            )
        }
    }
    val fileIds = uploads.mapNotNull { it.getOrNull()?.first }
    val lastPreview = uploads.mapNotNull { it.getOrNull()?.second }.lastOrNull()
    if (fileIds.isEmpty() && uris.isNotEmpty()) {
        return@coroutineScope Result.failure(
            IllegalStateException(res.getString(R.string.chat_attachment_read_failed)),
        )
    }
    Result.success(fileIds to lastPreview)
}

private suspend fun uploadSingleForumImageFromUri(
    cr: ContentResolver,
    cacheDir: java.io.File,
    res: android.content.res.Resources,
    forumRepository: ForumRepository,
    teamId: String,
    uri: Uri,
): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
    val tmp = File.createTempFile("forum_upload_${UUID.randomUUID()}", ".part", cacheDir)
    try {
        val input = openUriInputStream(cr, uri)
            ?: return@withContext Result.failure(
                IllegalStateException(
                    res.getString(R.string.chat_attachment_read_failed),
                ),
            )
        input.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
        if (tmp.length() == 0L) {
            return@withContext Result.failure(
                IllegalStateException(res.getString(R.string.chat_attachment_read_failed)),
            )
        }
        val header = ByteArray(32)
        tmp.inputStream().use { it.read(header) }
        val sniffed = sniffImageMimeFromHeader(header)
        val mime = resolveUploadImageMime(cr.getType(uri)?.trim().orEmpty(), sniffed)
            ?: return@withContext Result.failure(
                IllegalStateException(
                    res.getString(R.string.chat_attachment_unsupported),
                ),
            )
        val name =
            "forum_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.${guessExt(mime)}"
        val uploaded = forumRepository.uploadForumImageFromFile(teamId, tmp, name, mime).getOrElse { err ->
            return@withContext Result.failure(err)
        }
        Result.success(uploaded.fileId to uploaded.url)
    } finally {
        runCatching { tmp.delete() }
    }
}

private suspend fun uploadForumApkFromUri(
    context: android.content.Context,
    res: android.content.res.Resources,
    forumRepository: ForumRepository,
    teamId: String,
    uri: Uri,
    displayName: String,
): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
    val cr = context.contentResolver
    val safeName = displayName.trim().let { n ->
        if (n.endsWith(".apk", ignoreCase = true)) n else "$n.apk"
    }
    val tmp = File.createTempFile("forum_apk_${UUID.randomUUID()}", ".apk", context.cacheDir)
    try {
        val input = openUriInputStream(cr, uri)
            ?: return@withContext Result.failure(
                IllegalStateException(res.getString(R.string.chat_attachment_read_failed)),
            )
        input.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
        if (tmp.length() == 0L) {
            return@withContext Result.failure(
                IllegalStateException(res.getString(R.string.chat_attachment_prepare_failed)),
            )
        }
        val uploaded = forumRepository.uploadForumFileFromFile(
            teamId = teamId,
            file = tmp,
            fileName = safeName,
            mimeType = "application/vnd.android.package-archive",
        ).getOrElse { return@withContext Result.failure(it) }
        Result.success(uploaded.fileId to safeName)
    } finally {
        runCatching { tmp.delete() }
    }
}

private fun openUriInputStream(cr: ContentResolver, uri: Uri): InputStream? {
    runCatching { cr.openInputStream(uri) }.getOrNull()?.let { return it }
    val pfd = runCatching { cr.openFileDescriptor(uri, "r") }.getOrNull()
    if (pfd != null) {
        runCatching { return ParcelFileDescriptor.AutoCloseInputStream(pfd) }
        runCatching { pfd.close() }
    }
    val afd = runCatching { cr.openAssetFileDescriptor(uri, "r") }.getOrNull()
    if (afd != null) {
        runCatching { return afd.createInputStream() }
        runCatching { afd.close() }
    }
    return null
}

private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (i in prefix.indices) {
        if (this[i] != prefix[i]) return false
    }
    return true
}

private fun sniffImageMimeFromHeader(bytes: ByteArray): String? {
    if (bytes.size < 12) return null
    val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    if (bytes.hasPrefix(jpeg)) return "image/jpeg"
    val png = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    if (bytes.hasPrefix(png)) return "image/png"
    if (bytes.size >= 6) {
        val gif = String(bytes, 0, 6, Charsets.US_ASCII)
        if (gif == "GIF87a" || gif == "GIF89a") return "image/gif"
    }
    val riff = String(bytes, 0, 4, Charsets.US_ASCII)
    val webp = String(bytes, 8, 4, Charsets.US_ASCII)
    if (riff == "RIFF" && webp == "WEBP") return "image/webp"
    if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "image/bmp"
    if (bytes.size >= 12 &&
        bytes[4] == 'f'.code.toByte() &&
        bytes[5] == 't'.code.toByte() &&
        bytes[6] == 'y'.code.toByte() &&
        bytes[7] == 'p'.code.toByte()
    ) {
        val brand = String(bytes, 8, 4, Charsets.US_ASCII)
        if (brand.equals("heic", ignoreCase = true) ||
            brand.equals("heix", ignoreCase = true) ||
            brand.equals("mif1", ignoreCase = true) ||
            brand.equals("msf1", ignoreCase = true)
        ) {
            return "image/heic"
        }
    }
    return null
}

private fun resolveUploadImageMime(declared: String, sniffed: String?): String? {
    val d = declared.trim()
    val dl = d.lowercase(Locale.ROOT)
    return when {
        dl.startsWith("image/") && dl != "image/*" -> d
        dl == "image/*" -> sniffed ?: "image/jpeg"
        sniffed != null -> sniffed
        else -> null
    }
}

private fun guessExt(mime: String): String = when (mime.lowercase(Locale.ROOT)) {
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/bmp" -> "bmp"
    "image/heic" -> "heic"
    else -> "jpg"
}

@Composable
private fun ForumSelectionToolbar(
    selectedCount: Int,
    isDeleting: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = SquadRelaySurfaces.panelColor(0.52f),
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
                    Icon(Icons.Outlined.Close, contentDescription = null)
                }
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete, enabled = !isDeleting) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = scheme.error,
                    )
                }
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
    }
}

/** Telegram-like instant feedback before REST round-trip. */
private fun applyOptimisticForumReactionToggle(
    message: TeamForumMessageDto,
    emoji: String,
): TeamForumMessageDto {
    val reactions = message.reactions.toMutableList()
    val at = reactions.indexOfFirst { it.emoji == emoji }
    if (at >= 0) {
        val row = reactions[at]
        if (row.reactedByMe) {
            val nextCount = row.count - 1
            if (nextCount <= 0) {
                reactions.removeAt(at)
            } else {
                reactions[at] = row.copy(count = nextCount, reactedByMe = false)
            }
        } else {
            reactions[at] = row.copy(count = row.count + 1, reactedByMe = true)
        }
    } else {
        reactions.add(
            ChatReaction(
                emoji = emoji,
                count = 1,
                reactedByMe = true,
            ),
        )
    }
    if (reactions == message.reactions) return message
    return message.copy(reactions = reactions)
}
