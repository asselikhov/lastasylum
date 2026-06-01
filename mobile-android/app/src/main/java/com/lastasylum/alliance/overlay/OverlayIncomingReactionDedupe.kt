package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionEvent

/**
 * Suppresses duplicate fullscreen reaction bursts from double socket delivery or stacked listeners.
 */
internal object OverlayIncomingReactionDedupe {
    private const val TTL_MS = 3_000L
    private const val MAX_KEYS = 32

    private val recentKeys = LinkedHashMap<String, Long>(MAX_KEYS + 1, 0.75f, true)

    fun shouldSuppress(event: OverlayReactionEvent, nowMs: Long = System.currentTimeMillis()): Boolean {
        val key = dedupeKey(event)
        prune(nowMs)
        val last = recentKeys[key]
        if (last != null && nowMs - last < TTL_MS) {
            return true
        }
        recentKeys[key] = nowMs
        while (recentKeys.size > MAX_KEYS) {
            val eldest = recentKeys.entries.firstOrNull()?.key ?: break
            recentKeys.remove(eldest)
        }
        return false
    }

    fun clear() {
        recentKeys.clear()
    }

    private fun dedupeKey(event: OverlayReactionEvent): String {
        val logId = event.logEntryId?.trim().orEmpty()
        if (logId.isNotEmpty()) return "log:$logId"
        return buildString {
            append(event.fromUserId.trim())
            append('|')
            append(event.targetUserId.trim())
            append('|')
            append(event.reaction.trim())
            append('|')
            append(event.broadcast)
        }
    }

    private fun prune(nowMs: Long) {
        val iter = recentKeys.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (nowMs - entry.value >= TTL_MS) {
                iter.remove()
            }
        }
    }
}
