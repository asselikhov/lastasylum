package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogFeedItem
import com.lastasylum.alliance.data.chat.OverlayReactionLogFilter
import com.lastasylum.alliance.data.chat.OverlayReactionLogRepository
import com.lastasylum.alliance.data.chat.OverlayReactionLogScopeFilter
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.util.OVERLAY_ONLINE_PANEL_POLL_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val SEARCH_DEBOUNCE_MS = 300L

data class OverlayReactionNotificationsRepositoryUi(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val unreadCount: Int = 0,
    val unreadEntryIds: Set<String> = emptySet(),
)

data class OverlayReactionNotificationsUiState(
    val directionFilter: OverlayReactionLogFilter = OverlayReactionLogFilter.All,
    val scopeFilter: OverlayReactionLogScopeFilter = OverlayReactionLogScopeFilter.All,
    val searchQuery: String = "",
    val debouncedSearch: String = "",
    val filterKey: String = "",
    val groupedFeed: List<Pair<String, List<OverlayReactionLogFeedItem>>> = emptyList(),
    val clustered: List<OverlayReactionLogCluster> = emptyList(),
    val listLayout: OverlayReactionLogStickyListLayout = OverlayReactionLogStickyListLayout(
        groupedFeed = emptyList(),
        firstUnreadItemIndex = -1,
        latestClusterItemIndex = -1,
        itemIndexToEntryId = emptyMap(),
    ),
    val onlineUserIds: Set<String> = emptySet(),
    val newestUnreadEntryId: String? = null,
    val newestFeedEntryIds: List<String> = emptyList(),
)

class OverlayReactionNotificationsController(
    private val scope: CoroutineScope,
    private val repository: OverlayReactionLogRepository,
    private val usersRepository: UsersRepository,
    private val teamsRepository: TeamsRepository,
) {
    private val _uiState = MutableStateFlow(OverlayReactionNotificationsUiState())
    val uiState: StateFlow<OverlayReactionNotificationsUiState> = _uiState.asStateFlow()

    private val _repositoryUi = MutableStateFlow(OverlayReactionNotificationsRepositoryUi())
    val repositoryUi: StateFlow<OverlayReactionNotificationsRepositoryUi> = _repositoryUi.asStateFlow()

    private var selfUserId: String = ""
    private var lastListRebuildKey: ListRebuildKey? = null
    private var started = false
    private var pollJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var combineJob: Job? = null
    private var repositoryUiJob: Job? = null

    private data class ListRebuildKey(
        val entryIds: List<String>,
        val unreadEntryIds: Set<String>,
        val directionFilter: OverlayReactionLogFilter,
        val scopeFilter: OverlayReactionLogScopeFilter,
        val debouncedSearch: String,
    )

    fun start(userId: String) {
        selfUserId = userId.trim()
        repository.setSelfUserId(selfUserId)
        if (started) {
            rebuildFromRepositorySnapshot()
            return
        }
        started = true
        repository.loadInitial()
        startPresencePolling()
        repositoryUiJob = scope.launch {
            combine(
                combine(
                    repository.loading,
                    repository.refreshing,
                    repository.loadingMore,
                ) { loading, refreshing, loadingMore ->
                    Triple(loading, refreshing, loadingMore)
                },
                combine(
                    repository.error,
                    repository.unreadCount,
                    repository.unreadEntryIds,
                ) { error, unreadCount, unreadEntryIds ->
                    Triple(error, unreadCount, unreadEntryIds)
                },
            ) { loadingTriple, unreadTriple ->
                OverlayReactionNotificationsRepositoryUi(
                    loading = loadingTriple.first,
                    refreshing = loadingTriple.second,
                    loadingMore = loadingTriple.third,
                    error = unreadTriple.first,
                    unreadCount = unreadTriple.second,
                    unreadEntryIds = unreadTriple.third,
                )
            }.collect { _repositoryUi.value = it }
        }
        combineJob = scope.launch {
            combine(
                repository.entries,
                repository.unreadEntryIds,
                repository.loadingMore,
            ) { entries, unreadIds, loadingMore ->
                rebuildListState(entries, unreadIds, loadingMore)
            }.collect { /* driven by repository emissions */ }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        pollJob?.cancel()
        pollJob = null
        searchDebounceJob?.cancel()
        searchDebounceJob = null
        combineJob?.cancel()
        combineJob = null
        repositoryUiJob?.cancel()
        repositoryUiJob = null
        lastListRebuildKey = null
        OverlayReactionTilePreviewPool.clear()
    }

    fun onDirectionFilter(filter: OverlayReactionLogFilter) {
        _uiState.update { it.copy(directionFilter = filter) }
        rebuildFromRepositorySnapshot()
    }

    fun onScopeFilter(filter: OverlayReactionLogScopeFilter) {
        _uiState.update { it.copy(scopeFilter = filter) }
        rebuildFromRepositorySnapshot()
    }

    fun onSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(debouncedSearch = query) }
            rebuildFromRepositorySnapshot()
        }
    }

    fun loadMoreIfNeeded(lastVisibleItemIndex: Int, totalItemCount: Int) {
        if (totalItemCount <= 0) return
        if (lastVisibleItemIndex >= totalItemCount - 3) {
            repository.loadMore()
        }
    }

    private fun startPresencePolling() {
        pollJob = scope.launch {
            while (isActive && started) {
                runCatching {
                    val ctx = OverlayTeamContextCache.load(
                        usersRepository = usersRepository,
                        teamsRepository = teamsRepository,
                    ).getOrNull() ?: return@runCatching
                    OverlayTeamContextCache.loadTeamDetail(
                        teamId = ctx.teamId,
                        teamsRepository = teamsRepository,
                    )
                    OverlayTeamPresenceCache.load(
                        teamId = ctx.teamId,
                        teamsRepository = teamsRepository,
                    )
                }
                rebuildFromRepositorySnapshot()
                delay(OVERLAY_ONLINE_PANEL_POLL_MS)
            }
        }
    }

    private fun rebuildFromRepositorySnapshot() {
        rebuildListState(
            entries = repository.entries.value,
            unreadEntryIds = repository.unreadEntryIds.value,
            loadingMore = repository.loadingMore.value,
        )
    }

    private fun rebuildListState(
        entries: List<OverlayReactionLogEntry>,
        unreadEntryIds: Set<String>,
        loadingMore: Boolean,
    ) {
        val state = _uiState.value
        if (selfUserId.isEmpty()) return

        val rebuildKey = ListRebuildKey(
            entryIds = entries.map { it.id },
            unreadEntryIds = unreadEntryIds,
            directionFilter = state.directionFilter,
            scopeFilter = state.scopeFilter,
            debouncedSearch = state.debouncedSearch,
        )
        val previousKey = lastListRebuildKey
        if (
            previousKey != null &&
            previousKey == rebuildKey &&
            _uiState.value.groupedFeed.isNotEmpty()
        ) {
            val listLayout = buildStickyListLayout(_uiState.value.groupedFeed, loadingMore, unreadEntryIds)
            _uiState.update { it.copy(listLayout = listLayout) }
            return
        }
        lastListRebuildKey = rebuildKey

        val (clustered, groupedFeed) = OverlayReactionNotificationsDeriver.buildGroupedFeed(
            entries = entries,
            selfUserId = selfUserId,
            directionFilter = state.directionFilter,
            scopeFilter = state.scopeFilter,
            searchQuery = state.debouncedSearch,
        )
        val listLayout = buildStickyListLayout(groupedFeed, loadingMore, unreadEntryIds)
        val filterKey = OverlayReactionNotificationsDeriver.filterKey(
            state.directionFilter,
            state.scopeFilter,
            state.debouncedSearch,
        )
        val onlineUserIds = OverlayReactionNotificationsDeriver.resolveOnlineUserIds(clustered)
        val newestUnread = entries.firstOrNull()?.id?.takeIf { it in unreadEntryIds }
        val newestFeedEntryIds = buildNewestFeedEntryIds(groupedFeed)

        _uiState.update {
            it.copy(
                filterKey = filterKey,
                groupedFeed = groupedFeed,
                clustered = clustered,
                listLayout = listLayout,
                onlineUserIds = onlineUserIds,
                newestUnreadEntryId = newestUnread,
                newestFeedEntryIds = newestFeedEntryIds,
            )
        }
    }
}
