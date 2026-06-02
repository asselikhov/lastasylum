package com.lastasylum.alliance.data.chat

import com.lastasylum.alliance.overlay.OverlayReactionNotificationsDeriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val filtered = OverlayReactionNotificationsDeriver.filterEntries(
            all,
            self,
            OverlayReactionLogFilter.All,
            OverlayReactionLogScopeFilter.All,
            "",
        )
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = filtered,
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
    fun threadParent_listsAllReplyClusters() {
        val parentEntry = parent("p1")
        val replies = listOf(
            reply("r3", "p1"),
            reply("r2", "p1"),
            reply("r1", "p1"),
        )
        val all = listOf(parentEntry) + replies
        val filtered = OverlayReactionNotificationsDeriver.filterEntries(
            all,
            self,
            OverlayReactionLogFilter.All,
            OverlayReactionLogScopeFilter.All,
            "",
        )
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = filtered,
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.All,
        )
        val thread = feed.single() as OverlayReactionLogFeedItem.ThreadParent
        assertEquals(3, thread.replies.size)
        assertEquals(listOf("r3", "r2", "r1"), thread.replies.map { it.representative.id })
    }

    @Test
    fun outgoingParent_incomingReply_allFilter_threadsUnderParent() {
        val parentEntry = parent("p1", sender = self)
        val replyEntry = reply("r1", "p1", sender = other)
        val all = listOf(parentEntry, replyEntry)
        val filtered = OverlayReactionNotificationsDeriver.filterEntries(
            all,
            self,
            OverlayReactionLogFilter.All,
            OverlayReactionLogScopeFilter.All,
            "",
        )
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = filtered,
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.All,
        )
        assertEquals(1, feed.size)
        val thread = feed.first() as OverlayReactionLogFeedItem.ThreadParent
        assertEquals("p1", thread.parent.representative.id)
        assertEquals("r1", thread.replies.first().representative.id)
    }

    @Test
    fun incomingFilter_threadsWhenParentAndReplyBothIncoming() {
        val parentEntry = parent("p1", sender = other)
        val replyEntry = reply("r1", "p1", sender = other)
        val all = listOf(parentEntry, replyEntry)
        val filtered = OverlayReactionNotificationsDeriver.filterEntries(
            all,
            self,
            OverlayReactionLogFilter.Incoming,
            OverlayReactionLogScopeFilter.All,
            "",
        )
        assertEquals(2, filtered.size)
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = filtered,
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.Incoming,
        )
        assertEquals(1, feed.size)
        val thread = feed.first() as OverlayReactionLogFeedItem.ThreadParent
        assertEquals("p1", thread.parent.representative.id)
        assertEquals("r1", thread.replies.first().representative.id)
    }

    @Test
    fun incomingFilter_incomingReplyToOutgoingParent_showsStandaloneReply() {
        val parentEntry = parent("p1", sender = self)
        val replyEntry = reply("r1", "p1", sender = other)
        val all = listOf(parentEntry, replyEntry)
        val filtered = OverlayReactionNotificationsDeriver.filterEntries(
            all,
            self,
            OverlayReactionLogFilter.Incoming,
            OverlayReactionLogScopeFilter.All,
            "",
        )
        assertEquals(1, filtered.size)
        assertEquals("r1", filtered.first().id)
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = filtered,
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.Incoming,
        )
        assertEquals(1, feed.size)
        val root = feed.first() as OverlayReactionLogFeedItem.Root
        assertEquals("r1", root.cluster.representative.id)
        assertTrue(OverlayReactionLogReplyEnricher.isReplyEntry(root.cluster.representative))
    }

    @Test
    fun outgoingFilter_includesOwnReplyEntry() {
        val parentEntry = parent("p1", sender = other)
        val replyEntry = reply("r1", "p1", sender = self).copy(
            targetUserId = other,
            targetUsername = "Peer",
        )
        val all = listOf(parentEntry, replyEntry)
        val filtered = OverlayReactionNotificationsDeriver.filterEntries(
            all,
            self,
            OverlayReactionLogFilter.Outgoing,
            OverlayReactionLogScopeFilter.All,
            "",
        )
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = filtered,
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.Outgoing,
        )
        assertEquals(1, feed.size)
        val root = feed.first() as OverlayReactionLogFeedItem.Root
        assertEquals("r1", root.cluster.representative.id)
        assertTrue(OverlayReactionLogReplyEnricher.isReplyEntry(root.cluster.representative))
    }

    @Test
    fun incomingFilter_replyNotStandaloneWhenThreadedWithVisibleParent() {
        val parentEntry = parent("p1", sender = other)
        val replyEntry = reply("r1", "p1", sender = other)
        val all = listOf(parentEntry, replyEntry)
        val filtered = OverlayReactionNotificationsDeriver.filterEntries(
            all,
            self,
            OverlayReactionLogFilter.Incoming,
            OverlayReactionLogScopeFilter.All,
            "",
        )
        val feed = OverlayReactionLogFeedBuilder.buildFeedItems(
            filteredEntries = filtered,
            allEntries = all,
            selfUserId = self,
            directionFilter = OverlayReactionLogFilter.Incoming,
        )
        val replyRoots = feed.filterIsInstance<OverlayReactionLogFeedItem.Root>()
            .filter { OverlayReactionLogReplyEnricher.isReplyEntry(it.cluster.representative) }
        assertFalse(replyRoots.isNotEmpty())
    }
}
