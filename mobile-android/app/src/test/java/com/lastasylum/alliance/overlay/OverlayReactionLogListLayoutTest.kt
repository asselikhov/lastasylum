package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.OverlayReactionLogCluster
import com.lastasylum.alliance.data.chat.OverlayReactionLogEntry
import com.lastasylum.alliance.data.chat.OverlayReactionLogFeedItem
import com.lastasylum.alliance.data.chat.OverlayReactionLogVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReactionLogListLayoutTest {

    @Test
    fun buildNewestFeedEntryIds_takesFirstFiveInNewestFirstFeedOrder() {
        val grouped = listOf(
            "day" to listOf(
                root("6"),
                root("5"),
                root("4"),
                root("3"),
                root("2"),
                root("1"),
            ),
        )
        assertEquals(listOf("6", "5", "4", "3", "2"), buildNewestFeedEntryIds(grouped))
    }

    @Test
    fun collectMarkReadIdsForThread_includesUnreadReplies() {
        val parent = entry("parent", "alice")
        val reply = entry("reply", "bob")
        val grouped = listOf(
            "day" to listOf(
                OverlayReactionLogFeedItem.ThreadParent(
                    parent = OverlayReactionLogCluster(listOf(parent)),
                    replies = listOf(OverlayReactionLogCluster(listOf(reply))),
                ),
            ),
        )
        val ids = collectMarkReadIdsForListKey(
            listKey = "thread-parent",
            groupedFeed = grouped,
            unreadEntryIds = setOf("parent", "reply"),
        )
        assertEquals(setOf("parent", "reply"), ids)
    }

    @Test
    fun parseOverlayReactionLogListKey_supportsClusterAndThread() {
        assertEquals("abc", parseOverlayReactionLogListKey("cluster-abc"))
        assertEquals("xyz", parseOverlayReactionLogListKey("thread-xyz"))
    }

    private fun root(id: String) = OverlayReactionLogFeedItem.Root(
        cluster = OverlayReactionLogCluster(listOf(entry(id, "alice"))),
    )

    private fun entry(id: String, sender: String) = OverlayReactionLogEntry(
        id = id,
        senderUserId = sender,
        senderUsername = sender,
        targetUserId = "self",
        targetUsername = "self",
        reaction = "heart",
        visibility = OverlayReactionLogVisibility.Personal,
        createdAt = "2026-05-29T10:00:00Z",
    )
}
