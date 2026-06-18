package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatSocketIngress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun overlayApply_claimsChatListIngress() {
        val room = "raid-room"
        val id = "507f1f77bcf86cd799439011"
        assertTrue(ChatSocketIngress.claimForChatList(room, id))
        assertFalse(ChatSocketIngress.claimForChatList(room, id))
    }

    @Test
    fun overlayOwnMessageWithClientId_shouldConfirmNotBatch() {
        val room = "raid-room"
        val pending = "pending-abc"
        val server = "507f1f77bcf86cd799439099"
        val pendingMsg = ChatMessage(
            _id = pending,
            allianceId = "a1",
            roomId = room,
            senderId = "u1",
            senderUsername = "me",
            senderRole = "R1",
            text = "hello",
            clientMessageId = "cid-overlay-1",
        )
        val serverMsg = pendingMsg.copy(_id = server)
        assertEquals("cid-overlay-1", serverMsg.clientMessageId)
        assertNotEquals(pendingMsg._id, serverMsg._id)
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
