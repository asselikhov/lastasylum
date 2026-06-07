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
 * Per-user unread badge on this device: when the **device-persisted** read cursor is at or past
 * the server cursor, trust that the user already read (including reads persisted locally before
 * server sync). Stale [serverUnread] with equal cursors, or a missing server cursor with a local
 * one, must not resurrect badges after update or reinstall.
 *
 * [localLastReadMessageId] must be prefs/memory only — do not pass server [lastReadMessageId] here.
 */
fun effectiveUnreadCount(
    serverUnread: Int,
    lastReadMessageId: String?,
    localLastReadMessageId: String?,
): Int {
    if (serverUnread <= 0) return 0
    val localLast = localLastReadMessageId?.trim().orEmpty()
    val serverLast = lastReadMessageId?.trim().orEmpty()
    if (localLast.isNotBlank() && serverLast.isNotBlank() && !isObjectIdNewer(serverLast, localLast)) {
        return 0
    }
    if (localLast.isNotBlank() && serverLast.isBlank()) {
        // Mark-read persisted locally; stale server unreadCount without lastReadMessageId.
        return 0
    }
    return serverUnread
}

/**
 * Badge count shown in UI: keep optimistic bump while server count is still zero,
 * but never resurrect a badge when [rawServerUnread] > 0 and local read cursors
 * already suppress [effectiveUnread] to zero.
 */
fun displayedUnreadCount(
    effectiveUnread: Int,
    previouslyDisplayed: Int,
    rawServerUnread: Int = 0,
    optimisticFloor: Int = 0,
): Int {
    val effective = effectiveUnread.coerceAtLeast(0)
    val previous = previouslyDisplayed.coerceAtLeast(0)
    val raw = rawServerUnread.coerceAtLeast(0)
    val floor = optimisticFloor.coerceAtLeast(0)
    if (raw > 0 && effective == 0) {
        return 0
    }
    if (effective == 0) {
        // Do not resurrect cleared badges from [previouslyDisplayed] when leaving/re-entering chat.
        return floor
    }
    // Trust server effective count only — do not stack with a previously cleared badge.
    return maxOf(effective, floor)
}

/** @see displayedUnreadCount */
fun reconcileDisplayedUnread(serverUnread: Int, previouslyDisplayed: Int): Int =
    displayedUnreadCount(serverUnread, previouslyDisplayed, rawServerUnread = serverUnread)

/**
 * Whether to drop an optimistic unread floor after server merge/recompute.
 * Prevents badge flicker when listRooms still reports zero after a socket bump.
 */
fun shouldClearOptimisticUnreadFloor(
    floor: Int,
    rawServerUnread: Int,
    displayedUnread: Int,
    lastBumpAtMs: Long = 0L,
    nowMs: Long = System.currentTimeMillis(),
    graceMs: Long = 4_000L,
): Boolean {
    if (floor <= 0) return true
    val raw = rawServerUnread.coerceAtLeast(0)
    if (raw >= floor) return true
    if (raw > 0 && displayedUnread == 0) return true
    if (displayedUnread > 0) return false
    if (raw == 0 && lastBumpAtMs > 0L && nowMs - lastBumpAtMs < graceMs) return false
    return true
}

/** Unified inbox badge display: effective unread + optimistic floor + anti-flicker previous. */
fun computeDisplayedUnread(
    serverUnread: Int,
    lastReadMessageId: String?,
    localLastReadMessageId: String?,
    optimisticFloor: Int = 0,
    previouslyDisplayed: Int = 0,
): Int {
    val effective = effectiveUnreadCount(
        serverUnread = serverUnread,
        lastReadMessageId = lastReadMessageId,
        localLastReadMessageId = localLastReadMessageId,
    )
    return displayedUnreadCount(
        effectiveUnread = effective,
        previouslyDisplayed = previouslyDisplayed,
        rawServerUnread = serverUnread,
        optimisticFloor = optimisticFloor,
    )
}
