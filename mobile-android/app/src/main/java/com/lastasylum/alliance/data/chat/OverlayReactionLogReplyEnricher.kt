package com.lastasylum.alliance.data.chat

/** Reply-thread helpers: detect reply rows and hydrate parent preview snapshots. */
object OverlayReactionLogReplyEnricher {
    fun isReplyEntry(entry: OverlayReactionLogEntry): Boolean =
        entry.replyToLog != null || !entry.replyToLogId.isNullOrBlank()

    fun parentLogId(entry: OverlayReactionLogEntry): String? =
        entry.replyToLog?.logId?.trim()?.takeIf { it.isNotEmpty() }
            ?: entry.replyToLogId?.trim()?.takeIf { it.isNotEmpty() }

    fun enrichEntries(entries: List<OverlayReactionLogEntry>): List<OverlayReactionLogEntry> {
        if (entries.isEmpty()) return entries
        val byId = entries.associateBy { it.id }
        return entries.map { entry ->
            if (entry.replyToLog != null) entry
            else {
                val parentId = entry.replyToLogId?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return@map entry
                val parent = byId[parentId] ?: return@map entry
                entry.copy(replyToLog = parent.toReplyToSnapshot())
            }
        }
    }
}

fun OverlayReactionLogEntry.toReplyToSnapshot(): OverlayReactionLogReplyTo =
    OverlayReactionLogReplyTo(
        logId = id,
        reaction = reaction,
        visibility = visibility,
        senderUserId = senderUserId,
        senderUsername = senderUsername,
        targetUserId = targetUserId,
        targetUsername = targetUsername,
    )
