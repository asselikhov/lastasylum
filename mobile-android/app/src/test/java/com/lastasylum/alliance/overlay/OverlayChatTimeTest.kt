package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class OverlayChatTimeTest {
    @Test
    fun parseInstant_parsesIsoZulu() {
        val i = OverlayChatTime.parseInstant("2024-06-01T12:00:00Z")
        assertEquals(Instant.parse("2024-06-01T12:00:00Z"), i)
    }

    @Test
    fun parseInstant_blank_returnsNull() {
        assertNull(OverlayChatTime.parseInstant(""))
        assertNull(OverlayChatTime.parseInstant(null))
    }

    @Test
    fun effectiveInstant_usesMaxOfServerAndClient() {
        val msg = ChatMessage(
            _id = "1",
            allianceId = "ally",
            roomId = "r",
            senderId = "a",
            senderUsername = "u",
            senderRole = "R2",
            text = "hi",
            createdAt = "1970-01-01T00:00:00",
        )
        val key = msg.stableKey()
        val client = Instant.parse("2026-01-01T12:00:00Z")
        val received = mapOf(key to client)
        val eff = OverlayChatTime.effectiveInstant(msg, received)
        assertEquals(client, eff)
    }
}
