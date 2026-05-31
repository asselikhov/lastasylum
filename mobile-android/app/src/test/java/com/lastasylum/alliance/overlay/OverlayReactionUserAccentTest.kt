package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayReactionUserAccentTest {

    @Test
    fun ringColorFor_isStablePerUser() {
        val a = OverlayReactionUserAccent.ringColorFor("user-123")
        val b = OverlayReactionUserAccent.ringColorFor("user-123")
        assertEquals(a, b)
    }

    @Test
    fun ringColorFor_differsForDifferentUsers() {
        val a = OverlayReactionUserAccent.ringColorFor("user-a")
        val b = OverlayReactionUserAccent.ringColorFor("user-b")
        assert(a != b)
    }
}
