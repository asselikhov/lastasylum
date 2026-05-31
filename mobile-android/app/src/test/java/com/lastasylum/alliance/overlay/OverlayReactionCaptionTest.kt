package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayReactionCaptionTest {

    @Test
    fun formatReactionDisplayNick_stripsAtPrefix() {
        assertEquals("Player", OverlayReactionNickFormat.format("@Player"))
        assertEquals("Player", OverlayReactionNickFormat.format("Player"))
    }

    @Test
    fun formatReactionDisplayNick_blankFallback() {
        assertEquals("—", OverlayReactionNickFormat.format("  "))
    }
}
