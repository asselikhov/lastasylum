package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated baseline for timeline derive cost (JVM). Manual fling profiling remains on device.
 */
class MessagesListDeriverPerfTest {

    @Test
    fun buildChatMessagesListDerived_800messages_completesUnderBudget() {
        val messages = List(800) { i ->
            ChatMessage(
                _id = "507f1f77bcf86cd79943${String.format("%04d", i)}",
                allianceId = "a",
                roomId = "r",
                senderId = if (i % 3 == 0) "u1" else "u2",
                senderUsername = "user",
                senderRole = "R1",
                text = "message $i",
                createdAt = "2024-06-15T12:${String.format("%02d", i % 60)}:00Z",
            )
        }
        val start = System.nanoTime()
        val derived = buildChatMessagesListDerived(messages)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(derived.timeline.isNotEmpty())
        assertTrue("chat derive 800 msgs took ${elapsedMs}ms", elapsedMs < 500)
    }

    @Test
    fun buildForumMessagesListDerived_800messages_completesUnderBudget() {
        val messages = List(800) { i ->
            TeamForumMessageDto(
                id = "507f1f77bcf86cd79943${String.format("%04d", i)}",
                topicId = "t1",
                teamId = "team",
                senderUserId = if (i % 3 == 0) "u1" else "u2",
                senderUsername = "user",
                text = "message $i",
                createdAt = "2024-06-15T12:${String.format("%02d", i % 60)}:00Z",
                updatedAt = "2024-06-15T12:${String.format("%02d", i % 60)}:00Z",
                deletedAt = null,
                replyToMessageId = null,
                imageRelativeUrl = null,
                imageRelativeUrls = emptyList(),
                fileRelativeUrl = null,
            )
        }
        val start = System.nanoTime()
        val derived = buildForumMessagesListDerived(messages)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(derived.timeline.isNotEmpty())
        assertTrue("forum derive 800 msgs took ${elapsedMs}ms", elapsedMs < 500)
    }
}
