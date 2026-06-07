package com.lastasylum.alliance.data.chat

import java.util.concurrent.ConcurrentHashMap

/**
 * Shared dedupe for chat `message:new` across ChatViewModel and overlay FGS —
 * prevents double unread bump when both listeners receive the same fanout.
 */
object ChatSocketIngress {
    private const val MAX_SEEN = 2048
    private val seenKeys = ConcurrentHashMap.newKeySet<String>()

    /** @return true if this (roomId, messageId) is new for ingress processing. */
    fun markMessageNewSeen(roomId: String, messageId: String): Boolean {
        val rid = roomId.trim()
        val mid = messageId.trim()
        if (rid.isEmpty() || mid.isEmpty()) return false
        val key = "$rid:$mid"
        if (!seenKeys.add(key)) return false
        while (seenKeys.size > MAX_SEEN) {
            val oldest = seenKeys.iterator().next()
            seenKeys.remove(oldest)
        }
        return true
    }

    fun clear() {
        seenKeys.clear()
    }
}
