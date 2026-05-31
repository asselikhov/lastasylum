package com.lastasylum.alliance.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogClusterPolicy
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogRepository
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibilityPolicy
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.theme.premium.PremiumSurfaces
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OverlayReactionNotificationsPanel(
    repository: OverlayReactionLogRepository,
    selfUserId: String,
    onClose: () -> Unit,
    onReplyToUser: (userId: String) -> Unit,
    onQuickReplyToUser: (userId: String) -> Unit,
    onOpenParticipants: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by repository.entries.collectAsState()
    val loading by repository.loading.collectAsState()
    val refreshing by repository.refreshing.collectAsState()
    val loadingMore by repository.loadingMore.collectAsState()
    val error by repository.error.collectAsState()
    val lastSeenLogId by repository.lastSeenLogId.collectAsState()
    var directionFilter by remember { mutableStateOf(OverlayReactionLogFilter.All) }
    var scopeFilter by remember { mutableStateOf(OverlayReactionLogScopeFilter.All) }
    var searchQuery by remember { mutableStateOf("") }
    var detailCluster by remember { mutableStateOf<OverlayReactionLogCluster?>(null) }
    var contextCluster by remember { mutableStateOf<OverlayReactionLogCluster?>(null) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(repository, selfUserId) {
        repository.setSelfUserId(selfUserId)
        repository.loadInitial()
        repository.markAllRead()
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
            .sortedByDescending { (_, group) -> group.firstOrNull()?.representative?.id.orEmpty() }
    }

    var lastHapticEntryId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entries.firstOrNull()?.id, lastSeenLogId) {
        val newest = entries.firstOrNull() ?: return@LaunchedEffect
        if (newest.id != lastHapticEntryId && repository.isEntryUnread(newest)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            lastHapticEntryId = newest.id
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OverlayHudPanelHeader(
            title = stringResource(R.string.overlay_notifications_title),
            subtitle = stringResource(R.string.overlay_notifications_subtitle),
            onClose = onClose,
            onRefresh = { repository.refresh() },
            refreshing = refreshing,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OverlayReactionLogFilter.entries.forEach { chip ->
                FilterChip(
                    selected = directionFilter == chip,
                    onClick = { directionFilter = chip },
                    label = {
                        Text(
                            when (chip) {
                                OverlayReactionLogFilter.All ->
                                    stringResource(R.string.overlay_notifications_filter_all)
                                OverlayReactionLogFilter.Incoming ->
                                    stringResource(R.string.overlay_notifications_filter_incoming)
                                OverlayReactionLogFilter.Outgoing ->
                                    stringResource(R.string.overlay_notifications_filter_outgoing)
                            },
                        )
                    },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OverlayReactionLogScopeFilter.entries.forEach { chip ->
                FilterChip(
                    selected = scopeFilter == chip,
                    onClick = { scopeFilter = chip },
                    label = {
                        Text(
                            when (chip) {
                                OverlayReactionLogScopeFilter.All ->
                                    stringResource(R.string.overlay_notifications_scope_all)
                                OverlayReactionLogScopeFilter.Personal ->
                                    stringResource(R.string.overlay_reaction_burst_caption_private)
                                OverlayReactionLogScopeFilter.Broadcast ->
                                    stringResource(R.string.overlay_reaction_burst_caption_broadcast)
                            },
                        )
                    },
                )
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SquadRelayDimens.contentPaddingHorizontal, vertical = 4.dp),
            singleLine = true,
            placeholder = {
                Text(stringResource(R.string.overlay_notifications_search_hint))
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF3A4555),
            ),
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
                    LaunchedEffect(listState, grouped.size) {
                        snapshotFlow {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                            last >= info.totalItemsCount - 3
                        }
                            .distinctUntilChanged()
                            .collect { nearEnd ->
                                if (nearEnd) repository.loadMore()
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
                                horizontal = SquadRelayDimens.contentPaddingHorizontal,
                                vertical = 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            grouped.forEach { (headerKey, groupClusters) ->
                                stickyHeader(key = "header-$headerKey") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(PremiumSurfaces.layer1())
                                            .padding(vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = overlayReactionLogDateHeaderLabel(headerKey),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                items(groupClusters, key = { it.representative.id }) { cluster ->
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
                                            onReply = { onReplyToUser(entry.senderUserId) },
                                            onQuickReply = { onQuickReplyToUser(entry.senderUserId) },
                                            onClick = { detailCluster = cluster },
                                            onLongClick = { contextCluster = cluster },
                                        )
                                    }
                                }
                            }
                            if (loadingMore) {
                                item(key = "loading-more") {
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
                }
            }
        }
    }

    detailCluster?.let { cluster ->
        OverlayReactionLogDetailSheet(
            cluster = cluster,
            selfUserId = selfUserId,
            onDismiss = { detailCluster = null },
            onReply = {
                detailCluster = null
                onReplyToUser(cluster.representative.senderUserId)
            },
            onQuickReply = {
                onQuickReplyToUser(cluster.representative.senderUserId)
            },
        )
    }

    contextCluster?.let { cluster ->
        val entry = cluster.representative
        val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
        OverlayAwareBottomSheet(onDismissRequest = { contextCluster = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                if (incoming) {
                    TextButton(
                        onClick = {
                            contextCluster = null
                            onReplyToUser(entry.senderUserId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.overlay_notifications_reply))
                    }
                    TextButton(
                        onClick = {
                            contextCluster = null
                            onQuickReplyToUser(entry.senderUserId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE57373))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.overlay_notifications_quick_reply))
                    }
                }
                TextButton(
                    onClick = {
                        contextCluster = null
                        onOpenParticipants()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.overlay_notifications_open_participants))
                }
            }
        }
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
    onReply: () -> Unit,
    onQuickReply: () -> Unit,
) {
    val entry = cluster.representative
    val incoming = OverlayReactionLogVisibilityPolicy.isIncoming(entry, selfUserId)
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
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    cluster.entries.take(4).forEach { item ->
                        OverlayReactionLogMiniPreview(
                            reactionId = item.reaction,
                            visibility = item.visibility,
                            previewSizeDp = 72,
                        )
                    }
                }
                if (cluster.mergeCount > 4) {
                    Text(
                        text = stringResource(
                            R.string.overlay_notifications_cluster_more,
                            cluster.mergeCount - 4,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                OverlayReactionLogMiniPreview(
                    reactionId = entry.reaction,
                    visibility = entry.visibility,
                    previewSizeDp = 112,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = overlayReactionLogNarrative(entry, selfUserId, includeSenderName = true),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            val (absolute, relative) = formatOverlayReactionLogTimeLine(entry.createdAt)
            if (absolute.isNotBlank()) {
                Text(
                    text = listOf(absolute, relative).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (incoming) {
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onQuickReply, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE57373))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.overlay_notifications_quick_reply))
                }
                TextButton(onClick = onReply, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.overlay_notifications_reply))
                }
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
