package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogFeedItem
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogReplyEnricher
import com.lastasylum.alliance.data.chat.OverlayReactionLogRepository
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.data.chat.maxOverlayReactionLogId
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import com.lastasylum.alliance.ui.chat.formatChatDaySeparator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun OverlayReactionNotificationsPanel(
    repository: OverlayReactionLogRepository,
    selfUserId: String,
    onClose: () -> Unit,
    onReplyToReactionLog: (OverlayReactionLogEntry) -> Unit,
    onOpenReactionsPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Overlay ComposeView: explicit owner matches CombatOverlayService HUD/chat pattern.
    val lifecycleOwner = LocalLifecycleOwner.current
    val entries by repository.entries.collectAsStateWithLifecycle(lifecycleOwner)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = remember(context) { AppContainer.from(context) }
    val controller = remember(repository, container, scope) {
        OverlayReactionNotificationsController(
            scope = scope,
            repository = repository,
            usersRepository = container.usersRepository,
            teamsRepository = container.teamsRepository,
        )
    }
    val uiState by controller.uiState.collectAsStateWithLifecycle(lifecycleOwner)
    val repositoryUi by controller.repositoryUi.collectAsStateWithLifecycle(lifecycleOwner)
    val loading = repositoryUi.loading
    val refreshing = repositoryUi.refreshing
    val loadingMore = repositoryUi.loadingMore
    val error = repositoryUi.error
    val unreadCount = repositoryUi.unreadCount
    val unreadEntryIds = repositoryUi.unreadEntryIds
    val clustered = uiState.clustered
    val groupedFeed = uiState.groupedFeed
    val listLayout = uiState.listLayout
    val onlineUserIds = uiState.onlineUserIds
    val filterKey = uiState.filterKey
    val newestFeedEntryIds = uiState.newestFeedEntryIds
    var previewCluster by remember { mutableStateOf<OverlayReactionLogCluster?>(null) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showMarkAllReadConfirm by remember { mutableStateOf(false) }
    var hapticConsumedForSession by remember { mutableStateOf(false) }
    var markAllReadLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val markAllReadFailedText = stringResource(R.string.overlay_notifications_mark_all_read_failed)

    DisposableEffect(controller, selfUserId) {
        controller.start(selfUserId)
        onDispose {
            controller.stop()
            scope.launch {
                repository.flushPendingReadCursorAwait()
            }
        }
    }

    var savedScrollIndex by rememberSaveable(filterKey) { mutableIntStateOf(-1) }

    LaunchedEffect(entries.firstOrNull()?.id, unreadEntryIds) {
        val newest = entries.firstOrNull() ?: return@LaunchedEffect
        if (!hapticConsumedForSession && newest.id in unreadEntryIds) {
            runCatching {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            hapticConsumedForSession = true
        }
    }

    LaunchedEffect(error, clustered.isNotEmpty()) {
        val message = error ?: return@LaunchedEffect
        if (clustered.isEmpty()) return@LaunchedEffect
        val text = message.ifBlank {
            context.getString(R.string.overlay_notifications_reaction_failed)
        }
        val result = snackbarHostState.showSnackbar(
            message = text,
            actionLabel = context.getString(R.string.overlay_notifications_retry),
        )
        repository.clearError()
        if (result == SnackbarResult.ActionPerformed) {
            repository.refresh()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayHudPanelHeader(
                title = stringResource(R.string.overlay_notifications_title),
                subtitle = null,
                onClose = onClose,
                closeIconTint = Color.White,
                onMarkAllRead = {
                    OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
                    showMarkAllReadConfirm = true
                },
                markAllReadEnabled = unreadCount > 0 && !loading && !markAllReadLoading,
                markAllReadLoading = markAllReadLoading,
                markAllReadIconTint = Color.White,
                headerTrailing = {
                    IconButton(
                        onClick = {
                            OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
                            showClearHistoryConfirm = true
                        },
                        enabled = !loading,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(
                                R.string.overlay_notifications_clear_history_cd,
                            ),
                            tint = Color.White,
                        )
                    }
                },
            )
            if (refreshing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                    )
                    Text(
                        text = stringResource(R.string.overlay_notifications_syncing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            OverlayReactionLogFiltersBar(
                directionFilter = uiState.directionFilter,
                onDirectionFilter = controller::onDirectionFilter,
                scopeFilter = uiState.scopeFilter,
                onScopeFilter = controller::onScopeFilter,
                searchQuery = uiState.searchQuery,
                onSearchQuery = controller::onSearchQuery,
            )
            key(filterKey) {
                val latestClusterIndexForInit = listLayout.latestClusterItemIndex
                val initialListIndex = when {
                    savedScrollIndex >= 0 -> savedScrollIndex
                    latestClusterIndexForInit >= 0 -> latestClusterIndexForInit
                    else -> 0
                }.coerceAtMost(latestClusterIndexForInit.coerceAtLeast(0))
                val listState = rememberLazyListState(
                    cacheWindow = LazyLayoutCacheWindow(ahead = 140.dp, behind = 140.dp),
                    initialFirstVisibleItemIndex = initialListIndex,
                )
                LaunchedEffect(filterKey, listLayout.latestClusterItemIndex) {
                    val latestIndex = listLayout.latestClusterItemIndex
                    if (latestIndex < 0) return@LaunchedEffect
                    if (listState.firstVisibleItemIndex > latestIndex) {
                        listState.scrollToItem(latestIndex)
                    }
                }
                val firstUnreadIndex = listLayout.firstUnreadItemIndex
                val latestClusterIndex = listLayout.latestClusterItemIndex
                val isNearTop by remember(listState, latestClusterIndex) {
                    derivedStateOf {
                        if (latestClusterIndex < 0) return@derivedStateOf false
                        listState.firstVisibleItemIndex <= latestClusterIndex + 1
                    }
                }
                val isFirstUnreadVisible by remember(listState, firstUnreadIndex) {
                    derivedStateOf {
                        if (firstUnreadIndex < 0) return@derivedStateOf true
                        listState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadIndex }
                    }
                }
                val visibleUnreadCount by remember(clustered, unreadEntryIds) {
                    derivedStateOf {
                        clustered.count { it.representative.id in unreadEntryIds }
                    }
                }
                val showJumpToUnread by remember(
                    visibleUnreadCount,
                    firstUnreadIndex,
                    isFirstUnreadVisible,
                ) {
                    derivedStateOf {
                        visibleUnreadCount > 0 &&
                            firstUnreadIndex >= 0 &&
                            !isFirstUnreadVisible
                    }
                }
                val markReadUpToRef = rememberUpdatedState(repository::markReadUpTo)
                val unreadEntryIdsRef = rememberUpdatedState(unreadEntryIds)
                val groupedFeedRef = rememberUpdatedState(groupedFeed)
                fun visibleUnreadMarkReadIds(
                    visibleItems: List<androidx.compose.foundation.lazy.LazyListItemInfo>,
                ): Set<String> = visibleItems.flatMap { info ->
                    val key = info.key.toString()
                    collectMarkReadIdsForListKey(
                        listKey = key,
                        groupedFeed = groupedFeedRef.value,
                        unreadEntryIds = unreadEntryIdsRef.value,
                    )
                }.toSet()
                LaunchedEffect(listState, groupedFeed, unreadEntryIds) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                        .filter { it.isNotEmpty() }
                        .first()
                    val ids = visibleUnreadMarkReadIds(listState.layoutInfo.visibleItemsInfo)
                    val watermark = maxOverlayReactionLogId(ids) ?: return@LaunchedEffect
                    markReadUpToRef.value(watermark)
                }
                LaunchedEffect(listState, groupedFeed, unreadEntryIds) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                        .debounce(140)
                        .map { visible -> visibleUnreadMarkReadIds(visible) }
                        .distinctUntilChanged()
                        .collect { visibleUnreadIds ->
                            val watermark = maxOverlayReactionLogId(visibleUnreadIds) ?: return@collect
                            markReadUpToRef.value(watermark)
                        }
                }
                val showScrollToLatest by remember(isNearTop, groupedFeed) {
                    derivedStateOf { groupedFeed.isNotEmpty() && !isNearTop }
                }
                val previewContext = LocalContext.current
                val entryIdToReactionId by remember(groupedFeed) {
                    derivedStateOf {
                        buildMap {
                            groupedFeed.forEach { (_, items) ->
                                items.forEach { item ->
                                    val entryId = feedItemPrimaryEntryId(item)
                                    val reactionId = when (item) {
                                        is OverlayReactionLogFeedItem.Root ->
                                            item.cluster.representative.reaction
                                        is OverlayReactionLogFeedItem.ThreadParent ->
                                            item.parent.representative.reaction
                                    }
                                    put(entryId, reactionId)
                                }
                            }
                        }
                    }
                }
                val animatedPreviewIds by remember(
                    listState,
                    listLayout.itemIndexToEntryId,
                    newestFeedEntryIds,
                    entryIdToReactionId,
                ) {
                    derivedStateOf {
                        OverlayReactionPreviewAnimationPolicy.resolveAnimatedEntryIds(
                            newestEntryIds = newestFeedEntryIds,
                            visibleItems = listState.layoutInfo.visibleItemsInfo,
                            itemIndexToEntryId = listLayout.itemIndexToEntryId,
                            supportsAnimatedPreview = { entryId ->
                                val reactionId = entryIdToReactionId[entryId] ?: return@resolveAnimatedEntryIds false
                                overlayReactionSupportsAnimatedPreview(previewContext, reactionId)
                            },
                        )
                    }
                }
                LaunchedEffect(listState) {
                    snapshotFlow { listState.isScrollInProgress }
                        .distinctUntilChanged()
                        .collect { scrolling ->
                            if (!scrolling) {
                                savedScrollIndex = listState.firstVisibleItemIndex
                            }
                        }
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                when {
                    loading && clustered.isEmpty() -> {
                        OverlayReactionLogSkeleton(
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                    error != null && clustered.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.overlay_notifications_error),
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { repository.refresh() }) {
                                Text(stringResource(R.string.overlay_notifications_retry))
                            }
                        }
                    }
                    groupedFeed.isEmpty() -> {
                        EmptyNotificationsState(
                            directionFilter = uiState.directionFilter,
                            onOpenReactionsPicker = onOpenReactionsPicker,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        LaunchedEffect(listState, listLayout.itemIndexToEntryId.size) {
                            snapshotFlow {
                                val info = listState.layoutInfo
                                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible to info.totalItemsCount
                            }
                                .distinctUntilChanged()
                                .collect { (lastVisible, total) ->
                                    controller.loadMoreIfNeeded(lastVisible, total)
                                }
                        }

                        PullToRefreshBox(
                            isRefreshing = refreshing,
                            onRefresh = { repository.refresh() },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = com.lastasylum.alliance.ui.theme.SquadRelayDimens.contentPaddingHorizontal,
                                    vertical = 8.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listLayout.groupedFeed.forEach { (headerKey, feedItems) ->
                                    stickyHeader(key = "header-$headerKey", contentType = 0) {
                                        val sampleCreatedAt = feedItems.firstOrNull().let { item ->
                                            when (item) {
                                                is OverlayReactionLogFeedItem.Root ->
                                                    item.cluster.representative.createdAt
                                                is OverlayReactionLogFeedItem.ThreadParent ->
                                                    item.parent.representative.createdAt
                                                null -> null
                                            }
                                        }
                                        OverlayReactionLogDateHeader(
                                            label = formatChatDaySeparator(sampleCreatedAt),
                                        )
                                    }
                                    items(
                                        items = feedItems,
                                        key = { item ->
                                            when (item) {
                                                is OverlayReactionLogFeedItem.Root ->
                                                    "cluster-${item.cluster.representative.id}"
                                                is OverlayReactionLogFeedItem.ThreadParent ->
                                                    "thread-${item.parent.representative.id}"
                                            }
                                        },
                                        contentType = { item ->
                                            when (item) {
                                                is OverlayReactionLogFeedItem.Root -> 1
                                                is OverlayReactionLogFeedItem.ThreadParent -> 3
                                            }
                                        },
                                    ) { feedItem ->
                                        when (feedItem) {
                                            is OverlayReactionLogFeedItem.Root ->
                                                OverlayReactionLogFeedClusterRow(
                                                    cluster = feedItem.cluster,
                                                    selfUserId = selfUserId,
                                                    unreadEntryIds = unreadEntryIds,
                                                    animatedPreviewIds = animatedPreviewIds,
                                                    onlineUserIds = onlineUserIds,
                                                    newestUnreadEntryId = uiState.newestUnreadEntryId,
                                                    onPreviewCluster = { previewCluster = it },
                                                    onReplyToReactionLog = onReplyToReactionLog,
                                                    onToggleEmojiReaction = { id, emoji ->
                                                        repository.toggleLogEntryReaction(id, emoji)
                                                    },
                                                )
                                            is OverlayReactionLogFeedItem.ThreadParent ->
                                                OverlayReactionLogThreadParentClusterRow(
                                                    parent = feedItem.parent,
                                                    replies = feedItem.replies,
                                                    selfUserId = selfUserId,
                                                    unreadEntryIds = unreadEntryIds,
                                                    animatedPreviewIds = animatedPreviewIds,
                                                    onlineUserIds = onlineUserIds,
                                                    newestUnreadEntryId = uiState.newestUnreadEntryId,
                                                    onPreviewCluster = { previewCluster = it },
                                                    onReplyToReactionLog = onReplyToReactionLog,
                                                    onToggleEmojiReaction = { id, emoji ->
                                                        repository.toggleLogEntryReaction(id, emoji)
                                                    },
                                                )
                                        }
                                    }
                                }
                                if (loadingMore) {
                                    item(key = "loading-more", contentType = 2) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }

                        OverlayReactionLogJumpToUnreadFab(
                            visible = showJumpToUnread,
                            unreadCount = visibleUnreadCount,
                            onClick = {
                                if (firstUnreadIndex >= 0) {
                                    scope.launch {
                                        animateOverlayNotificationsListToIndex(listState, firstUnreadIndex)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .zIndex(6f),
                        )
                        OverlayReactionLogScrollToLatestFab(
                            visible = showScrollToLatest,
                            onClick = {
                                if (latestClusterIndex >= 0) {
                                    scope.launch {
                                        animateOverlayNotificationsListToIndex(listState, latestClusterIndex)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 8.dp, top = 8.dp)
                                .zIndex(6f),
                        )
                    }
                }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }

    previewCluster?.let { cluster ->
        OverlayReactionLogPreviewSheet(
            cluster = cluster,
            selfUserId = selfUserId,
            onDismiss = {
                previewCluster = null
                OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
            },
        )
    }

    if (showMarkAllReadConfirm) {
        OverlayAwareAlertDialog(
            onDismissRequest = {
                showMarkAllReadConfirm = false
                OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
            },
            title = {
                Text(
                    text = stringResource(R.string.overlay_notifications_mark_all_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.overlay_notifications_mark_all_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMarkAllReadConfirm = false
                        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
                        scope.launch {
                            markAllReadLoading = true
                            val ok = repository.markAllReadAwait()
                            markAllReadLoading = false
                            if (!ok) {
                                snackbarHostState.showSnackbar(markAllReadFailedText)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.overlay_notifications_mark_all_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMarkAllReadConfirm = false
                        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
                    },
                ) {
                    Text(stringResource(R.string.overlay_notifications_clear_cancel))
                }
            },
        )
    }

    if (showClearHistoryConfirm) {
        OverlayAwareAlertDialog(
            onDismissRequest = {
                showClearHistoryConfirm = false
                OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
            },
            title = {
                Text(
                    text = stringResource(R.string.overlay_notifications_clear_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.overlay_notifications_clear_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryConfirm = false
                        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
                        repository.clearHistoryForUser()
                    },
                ) {
                    Text(stringResource(R.string.overlay_notifications_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showClearHistoryConfirm = false
                        OverlayChatInteractionHold.cancelPreparedOverlayModalInteraction(true)
                    },
                ) {
                    Text(stringResource(R.string.overlay_notifications_clear_cancel))
                }
            },
        )
    }
}

private suspend fun scrollOverlayNotificationsListToIndex(
    listState: LazyListState,
    index: Int,
) {
    if (index < 0) return
    runCatching {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .filter { it > index }
            .first()
        listState.scrollToItem(index)
    }
}

@Composable
private fun OverlayReactionLogThreadParentClusterRow(
    parent: OverlayReactionLogCluster,
    replies: List<OverlayReactionLogCluster>,
    selfUserId: String,
    unreadEntryIds: Set<String>,
    animatedPreviewIds: Set<String>,
    onlineUserIds: Set<String>,
    newestUnreadEntryId: String?,
    onPreviewCluster: (OverlayReactionLogCluster) -> Unit,
    onReplyToReactionLog: (OverlayReactionLogEntry) -> Unit,
    onToggleEmojiReaction: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parentEntry = parent.representative
    val parentIncoming =
        OverlayReactionLogVisibilityPolicy.isIncoming(parentEntry, selfUserId)
    // Reply threads in notifications must be collapsed by default (user can expand explicitly).
    val defaultRepliesExpanded = false
    OverlayReactionLogReplyThreadFooter(
        parentLogId = parentEntry.id,
        replyCount = replies.size,
        defaultExpanded = defaultRepliesExpanded,
        incoming = parentIncoming,
        unreadHighlight = parentEntry.id in unreadEntryIds,
        modifier = modifier.fillMaxWidth(),
        cardContent = {
            OverlayReactionLogCard(
                incoming = parentIncoming,
                unreadHighlight = parentEntry.id in unreadEntryIds,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OverlayReactionLogEntryRow(
                    cluster = parent,
                    selfUserId = selfUserId,
                    unreadHighlight = parentEntry.id in unreadEntryIds,
                    playAnimatedPreview = parentEntry.id in animatedPreviewIds,
                    isOnline = parentEntry.senderUserId.trim() in onlineUserIds,
                    animateEnter = parentEntry.id == newestUnreadEntryId,
                    wrapInCard = false,
                    onPreviewClick = {
                        OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
                        onPreviewCluster(parent)
                    },
                    onQuickReply = if (
                        parentIncoming && !OverlayReactionLogReplyEnricher.isReplyEntry(parentEntry)
                    ) {
                        { onReplyToReactionLog(parentEntry) }
                    } else {
                        null
                    },
                    onToggleEmojiReaction = { emoji ->
                        onToggleEmojiReaction(parentEntry.id, emoji)
                    },
                )
            }
        },
        expandedContent = {
            replies.forEachIndexed { index, replyCluster ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                OverlayReactionLogFeedClusterRow(
                    cluster = replyCluster,
                    selfUserId = selfUserId,
                    unreadEntryIds = unreadEntryIds,
                    animatedPreviewIds = animatedPreviewIds,
                    onlineUserIds = onlineUserIds,
                    newestUnreadEntryId = newestUnreadEntryId,
                    onPreviewCluster = onPreviewCluster,
                    onReplyToReactionLog = onReplyToReactionLog,
                    onToggleEmojiReaction = onToggleEmojiReaction,
                )
            }
        },
    )
}

@Composable
private fun OverlayReactionLogFeedClusterRow(
    cluster: OverlayReactionLogCluster,
    selfUserId: String,
    unreadEntryIds: Set<String>,
    animatedPreviewIds: Set<String>,
    onlineUserIds: Set<String>,
    newestUnreadEntryId: String?,
    onPreviewCluster: (OverlayReactionLogCluster) -> Unit,
    onReplyToReactionLog: (OverlayReactionLogEntry) -> Unit,
    onToggleEmojiReaction: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = cluster.representative
    val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
    OverlayReactionLogEntryRow(
        cluster = cluster,
        selfUserId = selfUserId,
        unreadHighlight = entry.id in unreadEntryIds,
        playAnimatedPreview = entry.id in animatedPreviewIds,
        isOnline = entry.senderUserId.trim() in onlineUserIds,
        animateEnter = entry.id == newestUnreadEntryId,
        modifier = modifier,
        onPreviewClick = {
            OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
            onPreviewCluster(cluster)
        },
        onQuickReply = if (incoming && !OverlayReactionLogReplyEnricher.isReplyEntry(entry)) {
            { onReplyToReactionLog(entry) }
        } else {
            null
        },
        onToggleEmojiReaction = { emoji -> onToggleEmojiReaction(entry.id, emoji) },
    )
}

private suspend fun animateOverlayNotificationsListToIndex(
    listState: LazyListState,
    index: Int,
) {
    if (index < 0) return
    runCatching {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .filter { it > index }
            .first()
        listState.animateScrollToItem(index)
    }
}

@Composable
private fun EmptyNotificationsState(
    directionFilter: OverlayReactionLogFilter,
    onOpenReactionsPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Notifications,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when (directionFilter) {
                OverlayReactionLogFilter.Incoming ->
                    stringResource(R.string.overlay_notifications_empty_incoming)
                OverlayReactionLogFilter.Outgoing ->
                    stringResource(R.string.overlay_notifications_empty_outgoing)
                OverlayReactionLogFilter.All ->
                    stringResource(R.string.overlay_notifications_empty)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = onOpenReactionsPicker,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.overlay_notifications_open_reactions))
        }
    }
}
