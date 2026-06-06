package com.lastasylum.alliance.data.chat.outbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.store.ChatOutboxEntity
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
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
class ChatOutboxTest {
    private lateinit var context: Context
    private lateinit var db: SquadRelayDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = SquadRelayDatabase.createInMemory(context)
    }

    @Test
    fun outboxDao_persistsPendingRow() = runBlocking {
        val dao = db.chatOutboxDao()
        dao.upsert(
            ChatOutboxEntity(
                clientMessageId = "client-1",
                userId = "user1",
                roomId = "room1",
                pendingMessageId = "pending-1",
                text = "hello",
                replyToMessageId = null,
                attachmentsJson = null,
                excavationAlert = false,
                source = OutboxSendSource.ChatUi.wire,
                state = OutboxSendState.Pending.wire,
                attempts = 0,
                createdAtMs = 1L,
            ),
        )
        val active = dao.observeActive("user1").first()
        assertEquals(1, active.size)
        assertEquals("client-1", active.first().clientMessageId)
    }

    @Test
    fun outboxDao_getResumable_includesPendingAndFailed() = runBlocking {
        val dao = db.chatOutboxDao()
        dao.upsert(
            ChatOutboxEntity(
                clientMessageId = "client-resume",
                userId = "user1",
                roomId = "room1",
                pendingMessageId = "pending-resume",
                text = "resume me",
                replyToMessageId = null,
                attachmentsJson = null,
                excavationAlert = false,
                source = OutboxSendSource.ChatUi.wire,
                state = OutboxSendState.Pending.wire,
                attempts = 0,
                createdAtMs = 2L,
            ),
        )
        val reloadedDb = SquadRelayDatabase.createInMemory(context)
        reloadedDb.chatOutboxDao().upsert(
            db.chatOutboxDao().getByClientId("client-resume")!!,
        )
        val resumable = reloadedDb.chatOutboxDao().getResumable("user1")
        assertEquals(1, resumable.size)
        assertTrue(OutboxSendState.fromWire(resumable.first().state) == OutboxSendState.Pending)
    }
}
