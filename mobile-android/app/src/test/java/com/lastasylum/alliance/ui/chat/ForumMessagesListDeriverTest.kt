package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ForumMessagesListDeriverTest {

    @Test
    fun `lazy index maps newest message to reverse layout bottom`() {
        val m1 = msg("a", "u1", "2024-01-01T10:00:00Z")
        val m2 = msg("b", "u1", "2024-01-02T10:00:00Z")
        val derived = buildForumMessagesListDerived(listOf(m1, m2))
        assertEquals(4, derived.timeline.size)
        assertEquals(0, derived.fullLazyIndexForMessageId("b"))
        assertEquals(2, derived.fullLazyIndexForMessageId("a"))
    }

    @Test
    fun forumLazyIndexForMessageId_fallsBackWhenDerivedEmpty() {
        val m1 = msg("a", "u1", "2024-01-01T10:00:00Z")
        val m2 = msg("b", "u1", "2024-01-02T10:00:00Z")
        val idx = forumLazyIndexForMessageId(
            messages = listOf(m1, m2),
            derived = ForumMessagesListDerived.Empty,
            messageId = "a",
        )
        assertEquals(2, idx)
    }

    @Test
    fun `bottom index is zero for reverse chat parity`() {
        val messages = List(3) { i -> msg("id$i", "u", "2024-01-01T${10 + i}:00:00Z") }
        val derived = buildForumMessagesListDerived(messages)
        assertEquals(0, derived.bottomLazyIndex())
    }

    private fun msg(id: String, sender: String, createdAt: String) = TeamForumMessageDto(
        id = id,
        topicId = "t1",
        teamId = "team",
        senderUserId = sender,
        senderUsername = "user",
        text = "hi",
        createdAt = createdAt,
        updatedAt = createdAt,
        deletedAt = null,
        replyToMessageId = null,
        imageRelativeUrl = null,
        imageRelativeUrls = emptyList(),
        fileRelativeUrl = null,
        fileFilename = null,
    )
}
