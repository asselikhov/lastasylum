package com.lastasylum.alliance.data.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class OverlaySocketMessageStashTest {
    private val roomId = "507f1f77bcf86cd799439011"
    private val peerId = "507f1f77bcf86cd799439012"

    @Before
    fun setUp() {
        ChatSessionCache.clear()
    }

    @Test
    fun stash_persistsPeerMessageWithoutViewModel() {
        val msg = ChatMessage(
            _id = peerId,
            allianceId = "a1",
            roomId = roomId,
            senderId = "peer-user",
            senderUsername = "peer",
            senderRole = "R1",
            text = "hello from overlay",
        )
        OverlaySocketMessageStash.stash(msg)
        val cached = ChatSessionCache.getFreshMessages(roomId)
        assertNotNull(cached)
        assertEquals(1, cached!!.size)
        assertEquals(peerId, cached.first()._id)
        assertEquals("hello from overlay", cached.first().text)
    }

    @Test
    fun stash_upsertsByMessageId() {
        val msg = ChatMessage(
            _id = peerId,
            allianceId = "a1",
            roomId = roomId,
            senderId = "peer-user",
            senderUsername = "peer",
            senderRole = "R1",
            text = "v1",
        )
        OverlaySocketMessageStash.stash(msg)
        OverlaySocketMessageStash.stash(msg.copy(text = "v2"))
        val cached = ChatSessionCache.getFreshMessages(roomId)!!
        assertEquals(1, cached.size)
        assertEquals("v2", cached.first().text)
    }
}
