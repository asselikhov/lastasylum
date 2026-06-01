package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.ui.util.parseIsoInstant

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
        if (cursor.isEmpty()) return true
        return isLogEntryAfterCursor(entry, cursor)
    }

    internal fun isLogEntryAfterCursor(
        entry: OverlayReactionLogEntry,
        cursor: String,
    ): Boolean {
        if (entry.id.equals(cursor, ignoreCase = true)) return false
        val entryInstant = parseIsoInstant(entry.createdAt.trim())
        val cursorInstant = objectIdInstantOrNull(cursor)
        if (entryInstant != null && cursorInstant != null) {
            return entryInstant.isAfter(cursorInstant)
        }
        if (entry.id.length == 24 && cursor.length == 24) {
            return entry.id.compareTo(cursor, ignoreCase = true) > 0
        }
        return entry.id > cursor
    }

    private fun objectIdInstantOrNull(id: String): java.time.Instant? {
        if (id.length != 24) return null
        return runCatching {
            val ts = id.substring(0, 8).toLong(16)
            java.time.Instant.ofEpochSecond(ts)
        }.getOrNull()
    }

    fun matchesFilter(
        entry: OverlayReactionLogEntry,
        selfUserId: String,
        filter: OverlayReactionLogFilter,
    ): Boolean = when (filter) {
        OverlayReactionLogFilter.All -> true
        OverlayReactionLogFilter.Incoming ->
            isIncoming(entry, selfUserId) && !OverlayReactionLogReplyEnricher.isReplyEntry(entry)
        OverlayReactionLogFilter.Outgoing ->
            isOutgoing(entry, selfUserId) && !OverlayReactionLogReplyEnricher.isReplyEntry(entry)
        OverlayReactionLogFilter.Reply -> OverlayReactionLogReplyEnricher.isReplyEntry(entry)
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
    Reply,
}

enum class OverlayReactionLogScopeFilter {
    All,
    Personal,
    Broadcast,
}
