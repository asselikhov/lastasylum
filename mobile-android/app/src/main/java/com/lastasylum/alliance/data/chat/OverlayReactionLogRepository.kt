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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<OverlayReactionLogEntry>>(emptyList())
    val entries: StateFlow<List<OverlayReactionLogEntry>> = _entries.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

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
        val entry = dto.toEntry(selfUserId)
        if (entry == null) return
        scope.launch {
            mutex.withLock {
                mergeEntries(listOf(entry))
            }
            recomputeUnread()
        }
    }

    fun applyReactionUpdateFromSocket(dto: OverlayReactionLogEntryDto) {
        val entry = dto.toEntry(selfUserId) ?: return
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
                _error.value = e.message
            }
        }
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
                    page.items.mapNotNull { it.toEntry(selfUserId) },
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
                    mergeEntries(page.items.mapNotNull { it.toEntry(selfUserId) })
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

    private suspend fun recomputeUnread() {
        val self = selfUserId
        if (self.isEmpty()) {
            _unreadCount.value = 0
            return
        }
        val lastSeen = _lastSeenLogId.value?.trim().orEmpty()
        val count = _entries.value.count { entry ->
            OverlayReactionLogVisibilityPolicy.isEntryUnread(entry, self, lastSeen.ifEmpty { null })
        }
        if (count > 0) {
            _unreadCount.value = count
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
        _unreadCount.value = _entries.value.count { entry ->
            OverlayReactionLogVisibilityPolicy.isEntryUnread(entry, self, cursor.ifEmpty { null })
        }
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
