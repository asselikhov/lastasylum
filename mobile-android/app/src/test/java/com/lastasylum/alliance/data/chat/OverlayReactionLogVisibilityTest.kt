package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionLogVisibilityTest {
    private val self = "user-self"
    private val other = "user-other"
    private val third = "user-third"

    private fun replyTo(parentId: String) = OverlayReactionLogReplyTo(
        logId = parentId,
        reaction = "heart",
        visibility = OverlayReactionLogVisibility.Personal,
        senderUserId = other,
        senderUsername = "A",
        targetUserId = self,
        targetUsername = "Self",
    )

    private fun entry(
        sender: String,
        target: String? = null,
        visibility: OverlayReactionLogVisibility = OverlayReactionLogVisibility.Personal,
        id: String = "1",
        createdAt: String = "2026-01-01T12:00:00Z",
        replyToLog: OverlayReactionLogReplyTo? = null,
    ) = OverlayReactionLogEntry(
        id = id,
        senderUserId = sender,
        senderUsername = "A",
        targetUserId = target,
        targetUsername = "B",
        reaction = "heart",
        visibility = visibility,
        createdAt = createdAt,
        replyToLog = replyToLog,
    )

    @Test
    fun filter_reply_onlyReplyEntries() {
        val reply = entry(other, self, id = "2", replyToLog = replyTo("parent"))
        val plain = entry(other, self, id = "3")
        assertTrue(
            OverlayReactionLogVisibilityPolicy.matchesFilter(
                reply,
                self,
                OverlayReactionLogFilter.Reply,
            ),
        )
        assertFalse(
            OverlayReactionLogVisibilityPolicy.matchesFilter(
                plain,
                self,
                OverlayReactionLogFilter.Reply,
            ),
        )
    }

    @Test
    fun incoming_whenSenderIsNotSelf() {
        assertTrue(OverlayReactionLogVisibilityPolicy.isIncoming(entry(other, self), self))
    }

    @Test
    fun outgoing_whenSenderIsSelf() {
        assertTrue(OverlayReactionLogVisibilityPolicy.isOutgoing(entry(self, other), self))
    }

    @Test
    fun filter_incoming_excludesOutgoing() {
        val outgoing = entry(self, other)
        assertFalse(
            OverlayReactionLogVisibilityPolicy.matchesFilter(
                outgoing,
                self,
                OverlayReactionLogFilter.Incoming,
            ),
        )
    }

    @Test
    fun filter_outgoing_excludesIncoming() {
        val incoming = entry(other, self)
        assertFalse(
            OverlayReactionLogVisibilityPolicy.matchesFilter(
                incoming,
                self,
                OverlayReactionLogFilter.Outgoing,
            ),
        )
    }

    @Test
    fun broadcast_incoming_fromOther() {
        assertTrue(
            OverlayReactionLogVisibilityPolicy.isIncoming(
                entry(other, null, OverlayReactionLogVisibility.Broadcast),
                self,
            ),
        )
    }

    @Test
    fun scopeFilter_personal() {
        val personal = entry(other, self)
        val broadcast = entry(other, null, OverlayReactionLogVisibility.Broadcast)
        assertTrue(
            OverlayReactionLogVisibilityPolicy.matchesScopeFilter(
                personal,
                OverlayReactionLogScopeFilter.Personal,
            ),
        )
        assertFalse(
            OverlayReactionLogVisibilityPolicy.matchesScopeFilter(
                broadcast,
                OverlayReactionLogScopeFilter.Personal,
            ),
        )
    }

    @Test
    fun isEntryUnread_onlyIncomingAfterCursor() {
        val incoming = entry(other, self, id = "5")
        assertTrue(
            OverlayReactionLogVisibilityPolicy.isEntryUnread(incoming, self, "0"),
        )
        assertFalse(
            OverlayReactionLogVisibilityPolicy.isEntryUnread(incoming, self, incoming.id),
        )
        assertFalse(
            OverlayReactionLogVisibilityPolicy.isEntryUnread(entry(self, other), self, "0"),
        )
    }

    @Test
    fun isLogEntryAfterCursor_falseWhenCursorMatchesEntryId() {
        val logId = "674a1b2c3d4e5f6789012345"
        val read = entry(other, self, id = logId, createdAt = "2026-05-29T12:00:00Z")
        assertFalse(
            OverlayReactionLogVisibilityPolicy.isLogEntryAfterCursor(read, logId),
        )
    }

    @Test
    fun isLogEntryAfterCursor_usesCreatedAtWhenIdsAreNotEqual() {
        val newer = entry(
            sender = other,
            target = self,
            id = "000000000000000000000001",
            createdAt = "2026-05-29T12:00:00Z",
        )
        val oldCursor = "000000000000000000000099"
        assertTrue(
            OverlayReactionLogVisibilityPolicy.isLogEntryAfterCursor(newer, oldCursor),
        )
    }
}
