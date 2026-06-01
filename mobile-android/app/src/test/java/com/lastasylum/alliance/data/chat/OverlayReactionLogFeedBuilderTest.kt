package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionLogFeedBuilderTest {
    private val self = "user-self"
    private val other = "user-other"

    private fun parent(id: String, sender: String = self) = OverlayReactionLogEntry(
        id = id,
        senderUserId = sender,
        senderUsername = "Author",
        targetUserId = if (sender == self) other else self,
        targetUsername = "Peer",
        reaction = "heart",
        visibility = OverlayReactionLogVisibility.Personal,
        createdAt = "2026-05-29T10:00:00.000Z",
    )

    private fun reply(id: String, parentId: String, sender: String = other) = OverlayReactionLogEntry(
        id = id,
        senderUserId = sender,
        senderUsername = "Replier",
        targetUserId = self,
        targetUsername = "Author",
        reaction = "thumbsup",
        visibility = OverlayReactionLogVisibility.Personal,
        createdAt = "2026-05-29T10:00:01.000Z",
        replyToLog = OverlayReactionLogReplyTo(
            logId = parentId,
            reaction = "heart",
            visibility = OverlayReactionLogVisibility.Personal,
            senderUserId = self,
            senderUsername = "Author",
            targetUserId = other,
            targetUsername = "Peer",
        ),
    )

    @Test
    fun authorSeesReplyUnderThreadNotInRoot() {
        val parentEntry = parent("p1")
        val replyEntry = reply("r1", "p1")
        val all = listOf(parentEntry, replyEntry)
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = all,
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.All,
        )
        assertEquals(1, feed.size)
        val thread = feed.first() as OverlayReactionLogFeedItem.ThreadParent
        assertEquals("p1", thread.parent.representative.id)
        assertEquals(1, thread.replies.size)
        assertEquals("r1", thread.replies.first().representative.id)
    }

    @Test
    fun incomingParentWithReplies_showsThreadParent() {
        val parentEntry = parent("p1", sender = other)
        val replyEntry = reply("r1", "p1", sender = self).copy(
            targetUserId = other,
            targetUsername = "Peer",
        )
        val all = listOf(parentEntry, replyEntry)
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = all,
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.All,
        )
        assertEquals(1, feed.size)
        val thread = feed.first() as OverlayReactionLogFeedItem.ThreadParent
        assertEquals("p1", thread.parent.representative.id)
        assertEquals(1, thread.replies.size)
    }

    @Test
    fun replyFilterIsFlatList() {
        val parentEntry = parent("p1")
        val replyEntry = reply("r1", "p1")
        val all = listOf(parentEntry, replyEntry)
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = all.filter { it.replyToLog != null },
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.Reply,
        )
        assertEquals(1, feed.size)
        assertTrue(feed.first() is OverlayReactionLogFeedItem.Root)
        assertEquals("r1", (feed.first() as OverlayReactionLogFeedItem.Root).cluster.representative.id)
    }
}
