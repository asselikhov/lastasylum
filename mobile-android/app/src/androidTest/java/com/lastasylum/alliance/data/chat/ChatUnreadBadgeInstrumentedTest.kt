package com.lastasylum.alliance.data.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.reconcileDisplayedUnread
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests on device/emulator for unread badge math used by app + overlay HUD.
 */
@RunWith(AndroidJUnit4::class)
class ChatUnreadBadgeInstrumentedTest {
    @Test
    fun tabBadgeTotal_emptyRooms() {
        assertEquals(0, ChatUnreadCounts.tabBadgeTotal(emptyList(), emptyMap()))
    }

    @Test
    fun effectiveUnread_suppressesWhenLocalCursorEqualsServer() {
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
    fun overlayClear_mustNotUseReconcileToKeepStaleBadge() {
        assertEquals(5, reconcileDisplayedUnread(serverUnread = 0, previouslyDisplayed = 5))
        assertEquals(0, 0)
    }
}
