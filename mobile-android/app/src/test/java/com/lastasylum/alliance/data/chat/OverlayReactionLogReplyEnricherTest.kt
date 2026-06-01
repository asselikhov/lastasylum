package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionLogReplyEnricherTest {
    private fun parent(id: String) = OverlayReactionLogEntry(
        id = id,
        senderUserId = "user-a",
        senderUsername = "A",
        targetUserId = "user-b",
        targetUsername = "B",
        reaction = "heart",
        visibility = OverlayReactionLogVisibility.Personal,
        createdAt = "2026-05-29T10:00:00.000Z",
    )

    private fun reply(id: String, parentId: String) = OverlayReactionLogEntry(
        id = id,
        senderUserId = "user-b",
        senderUsername = "B",
        targetUserId = "user-a",
        targetUsername = "A",
        reaction = "thumbsup",
        visibility = OverlayReactionLogVisibility.Personal,
        createdAt = "2026-05-29T10:00:01.000Z",
        replyToLogId = parentId,
    )

    @Test
    fun isReplyEntry_trueWhenOnlyReplyToLogId() {
        assertTrue(OverlayReactionLogReplyEnricher.isReplyEntry(reply("r1", "p1")))
    }

    @Test
    fun enrichEntries_hydratesReplyToFromParent() {
        val enriched = OverlayReactionLogReplyEnricher.enrichEntries(
            listOf(parent("p1"), reply("r1", "p1")),
        )
        val replyRow = enriched.first { it.id == "r1" }
        assertNotNull(replyRow.replyToLog)
        assertEquals("p1", replyRow.replyToLog?.logId)
        assertEquals("heart", replyRow.replyToLog?.reaction)
    }
}
