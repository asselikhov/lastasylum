package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStripMessageFilterTest {
    @Test
    fun raidMessage_matchesRaidOrBlankRoom() {
        val msg = message(roomId = "raid1")
        assertTrue(OverlayStripMessageRouter.isOverlayRaidRoomMessage(msg, "raid1"))
        assertTrue(OverlayStripMessageRouter.isOverlayRaidRoomMessage(message(roomId = ""), "raid1"))
        assertFalse(OverlayStripMessageRouter.isOverlayRaidRoomMessage(msg, "other"))
    }

    @Test
    fun raidMessage_matchesCachedRaidIdWhenPrefsStale() {
        val msg = message(roomId = "raid-cache")
        assertFalse(OverlayStripMessageRouter.isOverlayRaidRoomMessage(msg, "raid-prefs"))
        assertTrue(
            OverlayStripMessageRouter.isOverlayRaidRoomMessage(
                msg,
                "raid-prefs",
                extraRaidRoomIds = setOf("raid-cache"),
            ),
        )
    }

    @Test
    fun hubUnread_onlyForHubNotRaidDuplicate() {
        val hubMsg = message(roomId = "hub1")
        assertTrue(OverlayStripMessageRouter.shouldRouteHubUnread(hubMsg, "hub1", "raid1"))
        assertFalse(OverlayStripMessageRouter.shouldRouteHubUnread(hubMsg, "hub1", "hub1"))
    }

    private fun message(roomId: String) = ChatMessage(
        _id = "m1",
        allianceId = "pt:1",
        roomId = roomId,
        senderId = "u2",
        senderUsername = "A",
        senderRole = "R2",
        text = "hi",
    )
}
