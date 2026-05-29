package com.lastasylum.alliance.data

import org.junit.Assert.assertEquals
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
    fun displayedUnread_keepsOptimisticFloorWhenLocalReadSuppressesStaleServer() {
        assertEquals(
            2,
            displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 1,
                rawServerUnread = 3,
                optimisticFloor = 2,
            ),
        )
    }

    @Test
    fun reconcileDisplayedUnread_clearsWhenServerZero() {
        assertEquals(0, reconcileDisplayedUnread(serverUnread = 0, previouslyDisplayed = 5))
    }
}
