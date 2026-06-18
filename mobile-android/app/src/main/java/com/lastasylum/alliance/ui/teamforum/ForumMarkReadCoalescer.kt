package com.lastasylum.alliance.ui.teamforum

import com.lastasylum.alliance.data.isObjectIdNewer
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val FORUM_MARK_READ_DEBOUNCE_MS = 320L

/**
 * Coalesces forum mark-read per topic: optimistic cursor advance immediately,
 * debounced single POST with max message id watermark.
 */
class ForumMarkReadCoalescer(
    private val scope: CoroutineScope,
) {
    private data class TopicState(
        var pendingMessageId: String? = null,
        var lastPostedMessageId: String? = null,
        var debounceJob: Job? = null,
        val flushMutex: Mutex = Mutex(),
    )

    private val topics = ConcurrentHashMap<String, TopicState>()
    private var networkHandler: (suspend (topicId: String, messageId: String) -> Unit)? = null

    fun clear() {
        topics.values.forEach { state ->
            state.debounceJob?.cancel()
            state.debounceJob = null
            state.pendingMessageId = null
        }
        topics.clear()
        networkHandler = null
    }

    suspend fun flushAndAwait(topicId: String? = null) {
        val handler = networkHandler ?: return
        val targets = when (val tid = topicId?.trim().orEmpty()) {
            "" -> topics.keys.toList()
            else -> listOf(tid).filter { topics.containsKey(it) }
        }
        for (tid in targets) {
            val state = topics[tid] ?: continue
            state.debounceJob?.cancel()
            state.debounceJob = null
            flushTopic(tid, handler)
        }
    }

    fun schedule(
        topicId: String,
        messageId: String,
        getCurrentCursor: () -> String?,
        onOptimisticAdvance: (topicId: String, messageId: String) -> Unit,
        onNetworkMarkRead: suspend (topicId: String, messageId: String) -> Unit,
        debounceMs: Long = FORUM_MARK_READ_DEBOUNCE_MS,
    ) {
        val tid = topicId.trim()
        val mid = messageId.trim()
        if (tid.isEmpty() || mid.isEmpty()) return

        networkHandler = onNetworkMarkRead
        val state = topics.getOrPut(tid) { TopicState() }
        val cursor = getCurrentCursor()?.trim().orEmpty()
        val posted = state.lastPostedMessageId?.trim().orEmpty()

        if (cursor.isNotEmpty() && !isObjectIdNewer(mid, cursor)) return
        if (posted.isNotEmpty() && !isObjectIdNewer(mid, posted)) return

        val pending = state.pendingMessageId?.trim().orEmpty()
        state.pendingMessageId = when {
            pending.isEmpty() -> mid
            isObjectIdNewer(mid, pending) -> mid
            else -> pending
        }

        val toAdvance = state.pendingMessageId ?: return
        if (cursor.isEmpty() || isObjectIdNewer(toAdvance, cursor)) {
            onOptimisticAdvance(tid, toAdvance)
        }

        state.debounceJob?.cancel()
        state.debounceJob = scope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            flushTopic(tid, onNetworkMarkRead)
        }
    }

    private suspend fun flushTopic(
        topicId: String,
        onNetworkMarkRead: suspend (topicId: String, messageId: String) -> Unit,
    ) {
        val state = topics[topicId] ?: return
        state.flushMutex.withLock {
            val messageId = state.pendingMessageId?.trim().orEmpty()
            if (messageId.isEmpty()) return
            val posted = state.lastPostedMessageId?.trim().orEmpty()
            if (posted.isNotEmpty() && !isObjectIdNewer(messageId, posted)) {
                state.pendingMessageId = null
                return
            }
            state.pendingMessageId = null
            onNetworkMarkRead(topicId, messageId)
            state.lastPostedMessageId = messageId
        }
    }
}
