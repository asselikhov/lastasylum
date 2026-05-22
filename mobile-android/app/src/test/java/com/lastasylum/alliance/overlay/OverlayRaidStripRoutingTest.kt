package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatSessionCache
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRaidStripRoutingTest {
    @After
    fun tearDown() {
        ChatSessionCache.clear()
    }

    @Test
    fun acceptsRaid_whenRoomIdInCachedRaidRoom() {
        ChatSessionCache.update(
            listOf(
                ChatRoomDto(
                    id = "raid-room-1",
                    allianceId = "pt:abc",
                    title = "Рейд",
                    sortOrder = 2,
                ),
            ),
        )
        val msg = message(roomId = "raid-room-1")
        assertTrue(
            OverlayRaidStripRouting.acceptsRaidStripMessage(
                msg,
                prefsRaidId = null,
            ),
        )
    }

    @Test
    fun invokesCallback_whenResolvedFromRoomList() {
        ChatSessionCache.update(
            listOf(
                ChatRoomDto(
                    id = "raid-room-2",
                    allianceId = "pt:abc",
                    title = "Рейд",
                    sortOrder = 2,
                ),
            ),
        )
        var resolved: String? = null
        val msg = message(roomId = "raid-room-2")
        assertTrue(
            OverlayRaidStripRouting.acceptsRaidStripMessage(
                msg,
                prefsRaidId = null,
                onRaidRoomIdResolved = { resolved = it },
            ),
        )
        assertTrue(resolved == "raid-room-2")
    }

    private fun message(roomId: String) = ChatMessage(
        _id = "m1",
        allianceId = "pt:1",
        roomId = roomId,
        senderId = "u2",
        senderUsername = "A",
        senderRole = "R2",
        text = "hi",
    )
}
