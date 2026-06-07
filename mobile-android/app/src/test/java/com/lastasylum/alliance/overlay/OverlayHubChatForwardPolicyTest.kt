package com.lastasylum.alliance.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayHubChatForwardPolicyTest {
    @Test
    fun appliesWhenOverlayChatOpenOnSelectedHub() {
        assertTrue(
            OverlayHubChatForwardPolicy.shouldApplyToVisibleChat(
                overlayPanelVisible = true,
                overlayChatContentActive = true,
                selectedRoomId = "hub-room",
                messageRoomId = "hub-room",
                hubRoomId = "hub-room",
            ),
        )
    }

    @Test
    fun appliesWhenOverlayChatOpenOnHubEvenIfDifferentSelection() {
        assertTrue(
            OverlayHubChatForwardPolicy.shouldApplyToVisibleChat(
                overlayPanelVisible = true,
                overlayChatContentActive = true,
                selectedRoomId = "other-room",
                messageRoomId = "hub-room",
                hubRoomId = "hub-room",
            ),
        )
    }

    @Test
    fun skipsWhenOverlayPanelClosed() {
        assertFalse(
            OverlayHubChatForwardPolicy.shouldApplyToVisibleChat(
                overlayPanelVisible = false,
                overlayChatContentActive = true,
                selectedRoomId = "hub-room",
                messageRoomId = "hub-room",
                hubRoomId = "hub-room",
            ),
        )
    }

    @Test
    fun skipsWhenOverlayOnForumPane() {
        assertFalse(
            OverlayHubChatForwardPolicy.shouldApplyToVisibleChat(
                overlayPanelVisible = true,
                overlayChatContentActive = false,
                selectedRoomId = "hub-room",
                messageRoomId = "hub-room",
                hubRoomId = "hub-room",
            ),
        )
    }
}
