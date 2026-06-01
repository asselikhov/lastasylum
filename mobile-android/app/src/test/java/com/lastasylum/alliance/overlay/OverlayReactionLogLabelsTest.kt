package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogReplyTo
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionLogLabelsTest {
    @Test
    fun replyEntry_hasReplyToLog() {
        val entry = OverlayReactionLogEntry(
            id = "r1",
            senderUserId = "u2",
            senderUsername = "Alice",
            targetUserId = "u1",
            targetUsername = "Bob",
            reaction = "heart",
            visibility = OverlayReactionLogVisibility.Personal,
            createdAt = "2026-05-29T10:00:00Z",
            replyToLog = OverlayReactionLogReplyTo(
                logId = "p1",
                reaction = "thumbsup",
                visibility = OverlayReactionLogVisibility.Personal,
                senderUserId = "u1",
                senderUsername = "Bob",
                targetUserId = "u2",
                targetUsername = "Alice",
            ),
        )
        assertTrue(entry.replyToLog != null)
    }

    @Test
    fun plainEntry_hasNoReplyToLog() {
        val entry = OverlayReactionLogEntry(
            id = "p1",
            senderUserId = "u1",
            senderUsername = "Bob",
            targetUserId = "u2",
            targetUsername = "Alice",
            reaction = "heart",
            visibility = OverlayReactionLogVisibility.Personal,
            createdAt = "2026-05-29T10:00:00Z",
        )
        assertFalse(entry.replyToLog != null)
    }
}
