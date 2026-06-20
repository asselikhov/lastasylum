package com.lastasylum.alliance.data.teams.forum

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumOutboxTest {
    private lateinit var db: SquadRelayDatabase
    private lateinit var outbox: ForumOutbox

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = SquadRelayDatabase.createInMemory(context)
        outbox = ForumOutbox(db)
    }

    @Test
    fun persistAndMarkSent_removesRow() = runBlocking {
        val entry = ForumOutboxEntry(
            clientMessageId = "client-1",
            userId = "user-1",
            teamId = "team-1",
            topicId = "topic-1",
            pendingMessageId = "pending-1",
            text = "hello",
            replyToMessageId = null,
            imageFileIds = null,
            fileFileId = null,
            state = "pending",
            attempts = 0,
            createdAtMs = 1L,
            lastError = null,
        )
        outbox.persist(entry)
        assertNotNull(db.forumOutboxDao().getByClientId("client-1"))
        outbox.markSent("client-1")
        assertNull(db.forumOutboxDao().getByClientId("client-1"))
    }

    @Test
    fun markFailed_incrementsAttempts() = runBlocking {
        val entry = ForumOutboxEntry(
            clientMessageId = "client-2",
            userId = "user-1",
            teamId = "team-1",
            topicId = "topic-1",
            pendingMessageId = "pending-2",
            text = "retry me",
            replyToMessageId = null,
            imageFileIds = listOf("img-1"),
            fileFileId = null,
            state = "pending",
            attempts = 0,
            createdAtMs = 1L,
            lastError = null,
        )
        outbox.persist(entry)
        outbox.markFailed("client-2", "network")
        val row = db.forumOutboxDao().getByClientId("client-2")
        assertNotNull(row)
        assertEquals("failed", row!!.state)
        assertEquals(1, row.attempts)
    }

    @Test
    fun resumePendingSync_successMarksSent() = runBlocking {
        val entry = ForumOutboxEntry(
            clientMessageId = "client-3",
            userId = "user-1",
            teamId = "team-1",
            topicId = "topic-1",
            pendingMessageId = "pending-3",
            text = "from worker",
            replyToMessageId = null,
            imageFileIds = null,
            fileFileId = null,
            state = "pending",
            attempts = 0,
            createdAtMs = 1L,
            lastError = null,
        )
        outbox.persist(entry)
        outbox.resumePendingSync("user-1") {
            Result.success(
                com.lastasylum.alliance.data.teams.TeamForumMessageDto(
                    id = "msg-3",
                    topicId = entry.topicId,
                    teamId = entry.teamId,
                    senderUserId = "user-1",
                    senderUsername = "me",
                    text = entry.text,
                    createdAt = "2026-01-01T00:00:00Z",
                    updatedAt = "2026-01-01T00:00:00Z",
                ),
            )
        }
        assertNull(db.forumOutboxDao().getByClientId("client-3"))
    }

    @Test
    fun recoverStuckSending_allowsRetry() = runBlocking {
        db.forumOutboxDao().upsert(
            com.lastasylum.alliance.data.chat.store.ForumOutboxEntity(
                clientMessageId = "client-stuck",
                userId = "user-1",
                teamId = "team-1",
                topicId = "topic-1",
                pendingMessageId = "pending-stuck",
                text = "stuck",
                replyToMessageId = null,
                imageFileIdsJson = null,
                fileFileId = null,
                state = "sending",
                attempts = 1,
                createdAtMs = 1L,
                lastError = null,
            ),
        )
        var sent = false
        outbox.resumePendingSync("user-1") {
            sent = true
            Result.success(
                com.lastasylum.alliance.data.teams.TeamForumMessageDto(
                    id = "msg-stuck",
                    topicId = "topic-1",
                    teamId = "team-1",
                    senderUserId = "user-1",
                    senderUsername = "me",
                    text = "stuck",
                    createdAt = "2026-01-01T00:00:00Z",
                    updatedAt = "2026-01-01T00:00:00Z",
                ),
            )
        }
        assertTrue(sent)
        assertNull(db.forumOutboxDao().getByClientId("client-stuck"))
    }
}
