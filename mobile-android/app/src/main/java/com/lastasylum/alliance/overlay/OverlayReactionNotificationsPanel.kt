package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogClusterPolicy
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogRepository
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces
import com.lastasylum.alliance.ui.util.OVERLAY_ONLINE_PANEL_POLL_MS
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverlayReactionNotificationsPanel(
    repository: OverlayReactionLogRepository,
    selfUserId: String,
    onClose: () -> Unit,
    onReplyToUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by repository.entries.collectAsState()
    val loading by repository.loading.collectAsState()
    val refreshing by repository.refreshing.collectAsState()
    val loadingMore by repository.loadingMore.collectAsState()
    val error by repository.error.collectAsState()
    val unreadCount by repository.unreadCount.collectAsState()
    var directionFilter by remember { mutableStateOf(OverlayReactionLogFilter.All) }
    var scopeFilter by remember { mutableStateOf(OverlayReactionLogScopeFilter.All) }
    var searchQuery by remember { mutableStateOf("") }
    var detailCluster by remember { mutableStateOf<OverlayReactionLogCluster?>(null) }
    var replySheetEntry by remember { mutableStateOf<OverlayReactionLogCluster?>(null) }
    var initialScrollDone by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(repository, selfUserId) {
        repository.setSelfUserId(selfUserId)
        repository.loadInitial()
    }

    LaunchedEffect(Unit) {
        val container = AppContainer.from(context)
        while (true) {
            runCatching {
                val ctx = OverlayTeamContextCache.load(
                    usersRepository = container.usersRepository,
                    teamsRepository = container.teamsRepository,
                ).getOrNull() ?: return@runCatching
                OverlayTeamContextCache.loadTeamDetail(
                    teamId = ctx.teamId,
                    teamsRepository = container.teamsRepository,
                )
                OverlayTeamPresenceCache.load(
                    teamId = ctx.teamId,
                    teamsRepository = container.teamsRepository,
                )
            }
            delay(OVERLAY_ONLINE_PANEL_POLL_MS)
        }
    }

    val filtered = remember(entries, directionFilter, scopeFilter, searchQuery, selfUserId) {
        entries
            .filter { OverlayReactionLogVisibilityPolicy.matchesFilter(it, selfUserId, directionFilter) }
            .filter { OverlayReactionLogVisibilityPolicy.matchesScopeFilter(it, scopeFilter) }
            .filter { OverlayReactionLogVisibilityPolicy.matchesSearchQuery(it, searchQuery) }
    }

    val clustered = remember(filtered, selfUserId) {
        OverlayReactionLogClusterPolicy.clusterEntries(filtered, selfUserId)
    }

    val grouped = remember(clustered) {
        clustered.groupBy { overlayReactionLogDateHeaderKey(it.representative.createdAt) }
            .toList()
            .sortedBy { (_, group) -> group.firstOrNull()?.representative?.id.orEmpty() }
            .map { (headerKey, groupClusters) ->
                headerKey to groupClusters.sortedBy { it.representative.id }
            }
    }

    val displayRows = remember(grouped, loadingMore) {
        buildOverlayReactionLogDisplayRows(grouped, loadingMore)
    }

    val firstUnreadIndex = remember(displayRows, unreadCount, selfUserId) {
        if (unreadCount <= 0) {
            -1
        } else {
            firstUnreadDisplayIndex(displayRows) { cluster ->
                repository.isEntryUnread(cluster.representative)
            }
        }
    }

    val lastClusterIndex = remember(displayRows) {
        lastClusterDisplayIndex(displayRows)
    }

    var lastHapticEntryId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entries.firstOrNull()?.id) {
        val newest = entries.firstOrNull() ?: return@LaunchedEffect
        if (newest.id != lastHapticEntryId && repository.isEntryUnread(newest)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            lastHapticEntryId = newest.id
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OverlayHudPanelHeader(
            title = stringResource(R.string.overlay_notifications_title),
            onClose = onClose,
            onRefresh = { repository.refresh() },
            refreshing = refreshing,
        )
        OverlayReactionLogFiltersBar(
            directionFilter = directionFilter,
            onDirectionFilter = { directionFilter = it },
            scopeFilter = scopeFilter,
            onScopeFilter = { scopeFilter = it },
            searchQuery = searchQuery,
            onSearchQuery = { searchQuery = it },
        )
        Box(
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
                clustered.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        EmptyNotificationsState(
                            directionFilter = directionFilter,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    val isNearBottom by remember(listState, lastClusterIndex) {
                        derivedStateOf {
                            if (lastClusterIndex < 0) return@derivedStateOf false
                            val info = listState.layoutInfo
                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                            lastVisible >= lastClusterIndex - 1
                        }
                    }
                    val isFirstUnreadVisible by remember(listState, firstUnreadIndex) {
                        derivedStateOf {
                            if (firstUnreadIndex < 0) return@derivedStateOf true
                            listState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadIndex }
                        }
                    }
                    val showJumpToUnread by remember(
                        unreadCount,
                        firstUnreadIndex,
                        isFirstUnreadVisible,
                        isNearBottom,
                    ) {
                        derivedStateOf {
                            unreadCount > 0 &&
                                firstUnreadIndex >= 0 &&
                                !isFirstUnreadVisible &&
                                !isNearBottom
                        }
                    }
                    val showScrollToLatest by remember(isNearBottom, displayRows.size) {
                        derivedStateOf {
                            displayRows.isNotEmpty() && !isNearBottom
                        }
                    }

                    LaunchedEffect(listState, displayRows.size) {
                        snapshotFlow { listState.firstVisibleItemIndex }
                            .distinctUntilChanged()
                            .collect { firstIndex ->
                                if (firstIndex <= 2) repository.loadMore()
                            }
                    }

                    LaunchedEffect(lastClusterIndex, loading, clustered.isNotEmpty()) {
                        if (!loading && lastClusterIndex >= 0 && !initialScrollDone) {
                            listState.scrollToItem(lastClusterIndex)
                            initialScrollDone = true
                            repository.markAllRead()
                        }
                    }

                    LaunchedEffect(isNearBottom) {
                        if (isNearBottom && clustered.isNotEmpty()) {
                            repository.markAllRead()
                        }
                    }

                    LaunchedEffect(listState, lastClusterIndex) {
                        snapshotFlow { isNearBottom }
                            .distinctUntilChanged()
                            .collect { nearBottom ->
                                if (nearBottom) {
                                    delay(500)
                                    if (isNearBottom) {
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
                            itemsIndexed(
                                items = displayRows,
                                key = { _, row ->
                                    when {
                                        row.cluster != null -> "cluster-${row.cluster.representative.id}"
                                        row.headerKey != null -> "header-${row.headerKey}"
                                        else -> "loading-more"
                                    }
                                },
                            ) { _, row ->
                                when {
                                    row.cluster == null && row.headerKey == null -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    row.headerKey != null -> {
                                        Text(
                                            text = overlayReactionLogDateHeaderLabel(row.headerKey),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(vertical = 4.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(PremiumSurfaces.layer1().copy(alpha = 0.35f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                    row.cluster != null -> {
                                        val cluster = row.cluster
                                        val entry = cluster.representative
                                        val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
                                        val unread = repository.isEntryUnread(entry)
                                        OverlayReactionLogSwipeRow(
                                            enabled = incoming,
                                            onReply = { onReplyToUser(entry.senderUserId) },
                                        ) {
                                            OverlayReactionLogEntryRow(
                                                cluster = cluster,
                                                selfUserId = selfUserId,
                                                unreadHighlight = unread,
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
                                                    {
                                                        OverlayChatInteractionHold
                                                            .prepareOverlayModalInteraction(true)
                                                        replySheetEntry = cluster
                                                    }
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
                        }
                    }

                    OverlayReactionLogJumpToUnreadFab(
                        visible = showJumpToUnread,
                        unreadCount = unreadCount,
                        onClick = {
                            if (firstUnreadIndex >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(firstUnreadIndex)
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
                                    listState.animateScrollToItem(lastClusterIndex)
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

    detailCluster?.let { cluster ->
        OverlayReactionLogDetailSheet(
            cluster = cluster,
            selfUserId = selfUserId,
            onDismiss = { detailCluster = null },
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
            },
        )
    }
}

@Composable
private fun EmptyNotificationsState(
    directionFilter: OverlayReactionLogFilter,
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
    }
}

@Composable
private fun OverlayReactionLogDetailSheet(
    cluster: OverlayReactionLogCluster,
    selfUserId: String,
    onDismiss: () -> Unit,
) {
    val entry = cluster.representative
    OverlayAwareBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (cluster.mergeCount > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    cluster.entries.forEach { item ->
                        OverlayReactionLogMiniPreview(
                            reactionId = item.reaction,
                            visibility = item.visibility,
                            previewSizeDp = 72,
                            showLabel = false,
                            playAnimatedPreview = true,
                            compact = false,
                        )
                    }
                }
            } else {
                OverlayReactionLogMiniPreview(
                    reactionId = entry.reaction,
                    visibility = entry.visibility,
                    previewSizeDp = 140,
                    showLabel = false,
                    playAnimatedPreview = true,
                    compact = false,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = overlayReactionLogNarrative(entry, selfUserId, includeSenderName = true),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            val timeLine = formatOverlayReactionLogTimeLabel(entry.createdAt)
            if (timeLine.isNotBlank()) {
                Text(
                    text = timeLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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
