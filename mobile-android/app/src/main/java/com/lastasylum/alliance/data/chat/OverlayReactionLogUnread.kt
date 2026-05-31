package com.lastasylum.alliance.data.chat

fun computeUnreadEntryIds(
    entries: List<OverlayReactionLogEntry>,
    selfUserId: String,
    lastSeenLogId: String?,
): Set<String> {
    val self = selfUserId.trim()
    if (self.isEmpty()) return emptySet()
    val cursor = lastSeenLogId?.trim()?.takeIf { it.isNotEmpty() }
    return entries
        .filter { entry -> OverlayReactionLogVisibilityPolicy.isEntryUnread(entry, self, cursor) }
        .map { it.id }
        .toSet()
}
