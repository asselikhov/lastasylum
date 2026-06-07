package com.lastasylum.alliance.data.chat

/**
 * Dedupe chat `message:new` fanout across ChatViewModel and overlay FGS.
 * Separate LRU sets so list apply and unread bumps do not block each other.
 */
object ChatSocketIngress {
    private const val MAX_SEEN = 4096
    private val lock = Any()
    private val chatListSeen = LinkedHashSet<String>()
    private val unreadBumpSeen = LinkedHashSet<String>()

    private fun ingressKey(roomId: String, messageId: String): String? {
        val rid = roomId.trim()
        val mid = messageId.trim()
        if (rid.isEmpty() || mid.isEmpty()) return null
        return "$rid:$mid"
    }

    private fun claim(seen: LinkedHashSet<String>, key: String): Boolean {
        synchronized(lock) {
            if (key in seen) return false
            seen.add(key)
            while (seen.size > MAX_SEEN) {
                val oldest = seen.iterator().next()
                seen.remove(oldest)
            }
            return true
        }
    }

    /** First consumer wins for visible chat list / VM apply path. */
    fun claimForChatList(roomId: String, messageId: String): Boolean {
        val key = ingressKey(roomId, messageId) ?: return false
        return claim(chatListSeen, key)
    }

    /** First consumer wins for optimistic unread badge bumps. */
    fun claimForUnreadBump(roomId: String, messageId: String): Boolean {
        val key = ingressKey(roomId, messageId) ?: return false
        return claim(unreadBumpSeen, key)
    }

    /** @see claimForChatList */
    fun markMessageNewSeen(roomId: String, messageId: String): Boolean =
        claimForChatList(roomId, messageId)

    fun clear() {
        synchronized(lock) {
            chatListSeen.clear()
            unreadBumpSeen.clear()
        }
    }
}
