package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageClusteringTest {
    private fun msg(senderId: String, createdAt: String = "2026-05-22T12:00:00Z") =
        ChatMessage(
            _id = "m$senderId$createdAt",
            allianceId = "a1",
            roomId = "r1",
            senderId = senderId,
            senderUsername = senderId,
            senderRole = "MEMBER",
            text = "hi",
            createdAt = createdAt,
        )

    @Test
    fun newestFirst_chainBottom_onlyOnNewestInRun() {
        val list = listOf(
            msg("a", "2026-05-22T12:02:00Z"),
            msg("a", "2026-05-22T12:01:00Z"),
            msg("b", "2026-05-22T12:00:00Z"),
        )
        assertTrue(chatMessageIsClusterChainBottomNewestFirst(list, 0))
        assertFalse(chatMessageIsClusterChainBottomNewestFirst(list, 1))
        assertTrue(chatMessageIsClusterChainBottomNewestFirst(list, 2))
    }

    @Test
    fun oldestFirst_chainBottom_onlyOnNewestInRun() {
        val list = listOf(
            msg("b", "2026-05-22T12:00:00Z"),
            msg("a", "2026-05-22T12:01:00Z"),
            msg("a", "2026-05-22T12:02:00Z"),
        )
        assertTrue(chatMessageIsClusterChainBottomOldestFirst(list, 0))
        assertFalse(chatMessageIsClusterChainBottomOldestFirst(list, 1))
        assertTrue(chatMessageIsClusterChainBottomOldestFirst(list, 2))
    }
}
