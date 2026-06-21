package com.lastasylum.alliance.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSearchDeepLinksTest {
    @Test
    fun searchUrls_openPanelFirstWithoutAmpersand() {
        val urls = GameSearchDeepLinks.searchUrls(
            GameSearchBridge.SearchKind.PLAYER,
            "Nick Name",
            serverNumber = 42,
        )
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.first().startsWith("globalphslink://search/player/"))
        assertTrue(urls.any { it == "globalphslink://search" })
        assertTrue(urls.any { it.endsWith("/42") })
        assertFalse(urls.any { it.contains("&") })
    }

    @Test
    fun mapUrlsForCoordinates_usePathStyleWithoutAmpersand() {
        val urls = GameSearchDeepLinks.mapUrlsForCoordinates(100, 200, serverNumber = 3)
        assertEquals("globalphslink://map/100/200/3", urls.first())
        assertTrue(urls.contains("globalphslink://world/100/200/3"))
        assertTrue(urls.contains("globalphslink://coordinate/100/200/3"))
        assertTrue(urls.contains("globalphslink://map"))
        assertFalse(urls.any { it.contains("&") })
    }

    @Test
    fun profileUrls_usePathStyleWithoutAmpersand() {
        val urls = GameSearchDeepLinks.profileUrls(
            GameSearchBridge.SearchKind.PLAYER,
            "Hero",
            serverNumber = 7,
        )
        assertTrue(urls.first().startsWith("globalphslink://profile/player/"))
        assertFalse(urls.first().contains("&"))
    }
}
