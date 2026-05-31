package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionLogUnreadTest {
    private val self = "user-self"
    private val other = "user-other"

    private fun entry(
        id: String,
        sender: String,
        target: String? = self,
        visibility: OverlayReactionLogVisibility = OverlayReactionLogVisibility.Personal,
        createdAt: String = "2026-05-29T12:00:00Z",
    ) = OverlayReactionLogEntry(
        id = id,
        senderUserId = sender,
        senderUsername = "A",
        targetUserId = target,
        targetUsername = "B",
        reaction = "heart",
        visibility = visibility,
        createdAt = createdAt,
    )

    @Test
    fun computeUnreadEntryIds_incomingWithoutCursor_allIncomingUnread() {
        val entries = listOf(
            entry(id = "b", sender = other),
            entry(id = "a", sender = self, target = other),
        )
        val unread = computeUnreadEntryIds(entries, self, lastSeenLogId = null)
        assertEquals(setOf("b"), unread)
    }

    @Test
    fun computeUnreadEntryIds_afterCursor_onlyNewerIncoming() {
        val entries = listOf(
            entry(
                id = "000000000000000000000001",
                sender = other,
                createdAt = "2026-05-29T11:00:00Z",
            ),
            entry(
                id = "000000000000000000000099",
                sender = other,
                createdAt = "2026-05-29T13:00:00Z",
            ),
        )
        val unread = computeUnreadEntryIds(
            entries = entries,
            selfUserId = self,
            lastSeenLogId = "6a197fc03d4e5f6789012345",
        )
        assertEquals(setOf("000000000000000000000099"), unread)
    }

    @Test
    fun computeUnreadEntryIds_outgoingNeverUnread() {
        val entries = listOf(entry(id = "1", sender = self, target = other))
        assertTrue(computeUnreadEntryIds(entries, self, null).isEmpty())
    }

    @Test
    fun computeUnreadEntryIds_broadcastIncomingIncluded() {
        val entries = listOf(
            entry(id = "1", sender = other, target = null, visibility = OverlayReactionLogVisibility.Broadcast),
        )
        assertEquals(setOf("1"), computeUnreadEntryIds(entries, self, null))
    }
}
