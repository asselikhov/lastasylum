package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayReactionLogModelsTest {

    @Test
    fun toEntry_treatsReplyToLogIdLiteralNullAsAbsent() {
        val dto = OverlayReactionLogEntryDto(
            id = "r1",
            senderUserId = "u1",
            senderUsername = "U1",
            reaction = "heart",
            visibility = "personal",
            createdAt = "2026-06-02T12:00:00Z",
            replyToLogId = "null",
            replyToLog = null,
        )
        val entry = dto.toEntry(selfUserId = "u2")
        assertNull(entry?.replyToLogId)
        if (entry != null) {
            assertFalse(OverlayReactionLogReplyEnricher.isReplyEntry(entry))
        }
    }
}

