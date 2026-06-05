package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModerationTest {
    @Test
    fun canPinChatMessage_allowsR4InTeamRoom() {
        assertTrue(canPinChatMessage("pt:team1", "R4"))
    }

    @Test
    fun canPinChatMessage_deniesGlobalRoom() {
        assertFalse(canPinChatMessage("__global__", "R5"))
    }

    @Test
    fun resolveChatPinAllianceId_fallsBackToMessageScope() {
        val room = ChatRoomDto(id = "room1", allianceId = null, title = "Рейд")
        val message = ChatMessage(
            _id = "m1",
            allianceId = "pt:team1",
            roomId = "room1",
            senderId = "u1",
            senderUsername = "alice",
            senderRole = "R1",
            text = "hi",
            createdAt = "2026-01-01T00:00:00Z",
        )
        assertTrue(
            canPinChatMessage(resolveChatPinAllianceId(room, message), "R5"),
        )
    }
}
