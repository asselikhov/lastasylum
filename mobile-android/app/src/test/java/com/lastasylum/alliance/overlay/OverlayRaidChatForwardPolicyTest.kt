package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRaidChatForwardPolicyTest {

    @Test
    fun appliesWhenSelectedRoomMatchesMessageRoom() {
        assertTrue(
            OverlayRaidChatForwardPolicy.shouldApplyToVisibleChat(
                selectedRoomId = "room-raid",
                messageRoomId = "room-raid",
            ),
        )
    }

    @Test
    fun skipsWhenSelectedRoomDiffers() {
        assertFalse(
            OverlayRaidChatForwardPolicy.shouldApplyToVisibleChat(
                selectedRoomId = "room-hub",
                messageRoomId = "room-raid",
            ),
        )
    }

    @Test
    fun skipsWhenSelectedOrMessageRoomBlank() {
        assertFalse(
            OverlayRaidChatForwardPolicy.shouldApplyToVisibleChat(
                selectedRoomId = null,
                messageRoomId = "room-raid",
            ),
        )
        assertFalse(
            OverlayRaidChatForwardPolicy.shouldApplyToVisibleChat(
                selectedRoomId = "room-raid",
                messageRoomId = "  ",
            ),
        )
    }

    @Test
    fun trimsWhitespaceBeforeCompare() {
        assertTrue(
            OverlayRaidChatForwardPolicy.shouldApplyToVisibleChat(
                selectedRoomId = " room-raid ",
                messageRoomId = "room-raid",
            ),
        )
    }
}
