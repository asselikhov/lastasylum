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
 * Per-user unread badge: trusts [serverUnread] only while this device has not read at/ past
 * the server cursor. A persisted local [localLastReadMessageId] suppresses stale server
 * counts (e.g. after [markRoomRead] before the next [listRooms] catches up).
 */
fun effectiveUnreadCount(
    serverUnread: Int,
    lastReadMessageId: String?,
    localLastReadMessageId: String?,
): Int {
    if (serverUnread <= 0) return 0
    val localLast = localLastReadMessageId?.trim().orEmpty()
    val serverLast = lastReadMessageId?.trim().orEmpty()
    if (localLast.isNotBlank()) {
        when {
            serverLast.isBlank() -> return 0
            !isObjectIdNewer(serverLast, localLast) -> return 0
        }
    }
    return serverUnread
}

/**
 * Keeps an optimistic on-device bump until [serverUnread] catches up (socket often arrives
 * before [listRooms] reflects the new message).
 */
fun reconcileDisplayedUnread(serverUnread: Int, previouslyDisplayed: Int): Int {
    val server = serverUnread.coerceAtLeast(0)
    val previous = previouslyDisplayed.coerceAtLeast(0)
    return if (server >= previous) server else previous
}
