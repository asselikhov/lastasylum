package com.lastasylum.alliance.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatLazyListScrollTest {
    @Test
    fun reverseChatExpandScrollDelta_atBottom_noCompensation() {
        assertEquals(0f, reverseChatExpandScrollDelta(heightDeltaPx = 120, atChatBottom = true))
    }

    @Test
    fun reverseChatExpandScrollDelta_readingHistory_negativeDelta() {
        assertEquals(-80f, reverseChatExpandScrollDelta(heightDeltaPx = 80, atChatBottom = false))
    }

    @Test
    fun reverseChatExpandScrollDelta_zeroOrNegativeGrowth_noCompensation() {
        assertEquals(0f, reverseChatExpandScrollDelta(heightDeltaPx = 0, atChatBottom = false))
        assertEquals(0f, reverseChatExpandScrollDelta(heightDeltaPx = -10, atChatBottom = false))
    }
}
