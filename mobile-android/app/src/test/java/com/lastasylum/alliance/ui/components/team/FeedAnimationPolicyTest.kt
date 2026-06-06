package com.lastasylum.alliance.ui.components.team

import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedAnimationPolicyTest {

    @Test
    fun forumTopicAnimationTier_fullForTopVisibleUnread() {
        assertEquals(
            FeedAnimationTier.Full,
            forumTopicAnimationTier(
                unread = true,
                isVisible = true,
                visibleUnreadRank = 0,
                listSize = 10,
                sectionActive = true,
                overlayMode = false,
            ),
        )
    }

    @Test
    fun forumTopicAnimationTier_liteForRank3() {
        assertEquals(
            FeedAnimationTier.Lite,
            forumTopicAnimationTier(
                unread = true,
                isVisible = true,
                visibleUnreadRank = 3,
                listSize = 10,
                sectionActive = true,
                overlayMode = false,
            ),
        )
    }

    @Test
    fun forumTopicAnimationTier_offWhenNotVisible() {
        assertEquals(
            FeedAnimationTier.Off,
            forumTopicAnimationTier(
                unread = true,
                isVisible = false,
                visibleUnreadRank = 0,
                listSize = 10,
                sectionActive = true,
                overlayMode = false,
            ),
        )
    }

    @Test
    fun forumTopicAnimationTier_overlayLargeListCapsToOff() {
        assertEquals(
            FeedAnimationTier.Off,
            forumTopicAnimationTier(
                unread = true,
                isVisible = true,
                visibleUnreadRank = 0,
                listSize = 40,
                sectionActive = true,
                overlayMode = true,
            ),
        )
    }

    @Test
    fun forumTopicAnimationTier_warmActivityUsesLite() {
        assertEquals(
            FeedAnimationTier.Lite,
            forumTopicAnimationTier(
                unread = false,
                isVisible = true,
                visibleUnreadRank = -1,
                listSize = 10,
                sectionActive = true,
                overlayMode = false,
                warmActivity = true,
            ),
        )
    }

    @Test
    fun buildVisibleUnreadRankMap_assignsSequentialRanks() {
        val ranks = buildVisibleUnreadRankMap(listOf(1, 3, 5)) { it == 3 || it == 5 }
        assertEquals(0, ranks[3])
        assertEquals(1, ranks[5])
        assertTrue(1 !in ranks)
    }

    @Test
    fun filterForumTopics_searchAndUnread() {
        val topics = listOf(
            topic(id = "1", title = "Alpha raid", unread = 2),
            topic(id = "2", title = "Beta chat", unread = 0),
        )
        val filtered = filterForumTopics(
            topics = topics,
            query = "alpha",
            filter = ForumTopicListFilter.Unread,
            unreadAt = { it.unreadCount },
        )
        assertEquals(1, filtered.size)
        assertEquals("1", filtered.first().id)
    }

    private fun topic(id: String, title: String, unread: Int) = TeamForumTopicDto(
        id = id,
        teamId = "t1",
        title = title,
        createdByUserId = "u1",
        messageCount = 1,
        unreadCount = unread,
        createdAt = "2026-01-01T00:00:00.000Z",
        updatedAt = "2026-01-01T00:00:00.000Z",
    )
}
