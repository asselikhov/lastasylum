package com.lastasylum.alliance.push

/**
 * Offline pushes should not replay as overlay strip cards when the player enters the game.
 */
object GameEventPushStripSuppressor {
    private const val MAX_ENTRIES = 64
    private val pushAcknowledgedMessageIds = LinkedHashSet<String>()

    fun ackPushDelivered(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        synchronized(this) {
            pushAcknowledgedMessageIds.add(id)
            while (pushAcknowledgedMessageIds.size > MAX_ENTRIES) {
                val oldest = pushAcknowledgedMessageIds.iterator().next()
                pushAcknowledgedMessageIds.remove(oldest)
            }
        }
    }

    fun shouldSuppressStrip(messageId: String?): Boolean {
        val id = messageId?.trim().orEmpty()
        if (id.isEmpty()) return false
        return synchronized(this) { pushAcknowledgedMessageIds.contains(id) }
    }

    /** Test-only reset. */
    internal fun clearForTests() {
        synchronized(this) { pushAcknowledgedMessageIds.clear() }
    }
}
