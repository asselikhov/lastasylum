package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStripRecipientPolicyTest {

    @Test
    fun hidesSelfMessages() {
        val msg = ChatMessage(
            allianceId = "a",
            roomId = "r",
            senderId = "user-1",
            senderUsername = "Pilot",
            senderRole = "R2",
            text = "hello",
        )
        assertFalse(OverlayStripRecipientPolicy.shouldShowIncomingStripCard(msg, "user-1"))
    }

    @Test
    fun showsTeammateMessages() {
        val msg = ChatMessage(
            allianceId = "a",
            roomId = "r",
            senderId = "user-2",
            senderUsername = "Ally",
            senderRole = "R2",
            text = "hello",
        )
        assertTrue(OverlayStripRecipientPolicy.shouldShowIncomingStripCard(msg, "user-1"))
    }

    @Test
    fun showsServiceNotices() {
        val notice = ChatMessage(
            _id = OverlayStripNoticeIds.NO_RAID,
            allianceId = "",
            roomId = "",
            senderId = "",
            senderUsername = "",
            senderRole = "",
            text = "notice",
        )
        assertTrue(OverlayStripRecipientPolicy.shouldShowIncomingStripCard(notice, "user-1"))
    }
}
