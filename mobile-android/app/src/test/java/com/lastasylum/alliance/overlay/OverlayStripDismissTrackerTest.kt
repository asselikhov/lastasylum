package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStripDismissTrackerTest {
    @After
    fun clear() {
        OverlayStripDismissTracker.clear()
    }

    @Test
    fun dismissedMessage_staysHiddenAfterCatchUp() {
        val msg = ChatMessage(
            _id = "server-msg-1",
            allianceId = "a1",
            roomId = "raid1",
            senderId = "self",
            senderUsername = "me",
            senderRole = "member",
            text = "quick command",
            createdAt = "2026-01-01T00:00:00Z",
            clientMessageId = "client-abc",
        )
        OverlayStripDismissTracker.markDismissed(msg)
        assertTrue(OverlayStripDismissTracker.isDismissed(msg))
        assertTrue(
            OverlayStripDismissTracker.isDismissed(
                msg.copy(_id = "server-msg-1", clientMessageId = "client-abc"),
            ),
        )
    }

    @Test
    fun clear_resetsDismissState() {
        val msg = ChatMessage(
            _id = "m1",
            allianceId = "a1",
            roomId = "r1",
            senderId = "u1",
            senderUsername = "u",
            senderRole = "member",
            text = "hi",
            createdAt = "2026-01-01T00:00:00Z",
        )
        OverlayStripDismissTracker.markDismissed(msg)
        OverlayStripDismissTracker.clear()
        assertFalse(OverlayStripDismissTracker.isDismissed(msg))
    }
}
