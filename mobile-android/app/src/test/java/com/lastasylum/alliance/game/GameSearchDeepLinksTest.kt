package com.lastasylum.alliance.game

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSearchDeepLinksTest {
    @Test
    fun searchUrls_usePathStyleWithoutAmpersand() {
        val urls = GameSearchDeepLinks.searchUrls(
            GameSearchBridge.SearchKind.PLAYER,
            "Nick Name",
            serverNumber = 42,
        )
        assertTrue(urls.isNotEmpty())
        assertTrue(urls.first().startsWith("globalphslink://search/player/"))
        assertTrue(urls.first().endsWith("/42"))
        assertTrue(urls.any { it.contains("Nick%20Name") || it.contains("Nick+Name") })
        assertFalse(urls.first().contains("&"))
    }

    @Test
    fun mapUrlsForCoordinates_usePathStyleWithoutAmpersand() {
        val urls = GameSearchDeepLinks.mapUrlsForCoordinates(100, 200, serverNumber = 3)
        assertTrue(urls.contains("globalphslink://map/100/200/3"))
        assertTrue(urls.contains("globalphslink://world/100/200/3"))
        assertTrue(urls.contains("globalphslink://map?xy=100,200"))
        assertFalse(urls.first().contains("&"))
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
