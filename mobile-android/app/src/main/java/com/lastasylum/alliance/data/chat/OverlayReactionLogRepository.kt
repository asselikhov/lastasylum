package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.data.isObjectIdNewer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    companion object {
        /** In-memory window for overlay list; older pages remain on server and reload via [loadMore]. */
        const val MAX_RETAINED_LOG_ENTRIES = 300

        private const val MARK_READ_UP_TO_DEBOUNCE_MS = 320L
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var markReadUpToJob: Job? = null

    @Volatile
    private var pendingMarkReadUpToWatermark: String? = null

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
            recomputeUnread()
        }
    }

    fun loadInitial() {
        scope.launch {
            fetchFirstPage(showLoading = !shouldUseSilentInitialFetch())
        }
    }

    /**
     * After local history clear we already know the feed is empty on this device;
     * skip the blocking skeleton and refresh quietly (new reactions after cutoff still load).
     */
    private fun shouldUseSilentInitialFetch(): Boolean {
        val self = selfUserId.trim()
        if (self.isEmpty()) return false
        if (_entries.value.isNotEmpty()) return false
        return preferences.getHiddenBeforeLogId(self) != null
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
            mergeLastSeenFromServer(seen, _lastSeenLogId.value)
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
            recomputeUnread()
        }
    }

    fun markAllRead() {
        scope.launch { markAllReadAwait() }
    }

    /** Advance read cursor for visible overlay cards (debounced PATCH). */
    fun markReadUpTo(logId: String) {
        val id = logId.trim()
        if (id.isEmpty()) return
        scope.launch { markReadUpToAwait(id) }
    }

    suspend fun markReadUpToAwait(logId: String) {
        val id = logId.trim()
        if (id.isEmpty()) return
        val self = selfUserId.trim()
        if (self.isEmpty()) return
        val current = _lastSeenLogId.value?.trim()?.takeIf { it.isNotEmpty() }
        if (current != null && !isObjectIdNewer(id, current)) return
        val merged = maxOverlayReactionLogId(listOfNotNull(current, id)) ?: return
        mutex.withLock {
            _lastSeenLogId.value = merged
        }
        publishUnreadCounts(
            unreadIdsForEntries(
                _entries.value,
                self,
                merged,
            ),
        )
        pendingMarkReadUpToWatermark = merged
        markReadUpToJob?.cancel()
        markReadUpToJob = scope.launch {
            delay(MARK_READ_UP_TO_DEBOUNCE_MS)
            flushPendingReadCursorAwait()
        }
    }

    /** Push debounced read cursor before closing the overlay panel. */
    suspend fun flushPendingReadCursorAwait() {
        markReadUpToJob?.cancel()
        markReadUpToJob = null
        val watermark = pendingMarkReadUpToWatermark
            ?: _lastSeenLogId.value?.trim()?.takeIf { it.isNotEmpty() }
            ?: return
        pendingMarkReadUpToWatermark = null
        val previous = _lastSeenLogId.value
        runCatching {
            chatApi.advanceOverlayReactionReadCursor(
                AdvanceOverlayReactionReadCursorRequest(lastSeenLogId = watermark),
            )
        }.onFailure {
            _lastSeenLogId.value = previous
            recomputeUnread()
        }
    }

    suspend fun markAllReadAwait(): Boolean {
        flushPendingReadCursorAwait()
        markReadUpToJob?.cancel()
        markReadUpToJob = null
        pendingMarkReadUpToWatermark = null
        val watermark = resolveMarkAllReadWatermark()
        if (watermark.isNullOrBlank()) return false
        val previousCursor = _lastSeenLogId.value
        return runCatching {
            chatApi.advanceOverlayReactionReadCursor(
                AdvanceOverlayReactionReadCursorRequest(lastSeenLogId = watermark),
            )
        }.onSuccess { response ->
            val serverCursor = response.lastSeenLogId?.trim()?.takeIf { it.isNotEmpty() }
            val merged = mergeOverlayReactionLastSeenLogId(watermark, serverCursor) ?: watermark
            _lastSeenLogId.value = merged
            publishUnreadCounts(emptySet())
        }.onFailure {
            _lastSeenLogId.value = previousCursor
            recomputeUnread()
        }.isSuccess
    }

    private suspend fun resolveMarkAllReadWatermark(): String? {
        val fromMemory = resolveOverlayReactionMarkAllReadWatermark(
            unreadIds = _unreadEntryIds.value,
            loadedEntries = _entries.value,
            lastSeenLogId = _lastSeenLogId.value,
        )
        val fetchedNewest = runCatching {
            chatApi.listOverlayReactionLog(before = null, limit = 1)
                .items.firstOrNull()?.resolvedId()?.trim()?.takeIf { it.isNotEmpty() }
        }.getOrNull()
        return maxOverlayReactionLogId(listOfNotNull(fromMemory, fetchedNewest))
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

    private suspend fun recomputeUnread(fetchServerCursorIfEmpty: Boolean = true) {
        val self = selfUserId
        if (self.isEmpty()) {
            publishUnreadCounts(emptySet())
            return
        }
        val entries = _entries.value
        val lastSeen = _lastSeenLogId.value?.trim()?.takeIf { it.isNotEmpty() }
        var unreadIds = unreadIdsForEntries(entries, self, lastSeen)
        if (unreadIds.isNotEmpty()) {
            publishUnreadCounts(unreadIds)
            return
        }
        if (fetchServerCursorIfEmpty) {
            val localSeen = lastSeen
            runCatching {
                chatApi.getOverlayReactionReadCursor().lastSeenLogId?.trim()?.takeIf { it.isNotEmpty() }
            }.onSuccess { serverCursor ->
                mergeLastSeenFromServer(serverCursor, localSeen)
            }
            unreadIds = unreadIdsForEntries(
                _entries.value,
                self,
                _lastSeenLogId.value?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        publishUnreadCounts(unreadIds)
    }

    private fun unreadIdsForEntries(
        entries: List<OverlayReactionLogEntry>,
        selfUserId: String,
        lastSeenLogId: String?,
    ): Set<String> = filterUnreadEntryIdsToRetained(
        computeUnreadEntryIds(entries, selfUserId, lastSeenLogId),
        entries,
    )

    private fun mergeLastSeenFromServer(serverCursor: String?, localSeen: String?) {
        val merged = mergeOverlayReactionLastSeenLogId(localSeen, serverCursor) ?: return
        _lastSeenLogId.value = merged
    }

    private fun publishUnreadCounts(unreadIds: Set<String>) {
        _unreadCount.value = unreadIds.size
        _unreadEntryIds.value = unreadIds
    }

    private fun upsertEntry(entry: OverlayReactionLogEntry) {
        val existing = _entries.value
        val index = existing.indexOfFirst { it.id == entry.id }
        val merged = if (index >= 0) {
            existing.toMutableList().apply { this[index] = entry }
        } else {
            (existing + entry).distinctBy { it.id }.sortedByDescending { it.id }
        }
        _entries.value = trimRetainedEntries(
            OverlayReactionLogReplyEnricher.enrichEntries(merged),
        )
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
        val merged = OverlayReactionLogReplyEnricher.enrichEntries(
            (base + incoming)
                .distinctBy { it.id }
                .sortedByDescending { it.id },
        )
        _entries.value = trimRetainedEntries(merged)
    }

    private fun trimRetainedEntries(entries: List<OverlayReactionLogEntry>): List<OverlayReactionLogEntry> {
        if (entries.size <= MAX_RETAINED_LOG_ENTRIES) return entries
        return entries.take(MAX_RETAINED_LOG_ENTRIES)
    }
}
