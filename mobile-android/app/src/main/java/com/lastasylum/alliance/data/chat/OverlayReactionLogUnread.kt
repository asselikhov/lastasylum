package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.data.isObjectIdNewer

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

/** Newest Mongo ObjectId string among [ids], or null if empty. */
fun maxOverlayReactionLogId(ids: Collection<String>): String? {
    var best: String? = null
    for (raw in ids) {
        val id = raw.trim()
        if (id.isEmpty()) continue
        val current = best
        if (current == null || isObjectIdNewer(id, current)) {
            best = id
        }
    }
    return best
}

/**
 * Watermark for mark-all-read: covers in-memory unread, loaded feed head, and prior cursor.
 */
fun resolveOverlayReactionMarkAllReadWatermark(
    unreadIds: Set<String>,
    loadedEntries: List<OverlayReactionLogEntry>,
    lastSeenLogId: String?,
): String? = maxOverlayReactionLogId(
    buildList {
        addAll(unreadIds)
        addAll(loadedEntries.map { it.id })
        lastSeenLogId?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
    },
)

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
