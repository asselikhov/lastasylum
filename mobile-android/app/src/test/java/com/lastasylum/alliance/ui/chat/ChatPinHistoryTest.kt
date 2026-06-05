package com.lastasylum.alliance.ui.chat

import com.lastasylum.alliance.data.chat.PinnedMessagePreviewDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPinHistoryTest {
    private fun preview(id: String, text: String = "text") = PinnedMessagePreviewDto(
        id = id,
        text = text,
        senderUsername = "alice",
        createdAt = "2026-01-01T00:00:00Z",
    )

    @Test
    fun pushPinHistory_dedupesAndPrepends() {
        val a = preview("a", "A")
        val b = preview("b", "B")
        val history = pushPinHistory(listOf(a, b), preview("a", "A2"))
        assertEquals(listOf("a", "b"), history.map { it.id })
        assertEquals("A2", history.first().text)
    }

    @Test
    fun pushPinHistory_capsAtMax() {
        val ids = (1..20).map { preview(it.toString()) }
        val next = pushPinHistory(ids, preview("new"))
        assertEquals(PIN_HISTORY_MAX, next.size)
        assertEquals("new", next.first().id)
    }

    @Test
    fun advancePinBarIndex_cyclesThroughHistory() {
        val history = listOf(preview("1"), preview("2"), preview("3"))
        assertEquals(1, advancePinBarIndex(history, 0))
        assertEquals(2, advancePinBarIndex(history, 1))
        assertEquals(0, advancePinBarIndex(history, 2))
    }

    @Test
    fun advancePinBarIndex_singlePinStaysAtZero() {
        val history = listOf(preview("1"))
        assertEquals(0, advancePinBarIndex(history, 0))
    }

    @Test
    fun pinBarPreviewAtIndex_usesHistoryThenServer() {
        val history = listOf(preview("1"), preview("2"))
        val server = preview("server")
        assertEquals("2", pinBarPreviewAtIndex(history, 1, server)?.id)
        assertEquals("server", pinBarPreviewAtIndex(emptyList(), 0, server)?.id)
    }

    @Test
    fun pinBarPreviewAtIndex_prefersActivePinAtIndexZero() {
        val history = listOf(preview("old"), preview("older"))
        val server = preview("current")
        assertEquals(
            "current",
            pinBarPreviewAtIndex(history, 0, server, activePinId = "current")?.id,
        )
    }

    @Test
    fun pinHistoryDisplayCount_showsOnlyWhenMultiple() {
        assertEquals(0, pinHistoryDisplayCount(listOf(preview("1"))))
        assertEquals(3, pinHistoryDisplayCount(listOf(preview("1"), preview("2"), preview("3"))))
    }

    @Test
    fun syncRoomPinHistory_resetsIndexOnNewPin() {
        val old = preview("old")
        val history = listOf(old)
        val (updated, reset) = syncRoomPinHistory(history, preview("new"), "new")
        assertTrue(reset)
        assertEquals("new", updated.first().id)
        assertEquals(2, updated.size)
    }

    @Test
    fun syncRoomPinHistory_keepsIndexWhenSamePin() {
        val pin = preview("same", "v1")
        val history = listOf(pin)
        val (updated, reset) = syncRoomPinHistory(history, preview("same", "v2"), "same")
        assertFalse(reset)
        assertEquals("v2", updated.first().text)
    }
}
