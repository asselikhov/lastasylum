package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OverlayChatStripBufferTest {
    @Test
    fun seedFromHistory_thenVisibleForPreview() {
        val buffer = OverlayChatStripBuffer(
            maxAgeMinutes = 60,
            bufferCap = 100,
            maxPreviewMessages = 3,
        )
        val created = Instant.now().toString()
        val msg = ChatMessage(
            _id = "abc123",
            allianceId = "ally",
            roomId = "room1",
            senderId = "u1",
            senderUsername = "Pilot",
            senderRole = "R2",
            text = "hello",
            createdAt = created,
        )
        buffer.seedFromHistory(listOf(msg))
        val visible = buffer.visibleForPreview()
        assertEquals(1, visible.size)
        assertEquals("hello", visible.first().text)
    }

    @Test
    fun clear_emptiesBuffer() {
        val buffer = OverlayChatStripBuffer()
        buffer.seedFromHistory(
            listOf(
                ChatMessage(
                    allianceId = "a",
                    roomId = "r",
                    senderId = "x",
                    senderUsername = "x",
                    senderRole = "R2",
                    text = "m",
                    createdAt = Instant.now().toString(),
                ),
            ),
        )
        buffer.clear()
        assertTrue(buffer.visibleForPreview().isEmpty())
    }
}
