package com.lastasylum.alliance.data.teams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForumMessageStashTest {
    @Before
    fun clear() {
        ForumMessageStash.drainAllForTeam("team1")
        ForumMessageStash.drainAllForTeam("team2")
    }

    @Test
    fun drainAllForTeam_returnsAllTopicsAndClearsStash() {
        val msg1 = sampleMessage("team1", "topic-a", "m1")
        val msg2 = sampleMessage("team1", "topic-b", "m2")
        assertTrue(ForumMessageStash.stash(msg1))
        assertTrue(ForumMessageStash.stash(msg2))

        val drained = ForumMessageStash.drainAllForTeam("team1")

        assertEquals(2, drained.size)
        assertEquals(listOf(msg1), drained["topic-a"])
        assertEquals(listOf(msg2), drained["topic-b"])
        assertTrue(ForumMessageStash.drainAllForTeam("team1").isEmpty())
    }

    private fun sampleMessage(teamId: String, topicId: String, id: String) =
        TeamForumMessageDto(
            id = id,
            teamId = teamId,
            topicId = topicId,
            senderUserId = "user1",
            senderUsername = "user",
            text = "hello",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )
}
