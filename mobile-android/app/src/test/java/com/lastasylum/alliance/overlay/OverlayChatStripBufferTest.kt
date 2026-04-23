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
            messageTtlSeconds = 3600,
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

    @Test
    fun prune_drops_messages_older_than_ttl_by_effective_instant() {
        val ttl = OverlayChatStripBuffer.DEFAULT_MESSAGE_TTL_SECONDS
        val buffer = OverlayChatStripBuffer(
            messageTtlSeconds = ttl,
            bufferCap = 100,
            maxPreviewMessages = 10,
        )
        val oldCreated = Instant.now().minusSeconds(ttl + 30).toString()
        val newCreated = Instant.now().toString()
        buffer.seedFromHistory(
            listOf(
                ChatMessage(
                    _id = "old1",
                    allianceId = "a",
                    roomId = "r",
                    senderId = "u-old",
                    senderUsername = "Old",
                    senderRole = "R2",
                    text = "stale",
                    createdAt = oldCreated,
                ),
                ChatMessage(
                    _id = "new1",
                    allianceId = "a",
                    roomId = "r",
                    senderId = "u-new",
                    senderUsername = "New",
                    senderRole = "R3",
                    text = "fresh",
                    createdAt = newCreated,
                ),
            ),
        )
        buffer.prune()
        val visible = buffer.visibleForPreview()
        assertEquals(1, visible.size)
        assertEquals("fresh", visible.first().text)
    }
}
