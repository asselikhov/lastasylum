package com.lastasylum.alliance.data.chat

/**
 * Resolves parent reaction snapshot for an incoming reply burst (socket event + log feed).
 */
object OverlayReactionIncomingBurstReplyResolver {
    fun isReplyEvent(event: OverlayReactionEvent): Boolean =
        event.replyToLog != null || !event.replyToLogId.isNullOrBlank()

    fun resolve(
        event: OverlayReactionEvent,
        logDto: OverlayReactionLogEntryDto? = null,
        entries: List<OverlayReactionLogEntry> = emptyList(),
    ): OverlayReactionBurstReplyTo? {
        event.replyToLog?.let { return it }
        logDto?.replyToLog?.toReplyTo()?.toBurstReplyTo()?.let { return it }

        val logEntryId = event.logEntryId?.trim().orEmpty()
        if (logEntryId.isNotEmpty()) {
            entries.find { it.id == logEntryId }
                ?.replyToLog
                ?.toBurstReplyTo()
                ?.let { return it }
            val replyEntry = entries.find { it.id == logEntryId }
            if (replyEntry != null && OverlayReactionLogReplyEnricher.isReplyEntry(replyEntry)) {
                OverlayReactionLogReplyEnricher.enrichEntries(entries)
                    .find { it.id == logEntryId }
                    ?.replyToLog
                    ?.toBurstReplyTo()
                    ?.let { return it }
            }
        }

        val parentId = event.replyToLogId?.trim().orEmpty()
        if (parentId.isNotEmpty()) {
            entries.find { it.id == parentId }?.let { parent ->
                return OverlayReactionBurstReplyTo(
                    logId = parent.id,
                    reaction = parent.reaction,
                    visibility = parent.visibility,
                )
            }
        }
        return null
    }
}
