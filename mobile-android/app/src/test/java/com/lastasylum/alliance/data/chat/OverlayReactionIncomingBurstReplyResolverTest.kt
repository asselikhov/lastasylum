package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionIncomingBurstReplyResolverTest {

    @Test
    fun resolve_prefersInlineReplyToLogOnEvent() {
        val parent = burstReply("parent", "fire")
        val resolved = OverlayReactionIncomingBurstReplyResolver.resolve(
            event = OverlayReactionEvent(
                fromUserId = "a",
                fromUsername = "Alice",
                reaction = "heart",
                targetUserId = "b",
                replyToLog = parent,
            ),
        )
        assertEquals(parent, resolved)
    }

    @Test
    fun resolve_fallsBackToParentEntryByReplyToLogId() {
        val parent = entry("parent", "bob", reaction = "star")
        val resolved = OverlayReactionIncomingBurstReplyResolver.resolve(
            event = OverlayReactionEvent(
                fromUserId = "bob",
                fromUsername = "Bob",
                reaction = "heart",
                targetUserId = "me",
                replyToLogId = "parent",
            ),
            entries = listOf(parent),
        )
        assertNotNull(resolved)
        assertEquals("parent", resolved?.logId)
        assertEquals("star", resolved?.reaction)
    }

    @Test
    fun resolve_usesLogDtoReplySnapshot() {
        val resolved = OverlayReactionIncomingBurstReplyResolver.resolve(
            event = OverlayReactionEvent(
                fromUserId = "bob",
                fromUsername = "Bob",
                reaction = "heart",
                targetUserId = "me",
                logEntryId = "reply1",
                replyToLogId = "parent",
            ),
            logDto = OverlayReactionLogEntryDto(
                _id = "reply1",
                senderUserId = "bob",
                senderUsername = "Bob",
                reaction = "heart",
                visibility = "personal",
                createdAt = "2026-06-02T12:00:00Z",
                replyToLog = OverlayReactionLogReplyToDto(
                    _id = "parent",
                    reaction = "fire",
                    visibility = "personal",
                    senderUserId = "me",
                    senderUsername = "Me",
                ),
            ),
        )
        assertNotNull(resolved)
        assertEquals("parent", resolved?.logId)
        assertEquals("fire", resolved?.reaction)
    }

    @Test
    fun isReplyEvent_trueWhenOnlyReplyToLogId() {
        assertTrue(
            OverlayReactionIncomingBurstReplyResolver.isReplyEvent(
                OverlayReactionEvent(
                    fromUserId = "a",
                    fromUsername = "A",
                    reaction = "heart",
                    targetUserId = "b",
                    replyToLogId = "p1",
                ),
            ),
        )
    }

    @Test
    fun resolve_returnsNullForPlainPersonalWithoutParent() {
        val resolved = OverlayReactionIncomingBurstReplyResolver.resolve(
            event = OverlayReactionEvent(
                fromUserId = "a",
                fromUsername = "A",
                reaction = "heart",
                targetUserId = "b",
            ),
        )
        assertNull(resolved)
    }

    private fun burstReply(id: String, reaction: String) = OverlayReactionBurstReplyTo(
        logId = id,
        reaction = reaction,
        visibility = OverlayReactionLogVisibility.Personal,
    )

    private fun entry(
        id: String,
        sender: String,
        reaction: String = "heart",
    ) = OverlayReactionLogEntry(
        id = id,
        senderUserId = sender,
        senderUsername = sender,
        targetUserId = "me",
        targetUsername = "Me",
        reaction = reaction,
        createdAt = "2026-06-02T12:00:00Z",
        visibility = OverlayReactionLogVisibility.Personal,
    )
}
