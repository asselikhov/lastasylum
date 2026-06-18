package com.lastasylum.alliance.data.teams

/**
 * Dedupe forum `message:new` fanout across NavHost persistence and topic UI listeners.
 */
object ForumSocketIngress {
    private const val MAX_SEEN = 4096
    private val lock = Any()
    private val topicUiSeen = LinkedHashSet<String>()
    private val persistenceSeen = LinkedHashSet<String>()

    private fun ingressKey(topicId: String, messageId: String): String? {
        val tid = topicId.trim()
        val mid = messageId.trim()
        if (tid.isEmpty() || mid.isEmpty()) return null
        return "$tid:$mid"
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

    /** First consumer wins for topic screen merge path. */
    fun claimForTopicUi(topicId: String, messageId: String): Boolean {
        val key = ingressKey(topicId, messageId) ?: return false
        return claim(topicUiSeen, key)
    }

    /** First consumer wins for NavHost Room persistence path. */
    fun claimForPersistence(topicId: String, messageId: String): Boolean {
        val key = ingressKey(topicId, messageId) ?: return false
        return claim(persistenceSeen, key)
    }

    fun clear() {
        synchronized(lock) {
            topicUiSeen.clear()
            persistenceSeen.clear()
        }
    }
}
