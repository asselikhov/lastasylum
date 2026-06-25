package com.lastasylum.alliance.data.chat.store

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageStoreFilterTest {

    private fun message(id: String, text: String) = ChatMessage(
        _id = id,
        allianceId = "a",
        roomId = "room",
        senderId = "u1",
        senderUsername = "alice",
        senderRole = "R1",
        text = text,
    )

    @Test
    fun durableStoreMessages_dropsOptimisticPendingRows() {
        val input = listOf(
            message("507f1f77bcf86cd799439011", "confirmed"),
            message("pending-abc123", "optimistic chat send"),
            message("overlay-pending-xyz", "optimistic overlay raid"),
        )
        val kept = durableStoreMessages(input)
        val keptIds = kept.mapNotNull { it._id }
        assertTrue("server message must be kept", "507f1f77bcf86cd799439011" in keptIds)
        assertFalse("pending- row must be dropped", "pending-abc123" in keptIds)
        assertFalse("overlay-pending- row must be dropped", "overlay-pending-xyz" in keptIds)
        assertEquals(1, kept.size)
    }

    @Test
    fun durableStoreMessages_keepsServerRowsAndDedupesById() {
        val input = listOf(
            message("507f1f77bcf86cd799439011", "newest"),
            message("507f1f77bcf86cd799439011", "duplicate id"),
            message("507f191e810c19729de860ea", "second"),
        )
        val kept = durableStoreMessages(input)
        assertEquals(2, kept.size)
        assertEquals(
            setOf("507f1f77bcf86cd799439011", "507f191e810c19729de860ea"),
            kept.mapNotNull { it._id }.toSet(),
        )
    }
}
