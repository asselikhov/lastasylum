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
    fun mapUrlsForCoordinates_pathFirstWithoutAmpersand() {
        val urls = GameSearchDeepLinks.mapUrlsForCoordinates(100, 200, serverNumber = 3)
        assertEquals("globalphslink://world/3", urls.first())
        assertTrue(urls.contains("globalphslink://map"))
        assertTrue(urls.contains("globalphslink://coordinate/100/200/3"))
        assertFalse(urls.any { it.contains("&") })
    }

    @Test
    fun mapFlyBurstUrls_staggerFlyWorldAttempts() {
        val urls = GameSearchDeepLinks.mapFlyBurstUrls(505, 495, serverNumber = 109)
        assertEquals(
            listOf(
                "globalphslink://world/109",
                "globalphslink://map?x=505",
                "globalphslink://map?y=495",
                "globalphslink://map/505/495/109",
                "globalphslink://coordinate/505/495/109",
                "globalphslink://map",
            ),
            urls,
        )
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
