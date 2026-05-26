package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessagesListDeriverTest {

    private fun msg(
        id: String,
        senderId: String = "u1",
        text: String = "hello",
        createdAt: String = "2026-05-24T12:00:00.000Z",
    ) = ChatMessage(
        _id = id,
        allianceId = "a",
        roomId = "r",
        senderId = senderId,
        senderUsername = "user",
        senderRole = "R1",
        text = text,
        createdAt = createdAt,
    )

    @Test
    fun buildChatMessagesListDerivedAfterPrepend_matchesFullRebuild() {
        val previous = listOf(
            msg("2", senderId = "u1", text = "b"),
            msg("1", senderId = "u2", text = "a"),
        )
        val previousDerived = buildChatMessagesListDerived(previous)
        val messages = listOf(msg("3", senderId = "u1", text = "c")) + previous
        val incremental = buildChatMessagesListDerivedAfterPrepend(
            previousDerived = previousDerived,
            previousMessages = previous,
            messages = messages,
        )
        val full = buildChatMessagesListDerived(messages)
        assertEquals(full.timeline.size, incremental.timeline.size)
        assertEquals(full.clusterFlags, incremental.clusterFlags)
        assertEquals(full.clusterTopSpacingDp, incremental.clusterTopSpacingDp)
    }

    @Test
    fun buildChatMessagesListDerivedAfterPrepend_sameSenderCluster() {
        val previous = listOf(
            msg("2", senderId = "u1"),
            msg("1", senderId = "u1"),
        )
        val previousDerived = buildChatMessagesListDerived(previous)
        val messages = listOf(msg("3", senderId = "u1")) + previous
        val incremental = buildChatMessagesListDerivedAfterPrepend(
            previousDerived = previousDerived,
            previousMessages = previous,
            messages = messages,
        )
        val full = buildChatMessagesListDerived(messages)
        assertEquals(full, incremental)
    }

    @Test
    fun buildChatMessagesListDerivedAfterPrepend_fallsBackWhenNotSimplePrepend() {
        val previous = listOf(msg("1"), msg("2"))
        val previousDerived = buildChatMessagesListDerived(previous)
        val messages = listOf(msg("3"), msg("1"), msg("2"))
        val incremental = buildChatMessagesListDerivedAfterPrepend(
            previousDerived = previousDerived,
            previousMessages = previous,
            messages = messages,
        )
        val full = buildChatMessagesListDerived(messages)
        assertEquals(full, incremental)
        assertTrue(incremental.timeline.isNotEmpty())
    }
}
