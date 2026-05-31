package com.lastasylum.alliance.data.chat

object OverlayReactionLogVisibilityPolicy {
    fun isIncoming(entry: OverlayReactionLogEntry, selfUserId: String): Boolean {
        val self = selfUserId.trim()
        if (self.isEmpty()) return false
        return entry.senderUserId.trim() != self
    }

    fun isOutgoing(entry: OverlayReactionLogEntry, selfUserId: String): Boolean {
        val self = selfUserId.trim()
        if (self.isEmpty()) return false
        return entry.senderUserId.trim() == self
    }

    fun isEntryUnread(
        entry: OverlayReactionLogEntry,
        selfUserId: String,
        lastSeenLogId: String?,
    ): Boolean {
        if (!isIncoming(entry, selfUserId)) return false
        val cursor = lastSeenLogId?.trim().orEmpty()
        return cursor.isEmpty() || entry.id > cursor
    }

    fun matchesFilter(
        entry: OverlayReactionLogEntry,
        selfUserId: String,
        filter: OverlayReactionLogFilter,
    ): Boolean = when (filter) {
        OverlayReactionLogFilter.All -> true
        OverlayReactionLogFilter.Incoming -> isIncoming(entry, selfUserId)
        OverlayReactionLogFilter.Outgoing -> isOutgoing(entry, selfUserId)
    }

    fun matchesScopeFilter(
        entry: OverlayReactionLogEntry,
        filter: OverlayReactionLogScopeFilter,
    ): Boolean = when (filter) {
        OverlayReactionLogScopeFilter.All -> true
        OverlayReactionLogScopeFilter.Personal ->
            entry.visibility == OverlayReactionLogVisibility.Personal
        OverlayReactionLogScopeFilter.Broadcast ->
            entry.visibility == OverlayReactionLogVisibility.Broadcast
    }

    fun matchesSearchQuery(entry: OverlayReactionLogEntry, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val needle = q.lowercase()
        return entry.senderUsername.lowercase().contains(needle) ||
            entry.targetUsername?.lowercase()?.contains(needle) == true
    }
}

enum class OverlayReactionLogFilter {
    All,
    Incoming,
    Outgoing,
}

enum class OverlayReactionLogScopeFilter {
    All,
    Personal,
    Broadcast,
}
