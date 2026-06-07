package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.isObjectIdNewer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val MARK_READ_NETWORK_DEBOUNCE_MS = 1_500L
internal const val MARK_READ_FORCE_SYNC_MIN_INTERVAL_MS = 10_000L

/**
 * Coalesces mark-read requests per room: optimistic cursor advance immediately,
 * debounced single POST /read with the max message id watermark.
 */
internal class ChatMarkReadCoalescer(
    private val scope: CoroutineScope,
) {
    private data class RoomState(
        var pendingMessageId: String? = null,
        var lastPostedMessageId: String? = null,
        var lastForceSyncAtMs: Long = 0L,
        var debounceJob: Job? = null,
        val flushMutex: Mutex = Mutex(),
    )

    private val rooms = ConcurrentHashMap<String, RoomState>()
    private var networkHandler: (suspend (roomId: String, messageId: String) -> Unit)? = null

    fun clear() {
        rooms.values.forEach { state ->
            state.debounceJob?.cancel()
            state.debounceJob = null
            state.pendingMessageId = null
        }
        rooms.clear()
        networkHandler = null
    }

    /** Cancel debounce and POST pending mark-read immediately (overlay close / DoneAll). */
    suspend fun flushAndAwait(roomId: String? = null) {
        val handler = networkHandler ?: return
        val targets = when (val rid = roomId?.trim().orEmpty()) {
            "" -> rooms.keys.toList()
            else -> listOf(rid).filter { rooms.containsKey(it) }
        }
        for (rid in targets) {
            val state = rooms[rid] ?: continue
            state.debounceJob?.cancel()
            state.debounceJob = null
            flushRoom(rid, handler)
        }
    }

    fun hasPending(roomId: String? = null): Boolean {
        val rid = roomId?.trim().orEmpty()
        if (rid.isEmpty()) {
            return rooms.values.any { it.pendingMessageId?.isNotBlank() == true }
        }
        return rooms[rid]?.pendingMessageId?.isNotBlank() == true
    }

    fun schedule(
        roomId: String,
        messageId: String,
        forceSync: Boolean,
        getCurrentCursor: () -> String?,
        onOptimisticAdvance: (roomId: String, messageId: String) -> Unit,
        onNetworkMarkRead: suspend (roomId: String, messageId: String) -> Unit,
    ) {
        val rid = roomId.trim()
        val mid = messageId.trim()
        if (rid.isEmpty() || mid.isEmpty()) return

        networkHandler = onNetworkMarkRead
        val state = rooms.getOrPut(rid) { RoomState() }
        val cursor = getCurrentCursor()?.trim().orEmpty()
        val posted = state.lastPostedMessageId?.trim().orEmpty()

        if (cursor.isNotEmpty() && !isObjectIdNewer(mid, cursor)) return
        if (posted.isNotEmpty() && !isObjectIdNewer(mid, posted)) return

        val now = System.currentTimeMillis()
        if (forceSync &&
            state.lastForceSyncAtMs > 0L &&
            now - state.lastForceSyncAtMs < MARK_READ_FORCE_SYNC_MIN_INTERVAL_MS &&
            posted.isNotEmpty() &&
            !isObjectIdNewer(mid, posted)
        ) {
            return
        }

        val pending = state.pendingMessageId?.trim().orEmpty()
        state.pendingMessageId = when {
            pending.isEmpty() -> mid
            isObjectIdNewer(mid, pending) -> mid
            else -> pending
        }

        val toAdvance = state.pendingMessageId ?: return
        if (cursor.isEmpty() || isObjectIdNewer(toAdvance, cursor)) {
            onOptimisticAdvance(rid, toAdvance)
        }

        if (forceSync) {
            state.lastForceSyncAtMs = now
        }

        state.debounceJob?.cancel()
        state.debounceJob = scope.launch {
            delay(MARK_READ_NETWORK_DEBOUNCE_MS)
            flushRoom(rid, onNetworkMarkRead)
        }
    }

    private suspend fun flushRoom(
        roomId: String,
        onNetworkMarkRead: suspend (roomId: String, messageId: String) -> Unit,
    ) {
        val state = rooms[roomId] ?: return
        state.flushMutex.withLock {
            val messageId = state.pendingMessageId?.trim().orEmpty()
            if (messageId.isEmpty()) return
            val posted = state.lastPostedMessageId?.trim().orEmpty()
            if (posted.isNotEmpty() && !isObjectIdNewer(messageId, posted)) {
                state.pendingMessageId = null
                return
            }
            state.pendingMessageId = null
            onNetworkMarkRead(roomId, messageId)
            state.lastPostedMessageId = messageId
        }
    }
}

internal fun shouldFetchPeerReadCursor(
    lastAtMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    minIntervalMs: Long = PEER_READ_CURSOR_MIN_INTERVAL_MS,
    force: Boolean = false,
): Boolean {
    if (lastAtMs <= 0L) return true
    if (force) return nowMs - lastAtMs >= minIntervalMs
    return nowMs - lastAtMs >= minIntervalMs
}

internal const val PEER_READ_CURSOR_MIN_INTERVAL_MS = 30_000L
internal const val OVERLAY_VIEWPORT_MARK_READ_DEBOUNCE_MS = 500L
internal const val CHAT_OVERLAY_ACTIVE_ROOM_RECONCILE_INTERVAL_MS = 120_000L
