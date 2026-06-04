package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPinTest {
    @Test
    fun toPinnedPreview_returnsNullWithoutId() {
        val msg = ChatMessage(
            _id = null,
            allianceId = "pt:team1",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "hi",
        )
        assertNull(msg.toPinnedPreview())
    }

    @Test
    fun toPinnedPreview_mapsFields() {
        val msg = ChatMessage(
            _id = "507f1f77bcf86cd799439014",
            allianceId = "pt:team1",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R5",
            text = "pinned text",
            createdAt = "2026-01-01T00:00:00Z",
        )
        val preview = msg.toPinnedPreview()
        assertNotNull(preview)
        assertEquals("507f1f77bcf86cd799439014", preview!!.id)
        assertEquals("pinned text", preview.text)
        assertEquals("alice", preview.senderUsername)
    }
}
