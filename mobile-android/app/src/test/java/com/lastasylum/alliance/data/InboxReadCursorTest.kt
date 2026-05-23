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
    fun effectiveUnread_trustsServerWhenServerCursorMissing() {
        assertEquals(
            3,
            effectiveUnreadCount(
                serverUnread = 3,
                lastReadMessageId = null,
                localLastReadMessageId = "000000000000000000000040",
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
    fun reconcileDisplayedUnread_keepsOptimisticBumpUntilServerCatchedUp() {
        assertEquals(1, reconcileDisplayedUnread(serverUnread = 0, previouslyDisplayed = 1))
        assertEquals(2, reconcileDisplayedUnread(serverUnread = 2, previouslyDisplayed = 1))
        assertEquals(0, reconcileDisplayedUnread(serverUnread = 0, previouslyDisplayed = 0))
    }
}
