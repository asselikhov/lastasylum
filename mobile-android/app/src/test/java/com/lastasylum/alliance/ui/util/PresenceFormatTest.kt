package com.lastasylum.alliance.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PresenceFormatTest {

    @Test
    fun ingameWithFreshPing_isOverlayIngameNow() {
        val now = Instant.now().toString()
        assertTrue(isOverlayIngameNow("ingame", now))
    }

    @Test
    fun onlineStatus_isNotOverlayIngameNow() {
        assertFalse(isOverlayIngameNow("online", Instant.now().toString()))
    }

    @Test
    fun staleIngamePing_isNotOverlayIngameNow() {
        val stale = Instant.now().minusMillis(OVERLAY_INGAME_PRESENCE_STALE_MS + 1).toString()
        assertFalse(isOverlayIngameNow("ingame", stale))
    }

    @Test
    fun freshPing_formatsJustNow() {
        assertEquals("только что", formatOverlayPresenceAgeRu(Instant.now().toString()))
    }

    @Test
    fun ingamePingWithinLiveWindow_isOverlayIngameNow() {
        val ping = Instant.now().minusSeconds(30).toString()
        assertTrue(isOverlayIngameNow("ingame", ping))
    }

    @Test
    fun ingamePingOlderThanLiveWindow_isNotOverlayIngameNow() {
        val ping = Instant.now().minusSeconds(100).toString()
        assertFalse(isOverlayIngameNow("ingame", ping))
    }
}
