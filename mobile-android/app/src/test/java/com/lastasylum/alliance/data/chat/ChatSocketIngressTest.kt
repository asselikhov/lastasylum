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
    fun claimForChatList_andUnreadBump_areIndependent() {
        val room = "raid-room"
        val id = "507f1f77bcf86cd799439011"
        assertTrue(ChatSocketIngress.claimForChatList(room, id))
        assertFalse(ChatSocketIngress.claimForChatList(room, id))
        assertTrue(ChatSocketIngress.claimForUnreadBump(room, id))
        assertFalse(ChatSocketIngress.claimForUnreadBump(room, id))
    }

    @Test
    fun markMessageNewSeen_allowsDifferentRooms() {
        assertTrue(ChatSocketIngress.markMessageNewSeen("room-a", "507f1f77bcf86cd799439011"))
        assertTrue(ChatSocketIngress.markMessageNewSeen("room-b", "507f1f77bcf86cd799439011"))
    }

    @Test
    fun lruEviction_doesNotReAdmitRecentIdWithinCapacity() {
        val room = "room-lru"
        val recentId = "507f1f77bcf86cd799439099"
        for (i in 0 until 4096) {
            val id = String.format("507f1f77bcf86cd7%06x", i)
            ChatSocketIngress.claimForChatList(room, id)
        }
        assertTrue(ChatSocketIngress.claimForChatList(room, recentId))
        assertFalse(ChatSocketIngress.claimForChatList(room, recentId))
    }
}
