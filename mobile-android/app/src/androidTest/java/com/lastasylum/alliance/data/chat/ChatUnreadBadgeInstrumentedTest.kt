package com.lastasylum.alliance.data.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lastasylum.alliance.data.displayedUnreadCount
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.overlay.OverlayHubUnreadPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests on device/emulator for unread badge math used by app + overlay HUD.
 */
@RunWith(AndroidJUnit4::class)
class ChatUnreadBadgeInstrumentedTest {
    @Test
    fun hubUnreadGrace_twoSeconds() {
        assertEquals(2_000L, OverlayHubUnreadPolicy.RECONCILE_GRACE_MS)
    }

    @Test
    fun tabBadgeTotal_emptyRooms() {
        assertEquals(0, ChatUnreadCounts.tabBadgeTotal(emptyList()))
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
        assertEquals(
            0,
            displayedUnreadCount(
                effectiveUnread = 0,
                previouslyDisplayed = 5,
                rawServerUnread = 2,
            ),
        )
    }
}
