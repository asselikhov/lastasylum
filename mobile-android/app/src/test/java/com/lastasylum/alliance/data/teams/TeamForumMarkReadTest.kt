package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.teams.TeamInboxUnread.displayedForumTopicUnread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamForumMarkReadTest {
    private fun topic(id: String, unread: Int, lastRead: String? = null) = TeamForumTopicDto(
        id = id,
        teamId = "team1",
        title = "Topic $id",
        createdByUserId = "u1",
        messageCount = 3,
        unreadCount = unread,
        lastReadMessageId = lastRead,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun displayedForumTopicUnread_includesOptimisticFloorWhenEffectiveZero() {
        val t = topic("t1", unread = 0)
        val displayed = displayedForumTopicUnread(
            topic = t,
            localLastReadMessageId = null,
            optimisticFloor = 2,
        )
        assertEquals(2, displayed)
    }

    @Test
    fun displayedForumTopicUnread_suppressedByLocalCursorDespiteStaleServer() {
        val localRead = "507f1f77bcf86cd799439099"
        val t = topic("t1", unread = 4, lastRead = "507f1f77bcf86cd799439011")
        val displayed = displayedForumTopicUnread(
            topic = t,
            localLastReadMessageId = localRead,
            optimisticFloor = 0,
        )
        assertEquals(0, displayed)
    }

    @Test
    fun markAllResult_emptyWhenNoUnreadTopics() {
        val result = TeamForumMarkRead.MarkAllTopicsReadResult(emptyMap())
        assertTrue(result.markedTopics.isEmpty())
    }
}
