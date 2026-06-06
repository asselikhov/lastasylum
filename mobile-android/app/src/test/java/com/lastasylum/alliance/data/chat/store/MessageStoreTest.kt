package com.lastasylum.alliance.data.chat.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageStoreTest {
    private lateinit var context: Context
    private lateinit var store: MessageStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = MessageStore(SquadRelayDatabase.createInMemory(context))
    }

    @Test
    fun upsertAndObserve_messagesNewestFirst() = runBlocking {
        val roomId = "room1"
        val userId = "user1"
        val older = sampleMessage("507f1f77bcf86cd799439010", roomId, "older", "2026-01-01T00:00:00Z")
        val newer = sampleMessage("507f1f77bcf86cd799439099", roomId, "newer", "2026-01-02T00:00:00Z")
        store.upsertMessages(userId, roomId, listOf(older, newer))
        val observed = store.observeMessages(userId, roomId).first()
        assertEquals("507f1f77bcf86cd799439099", observed.first()._id)
    }

    private fun sampleMessage(id: String, roomId: String, text: String, createdAt: String) = ChatMessage(
        _id = id,
        allianceId = "a1",
        roomId = roomId,
        senderId = "u1",
        senderUsername = "user",
        senderRole = "R1",
        text = text,
        createdAt = createdAt,
    )
}
