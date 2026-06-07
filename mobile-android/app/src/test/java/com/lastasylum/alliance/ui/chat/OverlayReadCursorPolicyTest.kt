package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayReadCursorPolicyTest {
    private val peerMsg = ChatMessage(
        _id = "bbbbbbbbbbbbbbbbbbbbbbbb",
        allianceId = "a",
        roomId = "room",
        senderId = "peer",
        senderUsername = "Peer",
        senderRole = "R1",
        text = "hi",
    )
    private val ownMsg = peerMsg.copy(
        _id = "cccccccccccccccccccccccc",
        senderId = "self",
    )

    @Test
    fun computeOverlayViewportReadWatermark_advancesToNewestVisiblePeer() {
        val watermark = computeOverlayViewportReadWatermark(
            messageIds = listOf(
                "aaaaaaaaaaaaaaaaaaaaaaaa",
                peerMsg._id!!,
            ),
            messages = listOf(peerMsg),
            lastReadMessageId = "aaaaaaaaaaaaaaaaaaaaaaaa",
            currentUserId = "self",
            isValidMessageId = { it.isNotBlank() && !it.startsWith("pending-") },
        )
        assertEquals(peerMsg._id, watermark)
    }

    @Test
    fun computeOverlayViewportReadWatermark_skipsOwnMessages() {
        val watermark = computeOverlayViewportReadWatermark(
            messageIds = listOf(ownMsg._id!!),
            messages = listOf(ownMsg),
            lastReadMessageId = null,
            currentUserId = "self",
            isValidMessageId = { it.isNotBlank() && !it.startsWith("pending-") },
        )
        assertNull(watermark)
    }

    @Test
    fun computeOverlayViewportReadWatermark_respectsExistingCursor() {
        val watermark = computeOverlayViewportReadWatermark(
            messageIds = listOf("aaaaaaaaaaaaaaaaaaaaaaaa"),
            messages = listOf(peerMsg.copy(_id = "aaaaaaaaaaaaaaaaaaaaaaaa")),
            lastReadMessageId = "bbbbbbbbbbbbbbbbbbbbbbbb",
            currentUserId = "self",
            isValidMessageId = { it.isNotBlank() && !it.startsWith("pending-") },
        )
        assertNull(watermark)
    }

    @Test
    fun isOverlayRoomActivelyViewed_requiresChatTab() {
        assertFalse(
            isOverlayRoomActivelyViewed(
                overlayChatTabActive = false,
                appInForeground = true,
                fullscreenPanelVisible = true,
                overlayChatPanelOpenInGame = true,
            ),
        )
    }

    @Test
    fun isOverlayRoomActivelyViewed_trueWhenChatTabAndInGame() {
        assertTrue(
            isOverlayRoomActivelyViewed(
                overlayChatTabActive = true,
                appInForeground = false,
                fullscreenPanelVisible = false,
                overlayChatPanelOpenInGame = true,
            ),
        )
    }
}
