package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForumListMutationsTest {

    private fun msg(id: String, text: String = "t") = TeamForumMessageDto(
        id = id,
        teamId = "team",
        topicId = "topic",
        senderUserId = "u1",
        senderUsername = "user",
        text = text,
        createdAt = "2020-01-01T00:00:00.000Z",
        updatedAt = "2020-01-01T00:00:00.000Z",
    )

    @Test
    fun mergeForumMessagesPage_preservesSocketOnlyIds() {
        val socketOnly = msg("507f1f77bcf86cd799439013", "socket")
        val shared = msg("507f1f77bcf86cd799439011", "shared")
        val existing = listOf(socketOnly, shared)
        val loaded = listOf(shared.copy(text = "http"))
        val merged = mergeForumMessagesPage(existing, loaded)
        assertEquals(
            listOf("507f1f77bcf86cd799439011", "507f1f77bcf86cd799439013"),
            merged.map { it.id },
        )
        assertEquals("http", merged.first { it.id == shared.id }.text)
    }

    @Test
    fun capForumMessagesTrimNewestOnly_keepsOldestWhenOverCap() {
        val messages = (1..5).map { i ->
            msg("507f1f77bcf86cd79943901$i")
        }.toMutableList()
        capForumMessagesTrimNewestOnly(messages, max = 3)
        assertEquals(3, messages.size)
        assertEquals("507f1f77bcf86cd799439011", messages.first().id)
        assertTrue(messages.last().id.endsWith("3"))
    }

    @Test
    fun replaceMatchingPendingForumOutgoing_replacesByClientMessageId() {
        val clientId = "uuid-123"
        val messages = mutableListOf(
            msg("pending-$clientId", "hello").copy(clientMessageId = clientId),
        )
        val confirmed = msg("507f1f77bcf86cd799439099", "hello").copy(
            clientMessageId = clientId,
            senderUserId = "u1",
        )
        val replaced = replaceMatchingPendingForumOutgoing(messages, confirmed, "u1")
        assertTrue(replaced)
        assertEquals(1, messages.size)
        assertEquals("507f1f77bcf86cd799439099", messages.single().id)
        assertEquals(clientId, messages.single().clientMessageId)
    }
}
