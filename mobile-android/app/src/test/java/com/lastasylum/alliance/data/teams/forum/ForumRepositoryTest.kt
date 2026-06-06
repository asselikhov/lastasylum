package com.lastasylum.alliance.data.teams.forum

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamsApiUnusedStub
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.data.telemetry.LatencySpanType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumRepositoryTest {
    private lateinit var repo: ForumRepository
    private lateinit var tracker: DeliveryLatencyTracker

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = SquadRelayDatabase.createInMemory(context)
        tracker = DeliveryLatencyTracker(db, CoroutineScope(Dispatchers.Unconfined))
        val teamsApi = object : TeamsApiUnusedStub() {
            override suspend fun listForumTopics(teamId: String, view: String): List<TeamForumTopicDto> =
                listOf(sampleTopic("topic-1"))

            override suspend fun postForumMessage(
                teamId: String,
                topicId: String,
                body: com.lastasylum.alliance.data.teams.CreateTeamForumMessageBody,
            ): TeamForumMessageDto = sampleMessage("posted-1", body.clientMessageId)
        }
        repo = ForumRepository(
            db = db,
            teamsRepository = TeamsRepository(teamsApi),
            launchDiskCache = LaunchDiskCache(context),
            forumPrefs = TeamForumPreferences(context),
            latencyTracker = tracker,
        )
    }

    @Test
    fun syncTopics_upsertsIntoRoom() = runBlocking {
        val topics = repo.syncTopics("user1", "team1", bypassCache = true).getOrThrow()
        assertEquals(1, topics.size)
        val observed = repo.observeTopics("user1", "team1").first()
        assertEquals("topic-1", observed.first().id)
    }

    @Test
    fun onForumSocketMessage_endsForumSendSpan() = runBlocking {
        tracker.startSpan(LatencySpanType.ForumSendToSocket, "forum-client-1")
        repo.onForumSocketMessage(
            userId = "user1",
            teamId = "team1",
            topicId = "topic-1",
            message = sampleMessage("msg-1"),
            correlationId = "forum-client-1",
        )
        val snap = tracker.snapshot()
        assertTrue((snap.byType[LatencySpanType.ForumSendToSocket]?.count ?: 0) >= 1)
    }

    @Test
    fun postForumMessageWithRetries_startsAndCompletesForumSendSpan() = runBlocking {
        val clientId = "forum-client-post"
        repo.postForumMessageWithRetries(
            userId = "user1",
            teamId = "team1",
            topicId = "topic-1",
            text = "hello",
            clientMessageId = clientId,
        ).getOrThrow()
        repo.onForumSocketMessage(
            userId = "user1",
            teamId = "team1",
            topicId = "topic-1",
            message = sampleMessage("posted-1", clientId),
            correlationId = clientId,
        )
        val snap = tracker.snapshot()
        assertTrue((snap.byType[LatencySpanType.ForumSendToSocket]?.count ?: 0) >= 1)
    }

    private fun sampleTopic(id: String) = TeamForumTopicDto(
        id = id,
        teamId = "team1",
        title = "Topic",
        createdByUserId = "user1",
        messageCount = 0,
        unreadCount = 0,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
    )

    private fun sampleMessage(id: String, clientMessageId: String? = null) = TeamForumMessageDto(
        id = id,
        teamId = "team1",
        topicId = "topic-1",
        senderUserId = "user2",
        senderUsername = "peer",
        text = "hello",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z",
        clientMessageId = clientMessageId,
    )
}
