package com.lastasylum.alliance.data.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OverlayReactionLogRepository(
    private val chatApi: ChatApi,
    private val preferences: OverlayReactionLogPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<OverlayReactionLogEntry>>(emptyList())
    val entries: StateFlow<List<OverlayReactionLogEntry>> = _entries.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _unreadEntryIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadEntryIds: StateFlow<Set<String>> = _unreadEntryIds.asStateFlow()

    private val _lastSeenLogId = MutableStateFlow<String?>(null)
    val lastSeenLogId: StateFlow<String?> = _lastSeenLogId.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile
    private var nextCursor: String? = null

    @Volatile
    private var selfUserId: String = ""

    fun setSelfUserId(userId: String) {
        selfUserId = userId.trim()
        scope.launch { recomputeUnread() }
    }

    fun isEntryUnread(entry: OverlayReactionLogEntry): Boolean =
        OverlayReactionLogVisibilityPolicy.isEntryUnread(
            entry = entry,
            selfUserId = selfUserId,
            lastSeenLogId = _lastSeenLogId.value,
        )

    fun insertFromSocket(dto: OverlayReactionLogEntryDto) {
        val entry = dto.toEntry(selfUserId) ?: return
        if (isEntryHiddenFromHistory(entry)) return
        scope.launch {
            mutex.withLock {
                mergeEntries(listOf(entry))
            }
            recomputeUnread()
        }
    }

    fun applyReactionUpdateFromSocket(dto: OverlayReactionLogEntryDto) {
        val entry = dto.toEntry(selfUserId) ?: return
        if (isEntryHiddenFromHistory(entry)) return
        scope.launch {
            mutex.withLock {
                upsertEntry(entry)
            }
        }
    }

    fun loadInitial() {
        scope.launch { fetchFirstPage(showLoading = true) }
    }

    fun refresh() {
        scope.launch { fetchFirstPage(showLoading = false, showRefreshing = true) }
    }

    fun toggleLogEntryReaction(logId: String, emoji: String) {
        val previous = _entries.value
        val index = previous.indexOfFirst { it.id == logId }
        if (index < 0) return
        val entry = previous[index]
        val optimisticReactions = applyOptimisticReactionToggle(entry.reactions, emoji) ?: return
        val optimisticEntry = entry.copy(reactions = optimisticReactions)
        _entries.value = previous.toMutableList().apply { this[index] = optimisticEntry }
        scope.launch {
            runCatching {
                val dto = chatApi.toggleOverlayReactionLogReaction(
                    logId = logId,
                    body = ToggleOverlayReactionLogReactionRequest(emoji = emoji),
                )
                val updated = dto.toEntry(selfUserId) ?: return@runCatching
                mutex.withLock {
                    upsertEntry(updated)
                }
            }.onFailure { e ->
                _entries.value = previous
                _error.value = e.message
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun fetchFirstPage(
        showLoading: Boolean,
        showRefreshing: Boolean = false,
    ) {
        mutex.withLock {
            if (showLoading && _loading.value) return
            if (showRefreshing && _refreshing.value) return
            if (showLoading) _loading.value = true
            if (showRefreshing) _refreshing.value = true
            _error.value = null
        }
        runCatching {
            val cursor = chatApi.getOverlayReactionReadCursor()
            val seen = cursor.lastSeenLogId?.trim()?.takeIf { it.isNotEmpty() }
            _lastSeenLogId.value = seen
            val page = chatApi.listOverlayReactionLog(before = null, limit = 50)
            mutex.withLock {
                nextCursor = page.nextCursor?.trim()?.takeIf { it.isNotEmpty() }
                mergeEntries(
                    filterVisible(page.items.mapNotNull { it.toEntry(selfUserId) }),
                    replace = true,
                )
            }
        }.onFailure { e ->
            _error.value = e.message
        }
        if (showLoading) _loading.value = false
        if (showRefreshing) _refreshing.value = false
        recomputeUnread()
    }

    fun loadMore() {
        val before = nextCursor ?: return
        scope.launch {
            mutex.withLock {
                if (_loadingMore.value) return@launch
                _loadingMore.value = true
            }
            runCatching {
                val page = chatApi.listOverlayReactionLog(before = before, limit = 50)
                mutex.withLock {
                    nextCursor = page.nextCursor?.trim()?.takeIf { it.isNotEmpty() }
                    mergeEntries(filterVisible(page.items.mapNotNull { it.toEntry(selfUserId) }))
                }
            }.onFailure { e ->
                _error.value = e.message
            }
            _loadingMore.value = false
        }
    }

    fun markAllRead() {
        val newestId = _entries.value.firstOrNull()?.id ?: return
        val previousCursor = _lastSeenLogId.value
        _lastSeenLogId.value = newestId
        _unreadEntryIds.value = emptySet()
        _unreadCount.value = 0
        scope.launch {
            recomputeUnread()
            runCatching {
                chatApi.advanceOverlayReactionReadCursor(
                    AdvanceOverlayReactionReadCursorRequest(lastSeenLogId = newestId),
                )
            }.onSuccess {
                if (_entries.value.none { isEntryUnread(it) }) {
                    _unreadCount.value = 0
                }
            }.onFailure {
                _lastSeenLogId.value = previousCursor
                recomputeUnread()
            }
        }
    }

    fun refreshUnreadCount() {
        scope.launch { recomputeUnread() }
    }

    /**
     * Hides all current reaction cards for this user on this device (local cutoff + read cursor).
     * Log rows in the team database are not deleted.
     */
    fun clearHistoryForUser() {
        scope.launch {
            mutex.withLock {
                if (_loading.value || _refreshing.value) return@launch
            }
            _error.value = null
            val self = selfUserId.trim()
            if (self.isEmpty()) return@launch

            val watermark = mutex.withLock {
                _entries.value.firstOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
            } ?: runCatching {
                chatApi.listOverlayReactionLog(before = null, limit = 1)
                    .items.firstOrNull()?.resolvedId()?.trim()?.takeIf { it.isNotEmpty() }
            }.getOrNull()
                ?: _lastSeenLogId.value?.trim()?.takeIf { it.isNotEmpty() }

            if (!watermark.isNullOrBlank()) {
                preferences.setHiddenBeforeLogId(self, watermark)
                _lastSeenLogId.value = watermark
                runCatching {
                    chatApi.advanceOverlayReactionReadCursor(
                        AdvanceOverlayReactionReadCursorRequest(lastSeenLogId = watermark),
                    )
                }
            }

            mutex.withLock {
                _entries.value = emptyList()
                nextCursor = null
            }
            _unreadCount.value = 0
            _unreadEntryIds.value = emptySet()
        }
    }

    private fun isEntryHiddenFromHistory(entry: OverlayReactionLogEntry): Boolean {
        val hidden = preferences.getHiddenBeforeLogId(selfUserId) ?: return false
        return !OverlayReactionLogVisibilityPolicy.isLogEntryAfterCursor(entry, hidden)
    }

    private fun filterVisible(entries: List<OverlayReactionLogEntry>): List<OverlayReactionLogEntry> =
        entries.filterNot { isEntryHiddenFromHistory(it) }

    private suspend fun recomputeUnread() {
        val self = selfUserId
        if (self.isEmpty()) {
            _unreadCount.value = 0
            _unreadEntryIds.value = emptySet()
            return
        }
        val lastSeen = _lastSeenLogId.value?.trim().orEmpty()
        val unreadIds = _entries.value
            .filter { entry ->
                OverlayReactionLogVisibilityPolicy.isEntryUnread(entry, self, lastSeen.ifEmpty { null })
            }
            .map { it.id }
            .toSet()
        if (unreadIds.isNotEmpty()) {
            _unreadCount.value = unreadIds.size
            _unreadEntryIds.value = unreadIds
            return
        }
        runCatching {
            chatApi.getOverlayReactionReadCursor().lastSeenLogId?.trim()?.takeIf { it.isNotEmpty() }
        }.onSuccess { serverCursor ->
            if (!serverCursor.isNullOrBlank()) {
                _lastSeenLogId.value = serverCursor
            }
        }
        val cursor = _lastSeenLogId.value?.trim().orEmpty()
        val resolvedUnread = _entries.value
            .filter { entry ->
                OverlayReactionLogVisibilityPolicy.isEntryUnread(entry, self, cursor.ifEmpty { null })
            }
            .map { it.id }
            .toSet()
        _unreadCount.value = resolvedUnread.size
        _unreadEntryIds.value = resolvedUnread
    }

    private fun upsertEntry(entry: OverlayReactionLogEntry) {
        val existing = _entries.value
        val index = existing.indexOfFirst { it.id == entry.id }
        _entries.value = if (index >= 0) {
            existing.toMutableList().apply { this[index] = entry }
        } else {
            (existing + entry).distinctBy { it.id }.sortedByDescending { it.id }
        }
    }

    private fun applyOptimisticReactionToggle(
        reactions: List<ChatReaction>,
        emoji: String,
    ): List<ChatReaction>? {
        val mutable = reactions.toMutableList()
        val at = mutable.indexOfFirst { it.emoji == emoji }
        if (at >= 0) {
            val row = mutable[at]
            if (row.reactedByMe) {
                val nextCount = row.count - 1
                if (nextCount <= 0) {
                    mutable.removeAt(at)
                } else {
                    mutable[at] = row.copy(count = nextCount, reactedByMe = false)
                }
            } else {
                mutable[at] = row.copy(count = row.count + 1, reactedByMe = true)
            }
        } else {
            mutable.add(
                ChatReaction(
                    emoji = emoji,
                    count = 1,
                    reactedByMe = true,
                ),
            )
        }
        return if (mutable == reactions) null else mutable
    }

    private fun mergeEntries(
        incoming: List<OverlayReactionLogEntry>,
        replace: Boolean = false,
    ) {
        if (incoming.isEmpty() && replace) {
            _entries.value = emptyList()
            return
        }
        val base = if (replace) emptyList() else _entries.value
        val merged = (base + incoming)
            .distinctBy { it.id }
            .sortedByDescending { it.id }
        _entries.value = merged
    }
}
