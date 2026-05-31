package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayReactionLogTimeFormatTest {
    @Test
    fun timeLabel_dedupesWhenRelativeEqualsAbsolute() {
        val iso = "2026-05-29T14:30:00Z"
        val (absolute, relative) = formatOverlayReactionLogTimeLine(iso)
        val label = formatOverlayReactionLogTimeLabel(iso)
        if (relative == absolute || relative.isBlank()) {
            assertEquals(absolute.ifBlank { relative }, label)
        } else {
            assertEquals("$absolute · $relative", label)
        }
    }

    @Test
    fun timeLabel_includesRelativeWhenDifferent() {
        val label = formatOverlayReactionLogTimeLabel("2026-05-28T14:30:00Z")
        assertEquals(true, label.isNotBlank())
    }
}
