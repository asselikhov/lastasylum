package com.lastasylum.alliance.data.teams.forum

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}
