package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.ui.chat.shouldSkipBackgroundMessageRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayChatPanelLifecycleTest {
    @Test
    fun skipBackgroundRefresh_whenSessionCacheAheadOfVisible() {
        val messages = (1..20).map { i ->
            ChatMessage(
                _id = "507f1f77bcf86cd7994390${i.toString().padStart(2, '0')}",
                allianceId = "",
                roomId = "room1",
                senderId = "u1",
                senderUsername = "u",
                senderRole = "R1",
                text = "m$i",
                createdAt = "2026-01-01T00:00:00Z",
            )
        }
        assertTrue(
            shouldSkipBackgroundMessageRefresh(
                visible = messages.take(15),
                sessionCache = messages,
                roomCache = null,
                pageSize = 30,
            ),
        )
    }

    @Test
    fun overlayHubGrace_twoSeconds() {
        assertTrue(OverlayHubUnreadPolicy.RECONCILE_GRACE_MS == 2_000L)
    }
}
