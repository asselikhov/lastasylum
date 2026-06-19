package com.lastasylum.alliance.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayViewportReadPolicyTest {
    private val messages = listOf(
        com.lastasylum.alliance.data.chat.ChatMessage(
            _id = "000000000000000000000010",
            allianceId = "a1",
            senderId = "peer",
            senderUsername = "Peer",
            senderRole = "R1",
            text = "hi",
        ),
        com.lastasylum.alliance.data.chat.ChatMessage(
            _id = "000000000000000000000020",
            allianceId = "a1",
            senderId = "self",
            senderUsername = "Self",
            senderRole = "R1",
            text = "mine",
        ),
        com.lastasylum.alliance.data.chat.ChatMessage(
            _id = "000000000000000000000030",
            allianceId = "a1",
            senderId = "peer",
            senderUsername = "Peer",
            senderRole = "R1",
            text = "new",
        ),
    )

    @Test
    fun watermark_skipsSelfAndAlreadyRead() {
        val mark = computeOverlayViewportReadWatermark(
            messageIds = listOf(
                "000000000000000000000010",
                "000000000000000000000020",
                "000000000000000000000030",
            ),
            messages = messages,
            lastReadMessageId = "000000000000000000000010",
            currentUserId = "self",
            isValidMessageId = { it.isNotBlank() && !it.startsWith("pending-") },
        )
        assertEquals("000000000000000000000030", mark)
    }

    @Test
    fun mainAppRoomActivelyViewed_requiresChatTabForPeerTraffic() {
        assertEquals(
            true,
            isMainAppRoomActivelyViewed(
                isChatTabActive = true,
                isTargetGameForeground = false,
                isPeerMessage = true,
            ),
        )
        assertEquals(
            false,
            isMainAppRoomActivelyViewed(
                isChatTabActive = false,
                isTargetGameForeground = false,
                isPeerMessage = true,
            ),
        )
    }
}
