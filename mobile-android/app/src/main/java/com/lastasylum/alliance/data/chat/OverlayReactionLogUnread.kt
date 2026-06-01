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

/** Drop phantom unread ids that were trimmed from the in-memory window. */
internal fun filterUnreadEntryIdsToRetained(
    unreadIds: Set<String>,
    entries: List<OverlayReactionLogEntry>,
): Set<String> = unreadIds.intersect(entries.map { it.id }.toSet())

/** Keep the newer read cursor when reconciling with the server (never downgrade local). */
internal fun mergeOverlayReactionLastSeenLogId(local: String?, server: String?): String? {
    val serverTrim = server?.trim()?.takeIf { it.isNotEmpty() }
    if (serverTrim == null) return local?.trim()?.takeIf { it.isNotEmpty() }
    val localTrim = local?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        localTrim == null -> serverTrim
        com.lastasylum.alliance.data.isObjectIdNewer(localTrim, serverTrim) -> localTrim
        else -> serverTrim
    }
}
