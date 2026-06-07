package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatSocketIngress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Overlay forward must not consume chat-list ingress before VM apply path.
 */
class ChatDualListenerTest {
    @Before
    fun setUp() {
        ChatSocketIngress.clear()
    }

    @Test
    fun overlayDirectApply_doesNotReclaimChatListIngress() {
        val room = "raid-room"
        val id = "507f1f77bcf86cd799439011"
        val msg = ChatMessage(
            _id = id,
            allianceId = "alliance-1",
            roomId = room,
            senderId = "peer-user",
            senderUsername = "peer",
            senderRole = "R1",
            text = "hello",
        )
        // VM primary path claims first (simulated)
        assertTrue(ChatSocketIngress.claimForChatList(room, id))
        // Overlay direct apply skips ingress — batch still applies
        assertFalse(ChatSocketIngress.claimForChatList(room, id))
        assertTrue(msg.roomId == room)
    }

    @Test
    fun unreadBumpIndependentFromListClaim() {
        val room = "hub-room"
        val id = "507f1f77bcf86cd799439012"
        assertTrue(ChatSocketIngress.claimForChatList(room, id))
        assertTrue(ChatSocketIngress.claimForUnreadBump(room, id))
        assertFalse(ChatSocketIngress.claimForUnreadBump(room, id))
    }
}
