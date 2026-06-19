package com.lastasylum.alliance.ui.screens.teamforum

import com.lastasylum.alliance.data.isObjectIdNewer
import com.lastasylum.alliance.ui.chat.ForumTimelineEntry

/** Viewport watermark for forum topic mark-read (overlay + main app). */
internal object ForumVisibleMarkReadLogic {
    fun readWatermarkFromVisibleLazyIndices(
        lazyIndices: List<Int>,
        timeline: List<ForumTimelineEntry>,
        currentUserId: String,
        lastReadCursor: String?,
        senderUserIdForMessageId: (String) -> String?,
    ): String? {
        val self = currentUserId.trim()
        val lastRead = lastReadCursor?.trim().orEmpty()
        var watermark: String? = null
        for (lazyIdx in lazyIndices) {
            val timelineIndex = timeline.lastIndex - lazyIdx
            val entry = timeline.getOrNull(timelineIndex) ?: continue
            val id = when (entry) {
                is ForumTimelineEntry.Message -> entry.messageId
                else -> continue
            }
            if (id.isBlank()) continue
            if (self.isNotBlank()) {
                val sender = senderUserIdForMessageId(id)?.trim()
                if (sender == self) continue
            }
            if (lastRead.isNotEmpty() && !isObjectIdNewer(id, lastRead)) continue
            watermark = when (val prev = watermark) {
                null -> id
                else -> if (isObjectIdNewer(id, prev)) id else prev
            }
        }
        return watermark
    }
}

internal const val FORUM_PEER_READ_CURSOR_POLL_DELAY_MS = 2_000L
