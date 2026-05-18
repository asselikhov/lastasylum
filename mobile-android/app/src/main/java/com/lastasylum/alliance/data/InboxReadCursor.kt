package com.lastasylum.alliance.data

private val OBJECT_ID_HEX = Regex("^[a-fA-F0-9]{24}$")

/** MongoDB ObjectId strings are chronologically ordered when compared lexicographically (same length). */
fun isObjectIdNewer(candidate: String, baseline: String?): Boolean {
    if (baseline.isNullOrBlank()) return true
    if (!OBJECT_ID_HEX.matches(candidate)) return true
    if (!OBJECT_ID_HEX.matches(baseline)) return true
    if (candidate.length != baseline.length) return candidate.length > baseline.length
    return candidate > baseline
}

/**
 * Suppresses stale server unread counts when local read cursor is at/ past server [lastReadMessageId].
 */
fun effectiveUnreadCount(
    serverUnread: Int,
    lastReadMessageId: String?,
    localLastReadMessageId: String?,
): Int {
    if (serverUnread <= 0) return 0
    val localLast = localLastReadMessageId?.trim().orEmpty()
    if (localLast.isBlank()) return serverUnread
    val serverLast = lastReadMessageId?.trim().orEmpty()
    if (serverLast.isBlank()) return 0
    if (!isObjectIdNewer(serverLast, localLast)) return 0
    if (isObjectIdNewer(localLast, serverLast)) return 0
    return serverUnread
}
