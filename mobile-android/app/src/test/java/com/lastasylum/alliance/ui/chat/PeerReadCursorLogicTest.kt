package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatRoomReadEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeerReadCursorLogicTest {
    @Test
    fun mergePeerRead_updatesMapForNonSelectedRoom() {
        val map = mutableMapOf("roomA" to "000000000000000000000010")
        val publish = PeerReadCursorLogic.mergePeerReadEvent(
            otherReadUptoByRoom = map,
            selectedRoomId = "roomB",
            event = ChatRoomReadEvent(
                roomId = "roomA",
                userId = "peer",
                messageId = "000000000000000000000050",
            ),
            currentUserId = "self",
        )
        assertNull(publish)
        assertEquals("000000000000000000000050", map["roomA"])
    }

    @Test
    fun mergePeerRead_publishesWhenSelectedRoomMatches() {
        val map = mutableMapOf<String, String>()
        val publish = PeerReadCursorLogic.mergePeerReadEvent(
            otherReadUptoByRoom = map,
            selectedRoomId = "roomA",
            event = ChatRoomReadEvent(
                roomId = "roomA",
                userId = "peer",
                messageId = "000000000000000000000050",
            ),
            currentUserId = "self",
        )
        assertEquals("000000000000000000000050", publish)
        assertEquals("000000000000000000000050", map["roomA"])
    }

    @Test
    fun hydratePeerRead_publishesWhenSelectedRoomMatches() {
        val map = mutableMapOf<String, String>()
        val publish = PeerReadCursorLogic.hydratePeerRead(
            otherReadUptoByRoom = map,
            selectedRoomId = "roomA",
            roomId = "roomA",
            peerUptoMessageId = "000000000000000000000050",
        )
        assertEquals("000000000000000000000050", publish)
        assertEquals("000000000000000000000050", map["roomA"])
    }

    @Test
    fun mergePeerRead_afterRoomSwitch_usesStoredCursor() {
        val map = mutableMapOf("roomA" to "000000000000000000000060")
        val publish = PeerReadCursorLogic.mergePeerReadEvent(
            otherReadUptoByRoom = map,
            selectedRoomId = "roomA",
            event = ChatRoomReadEvent(
                roomId = "roomA",
                userId = "peer",
                messageId = "000000000000000000000040",
            ),
            currentUserId = "self",
        )
        assertNull(publish)
        assertEquals("000000000000000000000060", map["roomA"])
    }
}
