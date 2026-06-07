package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatSocketIngressTest {
    @Before
    fun setUp() {
        ChatSocketIngress.clear()
    }

    @Test
    fun markMessageNewSeen_dedupesSameRoomAndId() {
        assertTrue(ChatSocketIngress.markMessageNewSeen("room-1", "507f1f77bcf86cd799439011"))
        assertFalse(ChatSocketIngress.markMessageNewSeen("room-1", "507f1f77bcf86cd799439011"))
    }

    @Test
    fun markMessageNewSeen_blocksDualListenerSecondPath() {
        val room = "raid-room"
        val id = "507f1f77bcf86cd799439011"
        assertTrue(ChatSocketIngress.markMessageNewSeen(room, id))
        assertFalse(ChatSocketIngress.markMessageNewSeen(room, id))
    }

    @Test
    fun markMessageNewSeen_allowsDifferentRooms() {
        assertTrue(ChatSocketIngress.markMessageNewSeen("room-a", "507f1f77bcf86cd799439011"))
        assertTrue(ChatSocketIngress.markMessageNewSeen("room-b", "507f1f77bcf86cd799439011"))
    }
}
