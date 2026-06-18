package com.lastasylum.alliance.ui.screens.teamnews

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lastasylum.alliance.R
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.data.teams.TeamNewsMarkRead
import com.lastasylum.alliance.data.teams.TeamNewsReadCursorSync
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.overlay.LocalOverlayUiMode
import com.lastasylum.alliance.overlay.OverlayReactionLogJumpToUnreadFab
import com.lastasylum.alliance.ui.components.team.PremiumJournalFeedTokens
import com.lastasylum.alliance.ui.components.team.TeamNewsFeedCard
import com.lastasylum.alliance.ui.components.team.journal.JournalEmptyState
import com.lastasylum.alliance.ui.components.team.journal.JournalListSkeleton
import com.lastasylum.alliance.ui.theme.SquadRelayDimens
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
internal fun TeamNewsListScreen(
    teamId: String,
    currentUserId: String,
    canPublishNews: Boolean,
    teamsRepository: TeamsRepository,
    sectionActive: Boolean = true,
    refreshNonce: Int = 0,
    onOpenDetail: (String) -> Unit,
    onCreate: () -> Unit,
    onNewsInboxChanged: () -> Unit = {},
    onProvideMarkReadAction: (String, (() -> Unit)?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val res = context.resources
    val overlayUi = LocalOverlayUiMode.current
    val scope = rememberCoroutineScope()
    val app = remember(context) { AppContainer.from(context.applicationContext) }
    val newsPrefs = remember(app) { app.userSettingsPreferences }
    val launchDiskCache = remember(app) { app.launchDiskCache }
    val cachedPage = remember(teamId, currentUserId) {
        if (currentUserId.isNotBlank()) {
            launchDiskCache.loadTeamNews(currentUserId, teamId)
        } else {
            null
        }
    }
    var loading by remember { mutableStateOf(cachedPage == null) }
    var newsItems by remember {
        mutableStateOf(TeamInboxUnread.sortNewsFeedNewestFirst(cachedPage?.items ?: emptyList()))
    }
    var newsNextCursor by remember { mutableStateOf(cachedPage?.nextCursor) }
    var loadingMore by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    suspend fun loadNewsPage(cursor: String?, append: Boolean, silent: Boolean = false) {
        if (append) loadingMore = true else if (!silent) loading = true
        if (!append) loadError = null
        teamsRepository.listTeamNews(teamId, cursor, limit = 40)
            .onSuccess { page ->
                val merged = if (append) newsItems + page.items else page.items
                newsItems = TeamInboxUnread.sortNewsFeedNewestFirst(merged)
                newsNextCursor = page.nextCursor
                if (!append && currentUserId.isNotBlank()) {
                    launchDiskCache.saveTeamNews(
                        currentUserId,
                        teamId,
                        page.copy(items = newsItems),
                    )
                }
                loading = false
                loadingMore = false
            }
            .onFailure { e ->
                if (!append) loadError = e.toUserMessageRu(res)
                loading = false
                loadingMore = false
            }
    }

    LaunchedEffect(teamId, sectionActive) {
        if (!sectionActive) return@LaunchedEffect
        loadNewsPage(
            cursor = null,
            append = false,
            silent = cachedPage != null,
        )
    }

    LaunchedEffect(teamId, refreshNonce, sectionActive) {
        if (!sectionActive || refreshNonce <= 0) return@LaunchedEffect
        loadNewsPage(cursor = null, append = false, silent = true)
    }

    LaunchedEffect(teamId) {
        onProvideMarkReadAction("list") {
            scope.launch {
                TeamNewsMarkRead.markAllNewsRead(
                    teamsRepository = teamsRepository,
                    prefs = newsPrefs,
                    teamId = teamId,
                    currentUserId = currentUserId,
                )
                onNewsInboxChanged()
                loadNewsPage(cursor = null, append = false, silent = true)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { onProvideMarkReadAction("list", null) }
    }

    val unreadNewsIds = remember(newsItems, currentUserId) {
        val currentUser = currentUserId.trim()
        val lastSeen = newsPrefs.getLastSeenTeamNewsCreatedAt(teamId)
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (lastSeen == null) {
            newsItems.asSequence()
                .filterNot { item -> currentUser.isNotBlank() && item.authorUserId.trim() == currentUser }
                .map { it.id }
                .toSet()
        } else {
            newsItems.asSequence()
                .filterNot { item -> currentUser.isNotBlank() && item.authorUserId.trim() == currentUser }
                .filter { item ->
                    runCatching { Instant.parse(item.createdAt) }.getOrNull()?.isAfter(lastSeen) == true
                }
                .map { it.id }
                .toSet()
        }
    }
    val visibleUnreadCount = unreadNewsIds.size
    val newsListState = rememberLazyListState(
        initialFirstVisibleItemIndex = 0,
        initialFirstVisibleItemScrollOffset = 0,
    )
    var overlayListAnchored by remember(teamId) { mutableStateOf(false) }
    LaunchedEffect(overlayUi, teamId, loading, newsItems.size) {
        if (!overlayUi || overlayListAnchored || loading || newsItems.isEmpty()) return@LaunchedEffect
        newsListState.scrollToItem(0)
        overlayListAnchored = true
    }
    val firstUnreadIndex = remember(newsItems, unreadNewsIds) {
        if (unreadNewsIds.isEmpty()) -1
        else newsItems.indexOfLast { it.id in unreadNewsIds }
    }
    val isFirstUnreadVisible by remember(newsListState, firstUnreadIndex) {
        derivedStateOf {
            if (firstUnreadIndex < 0) return@derivedStateOf true
            newsListState.layoutInfo.visibleItemsInfo.any { it.index == firstUnreadIndex }
        }
    }
    val showJumpToUnread by remember(overlayUi, visibleUnreadCount, firstUnreadIndex, isFirstUnreadVisible) {
        derivedStateOf {
            overlayUi &&
                visibleUnreadCount > 0 &&
                firstUnreadIndex >= 0 &&
                !isFirstUnreadVisible
        }
    }
    val markNewsSeenUpToRef = rememberUpdatedState { createdAt: String ->
        TeamNewsReadCursorSync.markNewsSeenUpTo(
            teamsRepository = teamsRepository,
            prefs = newsPrefs,
            teamId = teamId,
            createdAt = createdAt,
        )
    }
    val unreadNewsIdsRef = rememberUpdatedState(unreadNewsIds)
    val newsItemsRef = rememberUpdatedState(newsItems)
    LaunchedEffect(newsListState, overlayUi, newsItems.size) {
        if (!overlayUi) return@LaunchedEffect
        snapshotFlow { newsListState.layoutInfo.visibleItemsInfo.map { it.index } }
            .debounce(140)
            .map { indices ->
                indices.mapNotNull { newsItemsRef.value.getOrNull(it) }
                    .filter { it.id in unreadNewsIdsRef.value }
                    .mapNotNull { item ->
                        runCatching { Instant.parse(item.createdAt.trim()) }.getOrNull()
                            ?.let { parsed -> item.createdAt.trim() to parsed }
                    }
                    .maxByOrNull { it.second }
                    ?.first
            }
            .distinctUntilChanged()
            .collect { newestSeen ->
                val iso = newestSeen ?: return@collect
                markNewsSeenUpToRef.value(iso)
            }
    }
    if (overlayUi) {
        DisposableEffect(teamId) {
            onDispose {
                scope.launch {
                    TeamNewsReadCursorSync.flushPendingNewsCursor(
                        teamsRepository = teamsRepository,
                        prefs = newsPrefs,
                        teamId = teamId,
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when {
                loading && newsItems.isEmpty() -> {
                    JournalListSkeleton(modifier = Modifier.align(Alignment.TopCenter))
                }
                loadError != null && newsItems.isEmpty() -> {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(SquadRelayDimens.itemGap),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        ) {
                            Text(
                                text = loadError ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch { loadNewsPage(cursor = null, append = false) }
                            },
                        ) {
                            Text(stringResource(R.string.team_news_retry))
                        }
                    }
                }
                newsItems.isEmpty() -> {
                    JournalEmptyState(
                        message = stringResource(R.string.team_news_empty),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    val listTopPad = if (overlayUi) 0.dp else 8.dp
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = newsListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = SquadRelayDimens.contentPaddingHorizontal,
                                end = SquadRelayDimens.contentPaddingHorizontal,
                                top = listTopPad,
                                bottom = 88.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(PremiumJournalFeedTokens.listSpacing),
                        ) {
                            items(
                                items = newsItems,
                                key = { it.id },
                                contentType = { "team_news" },
                            ) { item ->
                                TeamNewsFeedCard(
                                    item = item,
                                    isUnread = item.id in unreadNewsIds,
                                    onClick = { onOpenDetail(item.id) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(PremiumJournalFeedTokens.listEnterAnimMs),
                                        placementSpec = tween(PremiumJournalFeedTokens.listEnterAnimMs),
                                        fadeOutSpec = tween(PremiumJournalFeedTokens.listEnterAnimMs),
                                    ),
                                )
                            }
                            if (!newsNextCursor.isNullOrBlank()) {
                                item {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                loadNewsPage(newsNextCursor, append = true)
                                            }
                                        },
                                        enabled = !loadingMore,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (loadingMore) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.padding(end = 8.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        }
                                        Text(
                                            if (loadingMore) {
                                                stringResource(R.string.team_news_loading_more)
                                            } else {
                                                stringResource(R.string.team_news_load_more)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (overlayUi) {
                            OverlayReactionLogJumpToUnreadFab(
                                visible = showJumpToUnread,
                                unreadCount = visibleUnreadCount,
                                onClick = {
                                    if (firstUnreadIndex >= 0) {
                                        scope.launch {
                                            newsListState.animateScrollToItem(firstUnreadIndex)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .zIndex(6f),
                            )
                        }
                    }
                }
            }
            if (canPublishNews) {
                FloatingActionButton(
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.team_news_new))
                }
            }
        }
    }
}
