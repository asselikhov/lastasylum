package com.lastasylum.alliance.data.chat.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.MarkRoomReadResponse
import com.lastasylum.alliance.data.chat.outbox.ChatOutbox
import com.lastasylum.alliance.data.chat.outbox.OutboxSendSource
import com.lastasylum.alliance.data.chat.store.MessageStore
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatSyncEngineTest {
    private lateinit var messageStore: MessageStore
    private lateinit var fakeGateway: FakeSyncGateway
    private lateinit var chatOutbox: ChatOutbox
    private lateinit var engine: ChatSyncEngine
    private val scope = CoroutineScope(SupervisorJob())
    private val userId = "user-sync-test"
    private val roomId = "room-sync-test"

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = SquadRelayDatabase.createInMemory(context)
        messageStore = MessageStore(db)
        fakeGateway = FakeSyncGateway()
        val tracker = DeliveryLatencyTracker(db, scope)
        chatOutbox = ChatOutbox(db, messageStore, tracker)
        engine = ChatSyncEngine(messageStore, chatOutbox, fakeGateway, tracker)
    }

    @Test
    fun bootstrapLoadRooms_persistsRoomsToStore() = runBlocking {
        fakeGateway.rooms = Result.success(listOf(sampleRoom("hub-1"), sampleRoom("raid-2")))

        val loaded = engine.bootstrapLoadRooms(userId).getOrThrow()

        assertEquals(2, loaded.size)
        assertEquals(setOf("hub-1", "raid-2"), messageStore.getRooms(userId).map { it.id }.toSet())
    }

    @Test
    fun markRoomRead_updatesStoreAndRepository() = runBlocking {
        engine.bindUser(userId)
        fakeGateway.markReadResult = Result.success(MarkRoomReadResponse(success = true, unreadCount = 0))

        val result = engine.markRoomRead(userId, roomId, "507f1f77bcf86cd799439099")

        assertTrue(result.isSuccess)
        assertEquals(1, fakeGateway.markReadCalls.size)
        assertEquals(roomId to "507f1f77bcf86cd799439099", fakeGateway.markReadCalls.first())
    }

    @Test
    fun resumePendingOutbox_onlySendsEachEntryOnce() = runBlocking {
        engine.bindUser(userId)
        fakeGateway.sendResult = Result.success(
            sampleMessage("507f1f77bcf86cd799439010", roomId, "hello"),
        )
        chatOutbox.enqueueSend(
            userId = userId,
            roomId = roomId,
            text = "hello",
            replyToMessageId = null,
            attachments = null,
            excavationAlert = false,
            source = OutboxSendSource.ChatUi,
            currentUserId = userId,
            currentUserRole = "R1",
            senderUsername = "me",
            pendingMessageId = "pending-1",
        )

        engine.resumePendingOutbox(scope, userId)
        delay(300)
        engine.resumePendingOutbox(scope, userId)
        delay(300)

        assertEquals(1, fakeGateway.sendCalls.get())
    }

    @Test
    fun sendEnqueuedOutbox_concurrentClaim_sendsOnce() = runBlocking {
        engine.bindUser(userId)
        fakeGateway.sendResult = Result.success(
            sampleMessage("507f1f77bcf86cd799439010", roomId, "hello"),
        )
        val prepared = chatOutbox.prepareEnqueue(
            userId = userId,
            roomId = roomId,
            text = "hello",
            replyToMessageId = null,
            attachments = null,
            excavationAlert = false,
            source = OutboxSendSource.ChatUi,
            currentUserId = userId,
            currentUserRole = "R1",
            senderUsername = "me",
            pendingMessageId = "pending-claim",
        )
        chatOutbox.persistEnqueue(prepared)

        val results = coroutineScope {
            val first = async { engine.sendEnqueuedOutbox(prepared.clientMessageId, skipSocket = true) }
            val second = async { engine.sendEnqueuedOutbox(prepared.clientMessageId, skipSocket = true) }
            listOf(first.await(), second.await())
        }

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })
        assertEquals(1, fakeGateway.sendCalls.get())
    }

    @Test
    fun sendEnqueuedOutbox_overlayRaid_forwardsGameEventAlert() = runBlocking {
        engine.bindUser(userId)
        fakeGateway.sendResult = Result.success(
            sampleMessage("507f1f77bcf86cd799439011", roomId, "[ШТАБ] Раскопки"),
        )
        val prepared = chatOutbox.prepareEnqueue(
            userId = userId,
            roomId = roomId,
            text = "[ШТАБ] Раскопки",
            replyToMessageId = null,
            attachments = null,
            excavationAlert = false,
            source = OutboxSendSource.OverlayRaid,
            currentUserId = userId,
            currentUserRole = "R1",
            senderUsername = "me",
            pendingMessageId = "overlay-pending-1",
            gameEventAlert = "hq_excavation",
        )
        chatOutbox.persistEnqueue(prepared)

        val result = engine.sendEnqueuedOutbox(prepared.clientMessageId)

        assertTrue(result.isSuccess)
        assertEquals("hq_excavation", fakeGateway.lastOverlayGameEventAlert)
    }

    @Test
    fun loadRoomSnapshotFromStore_returnsEmptySnapshotForKnownRoomWithoutMessages() = runBlocking {
        messageStore.upsertRooms(userId, listOf(sampleRoom(roomId)))

        val snapshot = engine.loadRoomSnapshotFromStore(userId, roomId)

        assertTrue(snapshot != null)
        assertTrue(snapshot!!.messages.isEmpty())
    }

    private fun sampleRoom(id: String) = ChatRoomDto(
        id = id,
        allianceId = "pt:team",
        title = id,
        sortOrder = 1,
        unreadCount = 0,
        lastReadMessageId = null,
    )

    private fun sampleMessage(id: String, roomId: String, text: String) = ChatMessage(
        _id = id,
        allianceId = "a1",
        roomId = roomId,
        senderId = userId,
        senderUsername = "me",
        senderRole = "R1",
        text = text,
        createdAt = "2026-01-01T00:00:00Z",
    )

    private class FakeSyncGateway : ChatSyncRestGateway {
        var rooms: Result<List<ChatRoomDto>> = Result.success(emptyList())
        var markReadResult: Result<MarkRoomReadResponse> =
            Result.success(MarkRoomReadResponse(success = true, unreadCount = 0))
        val markReadCalls = mutableListOf<Pair<String, String>>()
        val sendCalls = AtomicInteger(0)
        var sendResult: Result<ChatMessage> = Result.failure(IllegalStateException("no_send_stub"))
        var lastOverlayGameEventAlert: String? = null

        override suspend fun listRooms(): Result<List<ChatRoomDto>> = rooms

        override suspend fun markRoomRead(roomId: String, messageId: String): Result<MarkRoomReadResponse> {
            markReadCalls.add(roomId to messageId)
            return markReadResult
        }

        override suspend fun sendMessageWithRetriesForChatUi(
            text: String,
            roomId: String,
            replyToMessageId: String?,
            attachments: List<String>?,
            excavationAlert: Boolean,
            clientMessageId: String?,
            skipSocket: Boolean,
        ): Result<ChatMessage> {
            sendCalls.incrementAndGet()
            return sendResult
        }

        override suspend fun sendOverlayRaidCommandFast(
            text: String,
            roomId: String,
            gameEventAlert: String?,
            clientMessageId: String,
            maxAttempts: Int,
        ): Result<ChatMessage> {
            lastOverlayGameEventAlert = gameEventAlert
            sendCalls.incrementAndGet()
            return sendResult
        }
    }
}
