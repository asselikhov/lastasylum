package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListDeriveDeferTest {

    private fun sampleMessages(): List<ChatMessage> = listOf(
        ChatMessage(
            _id = "a",
            allianceId = "al1",
            senderId = "u1",
            senderUsername = "user",
            senderRole = "R1",
            text = "hi",
            createdAt = "2026-01-01T00:00:00Z",
        ),
    )

    @Test
    fun deferFullDerive_whileScrolling_storesPending() {
        val gate = ChatListDeriveDefer()
        gate.setScrollInProgress(true)
        val messages = sampleMessages()
        assertTrue(gate.deferFullDerive(messages))
        assertEquals(messages, gate.peekPending())
    }

    @Test
    fun deferFullDerive_whenNotScrolling_runsImmediately() {
        val gate = ChatListDeriveDefer()
        assertFalse(gate.deferFullDerive(sampleMessages()))
        assertNull(gate.peekPending())
    }

    @Test
    fun setScrollInProgress_false_flushesPending() {
        val gate = ChatListDeriveDefer()
        val messages = sampleMessages()
        gate.setScrollInProgress(true)
        gate.deferFullDerive(messages)
        val flush = gate.setScrollInProgress(false)
        assertNotNull(flush)
        assertEquals(messages, flush)
        assertNull(gate.peekPending())
    }
}
