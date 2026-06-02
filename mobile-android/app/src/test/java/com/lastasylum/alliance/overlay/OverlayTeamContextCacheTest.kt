package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OverlayTeamContextCacheTest {

    @Test
    fun peekValid_returnsNullWhenEmpty() {
        OverlayTeamContextCache.invalidate()
        assertNull(OverlayTeamContextCache.peekValid())
    }

    @Test
    fun peekValid_returnsNullAfterInvalidate() {
        OverlayTeamContextCache.invalidate()
        assertNull(OverlayTeamContextCache.peekValid(nowMs = 0L))
    }
}
