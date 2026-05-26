package com.lastasylum.alliance.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTimeTest {
    @Test
    fun formatChatTimeMsk_convertsUtcToMoscow() {
        assertEquals("21:00", formatChatTimeMsk("2024-06-01T18:00:00.000Z"))
    }

    @Test
    fun formatChatTimeMsk_crossesMidnightInMoscow() {
        assertEquals("00:30", formatChatTimeMsk("2024-06-01T21:30:00Z"))
    }

    @Test
    fun chatDayKeyMsk_usesMoscowCalendarDay() {
        assertEquals("2024-06-02", chatDayKeyMsk("2024-06-01T21:30:00Z"))
        assertEquals("2024-06-01", chatDayKeyMsk("2024-06-01T18:00:00Z"))
    }

    @Test
    fun formatTeamFeedDateRu_includesMoscowOffset() {
        val label = formatTeamFeedDateRu("2024-06-01T18:00:00.000Z")
        assertEquals("1 июн. 2024, 21:00", label)
    }

    @Test
    fun parseIsoInstant_localWithoutZone_asUtcThenMsk() {
        assertEquals("15:00", formatChatTimeMsk("2024-06-01T12:00:00"))
        assertEquals("21:00", formatChatTimeMsk("2024-06-01T18:00:00.000"))
    }
}
