package com.lastasylum.alliance.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxReadCursorTest {
    @Test
    fun effectiveUnread_returnsServerCount_whenNoLocalCursor() {
        assertEquals(
            4,
            effectiveUnreadCount(
                serverUnread = 4,
                lastReadMessageId = "000000000000000000000050",
                localLastReadMessageId = null,
            ),
        )
    }

    @Test
    fun effectiveUnread_trustsServerCount_whenLocalReadAheadOfServerCursor() {
        assertEquals(
            0,
            effectiveUnreadCount(
                serverUnread = 3,
                lastReadMessageId = "000000000000000000000040",
                localLastReadMessageId = "000000000000000000000080",
            ),
        )
    }

    @Test
    fun effectiveUnread_suppressesStaleServerCount_whenCursorsEqual() {
        assertEquals(
            0,
            effectiveUnreadCount(
                serverUnread = 2,
                lastReadMessageId = "000000000000000000000070",
                localLastReadMessageId = "000000000000000000000070",
            ),
        )
    }

    @Test
    fun effectiveUnread_suppressesWhenLocalCursorPresentButServerMissing() {
        assertEquals(
            0,
            effectiveUnreadCount(
                serverUnread = 3,
                lastReadMessageId = null,
                localLastReadMessageId = "000000000000000000000040",
            ),
        )
    }

    @Test
    fun effectiveUnread_showsServerCount_whenServerMissingCursorAndNoLocal() {
        assertEquals(
            3,
            effectiveUnreadCount(
                serverUnread = 3,
                lastReadMessageId = null,
                localLastReadMessageId = null,
            ),
        )
    }

    @Test
    fun effectiveUnread_keepsCount_whenServerReadAheadOfLocal() {
        assertEquals(
            2,
            effectiveUnreadCount(
                serverUnread = 2,
                lastReadMessageId = "000000000000000000000090",
                localLastReadMessageId = "000000000000000000000050",
            ),
        )
    }

    @Test
    fun displayedUnread_doesNotStackWithPreviouslyClearedBadge() {
        assertEquals(
            1,
            displayedUnreadCount(
                effectiveUnread = 1,
                previouslyDisplayed = 9,
                rawServerUnread = 1,
            ),
        )
    }

    @Test
    fun displayedUnread_keepsOptimisticBumpUntilServerCatchedUp() {
        assertEquals(
            1,
            displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 0,
                rawServerUnread = 0,
                optimisticFloor = 1,
            ),
        )
        assertEquals(2, displayedUnreadCount(effectiveUnread = 2, previouslyDisplayed = 1))
        assertEquals(0, displayedUnreadCount(effectiveUnread = 0, previouslyDisplayed = 5))
    }

    @Test
    fun displayedUnread_clearsWhenLocalReadSuppressesStaleServer() {
        assertEquals(
            0,
            displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 5,
                rawServerUnread = 3,
            ),
        )
    }

    @Test
    fun displayedUnread_honorsOptimisticFloorWhenRawMatchesSocketBump() {
        assertEquals(
            1,
            displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 0,
                rawServerUnread = 1,
                optimisticFloor = 1,
            ),
        )
    }

    @Test
    fun displayedUnread_clearsOptimisticFloorWhenLocalReadSuppressesStaleServer() {
        assertEquals(
            0,
            displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 1,
                rawServerUnread = 3,
                optimisticFloor = 2,
            ),
        )
    }

    @Test
    fun displayedUnread_staysZeroWhenRawPositiveButLocalReadSuppressesEffective() {
        assertEquals(
            0,
            displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 3,
                rawServerUnread = 8,
                optimisticFloor = 0,
            ),
        )
    }

    @Test
    fun shouldClearOptimisticFloor_retainsDuringGraceWhenServerStillZero() {
        val now = 10_000L
        assertFalse(
            shouldClearOptimisticUnreadFloor(
                floor = 1,
                rawServerUnread = 0,
                displayedUnread = 1,
                lastBumpAtMs = now - 500L,
                nowMs = now,
                graceMs = 4_000L,
            ),
        )
        assertFalse(
            shouldClearOptimisticUnreadFloor(
                floor = 1,
                rawServerUnread = 0,
                displayedUnread = 0,
                lastBumpAtMs = now - 500L,
                nowMs = now,
                graceMs = 4_000L,
            ),
        )
    }

    @Test
    fun shouldClearOptimisticFloor_clearsWhenGraceExpiredAndServerZero() {
        val now = 10_000L
        assertTrue(
            shouldClearOptimisticUnreadFloor(
                floor = 1,
                rawServerUnread = 0,
                displayedUnread = 0,
                lastBumpAtMs = now - 5_000L,
                nowMs = now,
                graceMs = 4_000L,
            ),
        )
    }

    /**
     * Regression: a previously-read room (server read-cursor == local cursor, so effective is
     * suppressed to 0) gets a fresh socket message. The badge shows 1 via the optimistic floor and
     * must NOT be cleared just because the server's raw count caught up to the floor — otherwise the
     * tab/overlay badge collapses ~1s after every new message in an already-opened room.
     */
    @Test
    fun shouldClearOptimisticFloor_keepsWhileBadgeShownEvenWhenServerCaughtUp() {
        val now = 10_000L
        assertFalse(
            shouldClearOptimisticUnreadFloor(
                floor = 1,
                rawServerUnread = 1,
                displayedUnread = 1,
                lastBumpAtMs = now - 500L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun shouldClearOptimisticFloor_clearsWhenLocalReadSuppressesStaleServer() {
        assertTrue(
            shouldClearOptimisticUnreadFloor(
                floor = 2,
                rawServerUnread = 3,
                displayedUnread = 0,
            ),
        )
    }

    @Test
    fun displayedUnread_doesNotResurrectFloorWhenEffectiveZeroAndRawPositive() {
        assertEquals(
            0,
            displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 2,
                rawServerUnread = 5,
                optimisticFloor = 3,
            ),
        )
    }

    @Test
    fun displayedForumTopicUnread_keepsOptimisticFloorWhenCursorsEqual() {
        val topic = com.lastasylum.alliance.data.teams.TeamForumTopicDto(
            id = "topic1",
            teamId = "team1",
            title = "Topic",
            createdByUserId = "u1",
            messageCount = 5,
            unreadCount = 1,
            lastReadMessageId = "000000000000000000000070",
            createdAt = "2020-01-01T00:00:00Z",
            updatedAt = "2020-01-01T00:00:00Z",
        )
        assertEquals(
            0,
            com.lastasylum.alliance.data.teams.TeamInboxUnread.displayedForumTopicUnread(
                topic = topic,
                localLastReadMessageId = "000000000000000000000070",
                optimisticFloor = 0,
            ),
        )
        assertEquals(
            1,
            com.lastasylum.alliance.data.teams.TeamInboxUnread.displayedForumTopicUnread(
                topic = topic,
                localLastReadMessageId = "000000000000000000000070",
                optimisticFloor = 1,
            ),
        )
    }

    @Test
    fun displayedForumTopicUnread_seedsFromServerRawWhenFloorMatchesUnread() {
        val topic = com.lastasylum.alliance.data.teams.TeamForumTopicDto(
            id = "topic1",
            teamId = "team1",
            title = "Topic",
            createdByUserId = "u1",
            messageCount = 8,
            unreadCount = 3,
            lastReadMessageId = "000000000000000000000070",
            createdAt = "2020-01-01T00:00:00Z",
            updatedAt = "2020-01-01T00:00:00Z",
        )
        assertEquals(
            3,
            com.lastasylum.alliance.data.teams.TeamInboxUnread.displayedForumTopicUnread(
                topic = topic,
                localLastReadMessageId = "000000000000000000000070",
                optimisticFloor = 3,
            ),
        )
    }

    @Test
    fun reconcileDisplayedUnread_clearsWhenServerZero() {
        assertEquals(0, reconcileDisplayedUnread(serverUnread = 0, previouslyDisplayed = 5))
    }

    @Test
    fun computeDisplayedUnread_mergesLocalCursorAndOptimisticFloor() {
        assertEquals(
            2,
            computeDisplayedUnread(
                serverUnread = 0,
                lastReadMessageId = null,
                localLastReadMessageId = null,
                optimisticFloor = 2,
                previouslyDisplayed = 1,
            ),
        )
        assertEquals(
            0,
            computeDisplayedUnread(
                serverUnread = 5,
                lastReadMessageId = "000000000000000000000040",
                localLastReadMessageId = "000000000000000000000100",
                optimisticFloor = 3,
                previouslyDisplayed = 4,
            ),
        )
    }
}
