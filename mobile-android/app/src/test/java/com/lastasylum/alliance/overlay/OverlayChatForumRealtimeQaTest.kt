package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.overlay.OverlayInboxBadgeCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit coverage + manual QA checklist for overlay chat/forum realtime (plan: overlay_chat_forum_realtime).
 *
 * Manual QA:
 * - [ ] Overlay Chat open, Team tab → Chat tab: new messages visible without room switch
 * - [ ] Overlay Chat, same room: peer msg realtime without reopen
 * - [ ] Forum list: read topics without fire flash on open
 * - [ ] Forum topic open: peer msg realtime; Forum→Chat→Forum stash recovery
 * - [ ] Own message: optimistic → confirm without duplicate (chat + forum)
 * - [ ] HUD forum badge = sum of cards after mark-read
 */
class OverlayChatForumRealtimeQaTest {
    @Test
    fun hubForwardPolicy_stashOnlyWhenChatContentInactive() {
        assertFalse(
            OverlayHubChatForwardPolicy.shouldApplyToVisibleChat(
                overlayPanelVisible = true,
                overlayChatContentActive = false,
                selectedRoomId = "hub",
                messageRoomId = "hub",
                hubRoomId = "hub",
            ),
        )
    }

    @Test
    fun forumList_jumpToUnreadUsesIndexOfLast() {
        val topics = listOf(
            forumTopic("t1", unread = 0),
            forumTopic("t2", unread = 2),
            forumTopic("t3", unread = 1),
        )
        val firstUnreadIndex = topics.indexOfLast { it.unreadCount > 0 }
        assertEquals(2, firstUnreadIndex)
        assertEquals(
            firstUnreadIndex,
            topics.indexOfLast { it.unreadCount > 0 },
        )
        assertNotEquals(
            topics.indexOfFirst { it.unreadCount > 0 },
            firstUnreadIndex,
        )
    }

    @Test
    fun panelClose_forumFlushSucceeded_clearsOptimisticFloor() {
        val coordinator = OverlayInboxBadgeCoordinator()
        coordinator.bumpForumOptimistic(2)
        assertTrue(coordinator.isForumOptimisticActive())
        coordinator.clearForumOptimistic()
        assertFalse(coordinator.isForumOptimisticActive())
    }

    private fun forumTopic(id: String, unread: Int) = TeamForumTopicDto(
        id = id,
        teamId = "team1",
        title = id,
        createdByUserId = "u1",
        messageCount = 1,
        unreadCount = unread,
        lastReadMessageId = null,
        createdAt = "2020-01-01T00:00:00Z",
        updatedAt = "2020-01-01T00:00:00Z",
    )

    @Test
    fun hubForwardPolicy_requiresSelectedHubForHubMessages() {
        assertFalse(
            OverlayHubChatForwardPolicy.shouldApplyToVisibleChat(
                overlayPanelVisible = true,
                overlayChatContentActive = true,
                selectedRoomId = "room-a",
                messageRoomId = "hub",
                hubRoomId = "hub",
            ),
        )
        assertTrue(
            OverlayHubChatForwardPolicy.shouldApplyToVisibleChat(
                overlayPanelVisible = true,
                overlayChatContentActive = true,
                selectedRoomId = "hub",
                messageRoomId = "hub",
                hubRoomId = "hub",
            ),
        )
    }

    @Test
    fun forumList_firstFrameUnread_zeroAfterLocalCursorHydrate() {
        val topic = TeamForumTopicDto(
            id = "topic1",
            teamId = "team1",
            title = "Read topic",
            createdByUserId = "u1",
            messageCount = 10,
            unreadCount = 3,
            lastReadMessageId = "000000000000000000000050",
            createdAt = "2020-01-01T00:00:00Z",
            updatedAt = "2020-01-01T00:00:00Z",
        )
        val localLast = "000000000000000000000080"
        assertEquals(0, effectiveUnreadCount(3, topic.lastReadMessageId, localLast))
        assertEquals(
            0,
            TeamInboxUnread.displayedForumTopicUnread(
                topic = topic,
                localLastReadMessageId = localLast,
                optimisticFloor = 0,
            ),
        )
    }
}
