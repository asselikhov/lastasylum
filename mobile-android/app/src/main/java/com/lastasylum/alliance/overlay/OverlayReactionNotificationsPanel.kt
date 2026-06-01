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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogRepository
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverlayReactionNotificationsPanel(
    repository: OverlayReactionLogRepository,
    selfUserId: String,
    onClose: () -> Unit,
    onReplyToUser: (userId: String) -> Unit,
    onOpenReactionsPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Overlay ComposeView: explicit owner matches CombatOverlayService HUD/chat pattern.
    val lifecycleOwner = LocalLifecycleOwner.current
    val entries by repository.entries.collectAsStateWithLifecycle(lifecycleOwner)
    val loading by repository.loading.collectAsStateWithLifecycle(lifecycleOwner)
    val refreshing by repository.refreshing.collectAsStateWithLifecycle(lifecycleOwner)
    val loadingMore by repository.loadingMore.collectAsStateWithLifecycle(lifecycleOwner)
    val error by repository.error.collectAsStateWithLifecycle(lifecycleOwner)
    val unreadCount by repository.unreadCount.collectAsStateWithLifecycle(lifecycleOwner)
    val unreadEntryIds by repository.unreadEntryIds.collectAsStateWithLifecycle(lifecycleOwner)
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
    val clustered = uiState.clustered
    val grouped = uiState.grouped
    val listLayout = uiState.listLayout
    val onlineUserIds = uiState.onlineUserIds
    val filterKey = uiState.filterKey
    var detailCluster by remember { mutableStateOf<OverlayReactionLogCluster?>(null) }
    var replySheetEntry by remember { mutableStateOf<OverlayReactionLogCluster?>(null) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var showHeaderMenu by remember { mutableStateOf(false) }
    var initialScrollDone by remember(filterKey) { mutableStateOf(false) }
    var hapticConsumedForSession by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(controller, selfUserId) {
        controller.start(selfUserId)
        onDispose { controller.stop() }
    }

    var savedScrollIndex by rememberSaveable(filterKey) { mutableIntStateOf(0) }
    val listState = rememberLazyListState(
        cacheWindow = LazyLayoutCacheWindow(ahead = 140.dp, behind = 140.dp),
        initialFirstVisibleItemIndex = savedScrollIndex,
    )

    val firstUnreadIndex = listLayout.firstUnreadItemIndex
    val lastClusterIndex = listLayout.lastClusterItemIndex

    val isNearBottom by remember(listState, lastClusterIndex) {
        derivedStateOf {
            if (lastClusterIndex < 0) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= lastClusterIndex - 1
        }
    }
    val isFirstUnreadVisible by remember(listState, firstUnreadIndex) {
        derivedStateOf {
            if (firstUnreadIndex < 0) return@derivedStateOf true
            listState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadIndex }
        }
    }
    val showJumpToUnread by remember(unreadCount, firstUnreadIndex, isFirstUnreadVisible, isNearBottom) {
        derivedStateOf {
            unreadCount > 0 && firstUnreadIndex >= 0 && !isFirstUnreadVisible && !isNearBottom
        }
    }
    val showScrollToLatest by remember(isNearBottom, grouped) {
        derivedStateOf { grouped.isNotEmpty() && !isNearBottom }
    }
    val animatedPreviewIds by remember(listState, listLayout.itemIndexToEntryId) {
        derivedStateOf {
            OverlayReactionPreviewAnimationPolicy.resolveAnimatedEntryIds(
                visibleItems = listState.layoutInfo.visibleItemsInfo,
                itemIndexToEntryId = listLayout.itemIndexToEntryId,
                layoutInfo = listState.layoutInfo,
            )
        }
    }

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

    LaunchedEffect(listState.isScrollInProgress, filterKey) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    savedScrollIndex = listState.firstVisibleItemIndex
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OverlayHudPanelHeader(
                title = stringResource(R.string.overlay_notifications_title),
                subtitle = stringResource(R.string.overlay_notifications_subtitle),
                onClose = onClose,
                subtitleTrailing = {
                    if (unreadCount > 0) {
                        Badge {
                            Text(
                                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            )
                        }
                    }
                },
                headerTrailing = {
                    IconButton(
                        onClick = { showHeaderMenu = true },
                        enabled = !loading,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(
                                R.string.overlay_notifications_menu_clear_history,
                            ),
                        )
                    }
                    DropdownMenu(
                        expanded = showHeaderMenu,
                        onDismissRequest = { showHeaderMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.overlay_notifications_menu_clear_history))
                            },
                            onClick = {
                                showHeaderMenu = false
                                OverlayChatInteractionHold.prepareOverlayModalInteraction(true)
                                showClearHistoryConfirm = true
                            },
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val compactLayout = maxWidth < 340.dp
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
                    clustered.isEmpty() -> {
                        EmptyNotificationsState(
                            directionFilter = uiState.directionFilter,
                            onOpenReactionsPicker = onOpenReactionsPicker,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        LaunchedEffect(listState, listLayout.itemIndexToEntryId.size) {
                            snapshotFlow { listState.firstVisibleItemIndex }
                                .distinctUntilChanged()
                                .collect { firstIndex ->
                                    controller.loadMoreIfNeeded(firstIndex)
                                }
                        }

                        LaunchedEffect(lastClusterIndex, loading, clustered.isNotEmpty(), filterKey) {
                            if (!loading && lastClusterIndex >= 0 && !initialScrollDone && clustered.isNotEmpty()) {
                                scrollOverlayNotificationsListToIndex(listState, lastClusterIndex)
                                initialScrollDone = true
                                repository.markAllRead()
                            }
                        }

                        LaunchedEffect(listState, lastClusterIndex, clustered.isNotEmpty()) {
                            snapshotFlow { listState.isScrollInProgress to isNearBottom }
                                .distinctUntilChanged()
                                .collect { (scrolling, nearBottom) ->
                                    if (!scrolling && nearBottom && clustered.isNotEmpty()) {
                                        delay(300)
                                        if (!listState.isScrollInProgress && isNearBottom) {
                                            repository.markAllRead()
                                        }
                                    }
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
                                listLayout.grouped.forEach { (headerKey, clusters) ->
                                    stickyHeader(key = "header-$headerKey", contentType = 0) {
                                        OverlayReactionLogDateHeader(
                                            label = overlayReactionLogDateHeaderLabel(headerKey),
                                        )
                                    }
                                    items(
                                        items = clusters,
                                        key = { "cluster-${it.representative.id}" },
                                        contentType = { 1 },
                                    ) { cluster ->
                                        val entry = cluster.representative
                                        val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(
                                            entry,
                                            selfUserId,
                                        )
                                        OverlayReactionLogEntryRow(
                                            cluster = cluster,
                                            selfUserId = selfUserId,
                                            unreadHighlight = entry.id in unreadEntryIds,
                                            compactLayout = compactLayout,
                                            playAnimatedPreview = entry.id in animatedPreviewIds,
                                            isOnline = entry.senderUserId.trim() in onlineUserIds,
                                            animateEnter = entry.id == uiState.newestUnreadEntryId,
                                            onClick = { detailCluster = cluster },
                                            onLongClick = if (incoming) {
                                                {
                                                    OverlayChatInteractionHold
                                                        .prepareOverlayModalInteraction(true)
                                                    replySheetEntry = cluster
                                                }
                                            } else {
                                                null
                                            },
                                            onQuickReply = if (incoming) {
                                                { onReplyToUser(entry.senderUserId) }
                                            } else {
                                                null
                                            },
                                            onToggleEmojiReaction = { emoji ->
                                                repository.toggleLogEntryReaction(entry.id, emoji)
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        OverlayReactionLogJumpToUnreadFab(
                            visible = showJumpToUnread,
                            unreadCount = unreadCount,
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
                                if (lastClusterIndex >= 0) {
                                    scope.launch {
                                        animateOverlayNotificationsListToIndex(listState, lastClusterIndex)
                                        repository.markAllRead()
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 12.dp)
                                .zIndex(6f),
                        )
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

    detailCluster?.let { cluster ->
        val entry = cluster.representative
        val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
        OverlayReactionLogDetailSheet(
            cluster = cluster,
            selfUserId = selfUserId,
            onDismiss = { detailCluster = null },
            onReplyToUser = if (incoming) onReplyToUser else null,
            onToggleEmojiReaction = { emoji ->
                repository.toggleLogEntryReaction(entry.id, emoji)
            },
        )
    }

    replySheetEntry?.let { cluster ->
        val entry = cluster.representative
        OverlayReactionLogReplySheet(
            entry = entry,
            selfUserId = selfUserId,
            onDismiss = { replySheetEntry = null },
            onToggleEmoji = { emoji ->
                repository.toggleLogEntryReaction(entry.id, emoji)
                replySheetEntry = null
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

@Composable
private fun overlayReactionLogDateHeaderLabel(headerKey: String): String = when (headerKey) {
    "today" -> stringResource(R.string.overlay_notifications_date_today)
    "yesterday" -> stringResource(R.string.overlay_notifications_date_yesterday)
    else -> runCatching {
        LocalDate.parse(headerKey).format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
    }.getOrDefault(headerKey)
}
