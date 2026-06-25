package com.lastasylum.alliance.data.chat.outbox

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.store.ChatOutboxEntity
import com.lastasylum.alliance.data.chat.store.MessageStore
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
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
        val resumable = reloadedDb.chatOutboxDao().getResumable("user1", Int.MAX_VALUE)
        assertEquals(1, resumable.size)
        assertTrue(OutboxSendState.fromWire(resumable.first().state) == OutboxSendState.Pending)
    }

    @Test
    fun resumePending_idempotentConfirm() = runBlocking {
        val dao = db.chatOutboxDao()
        val clientId = "client-idem"
        dao.upsert(
            ChatOutboxEntity(
                clientMessageId = clientId,
                userId = "user1",
                roomId = "room1",
                pendingMessageId = "pending-idem",
                text = "hello",
                replyToMessageId = null,
                attachmentsJson = null,
                excavationAlert = false,
                source = OutboxSendSource.ChatUi.wire,
                state = OutboxSendState.Pending.wire,
                attempts = 0,
                createdAtMs = 3L,
            ),
        )
        val outbox = ChatOutbox(
            db = db,
            messageStore = MessageStore(db),
            latencyTracker = DeliveryLatencyTracker(db, CoroutineScope(Dispatchers.Unconfined)),
        )
        val sent = ChatMessage(
            _id = "507f1f77bcf86cd799439099",
            allianceId = "a1",
            roomId = "room1",
            senderId = "user1",
            senderUsername = "u",
            senderRole = "R1",
            text = "hello",
            createdAt = "2026-01-01T00:00:00Z",
        )
        outbox.confirmSend("user1", clientId, sent)
        outbox.confirmSend("user1", clientId, sent)
        val active = dao.observeActive("user1").first()
        assertEquals(0, active.size)
    }

    @Test
    fun observeActiveForRoom_emitsPendingAndClearsAfterConfirm() = runBlocking {
        val outbox = ChatOutbox(
            db = db,
            messageStore = MessageStore(db),
            latencyTracker = DeliveryLatencyTracker(db, CoroutineScope(Dispatchers.Unconfined)),
        )
        val enqueue = outbox.enqueueSend(
            userId = "user1",
            roomId = "room1",
            text = "hello",
            replyToMessageId = null,
            attachments = null,
            excavationAlert = false,
            source = OutboxSendSource.ChatUi,
            currentUserId = "user1",
            currentUserRole = "R1",
            senderUsername = "u",
        )
        val pending = outbox.observeActiveForRoom("user1", "room1").first()
        assertEquals(1, pending.size)
        assertEquals(enqueue.clientMessageId, pending.first().clientMessageId)
        val sent = ChatMessage(
            _id = "507f1f77bcf86cd799439099",
            allianceId = "a1",
            roomId = "room1",
            senderId = "user1",
            senderUsername = "u",
            senderRole = "R1",
            text = "hello",
            createdAt = "2026-01-01T00:00:00Z",
        )
        outbox.confirmSend("user1", enqueue.clientMessageId, sent)
        val cleared = outbox.observeActiveForRoom("user1", "room1").first()
        assertEquals(0, cleared.size)
    }

    @Test
    fun resumePendingSync_invokesSendBlockForEachResumableRow() = runBlocking {
        val dao = db.chatOutboxDao()
        repeat(2) { index ->
            dao.upsert(
                ChatOutboxEntity(
                    clientMessageId = "client-resume-$index",
                    userId = "user1",
                    roomId = "room1",
                    pendingMessageId = "pending-resume-$index",
                    text = "resume $index",
                    replyToMessageId = null,
                    attachmentsJson = null,
                    excavationAlert = false,
                    source = OutboxSendSource.ChatUi.wire,
                    state = OutboxSendState.Pending.wire,
                    attempts = 0,
                    createdAtMs = 10L + index,
                ),
            )
        }
        val outbox = ChatOutbox(
            db = db,
            messageStore = MessageStore(db),
            latencyTracker = DeliveryLatencyTracker(db, CoroutineScope(Dispatchers.Unconfined)),
        )
        var sendCount = 0
        outbox.resumePendingSync("user1") {
            sendCount++
            Result.failure(IllegalStateException("no network"))
        }
        assertEquals(2, sendCount)
    }

    @Test
    fun pendingRow_survivesProcessRestartSimulation() = runBlocking {
        val dao = db.chatOutboxDao()
        dao.upsert(
            ChatOutboxEntity(
                clientMessageId = "client-kill",
                userId = "user1",
                roomId = "room1",
                pendingMessageId = "pending-kill",
                text = "after kill",
                replyToMessageId = null,
                attachmentsJson = null,
                excavationAlert = false,
                source = OutboxSendSource.ChatUi.wire,
                state = OutboxSendState.Pending.wire,
                attempts = 0,
                createdAtMs = 99L,
            ),
        )
        val reloadedDb = SquadRelayDatabase.createInMemory(context)
        reloadedDb.chatOutboxDao().upsert(dao.getByClientId("client-kill")!!)
        val resumable = reloadedDb.chatOutboxDao().getResumable("user1", Int.MAX_VALUE)
        assertEquals(1, resumable.size)
        assertEquals("client-kill", resumable.first().clientMessageId)
    }
}
