package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pack grants gate sending/picker only; recipients decode incoming obzhory sticker reactions
 * from shared APK assets (see plan: Obzhory reaction visibility).
 */
class OverlayObzhoryStickerReactionTest {
    @Test
    fun decodeChatStickerReactionId_parsesObzhory() {
        val parsed = decodeChatStickerReactionId("sticker/obzhory/2")
        assertNotNull(parsed)
        assertEquals("obzhory", parsed!!.first)
        assertEquals("2", parsed.second)
    }

    @Test
    fun encodeChatStickerReactionId_roundTripsObzhory() {
        val id = encodeChatStickerReactionId("obzhory", "3")
        assertEquals("sticker/obzhory/3", id)
        assertEquals("obzhory" to "3", decodeChatStickerReactionId(id))
    }

    @Test
    fun overlayStickerPackTabs_hidesObzhoryWithoutGrant() {
        val tabs = overlayStickerPackTabs(emptySet())
        assertFalse(tabs.any { it.packKey == "obzhory" })
    }

    @Test
    fun overlayStickerPackTabs_showsObzhoryWhenGranted() {
        val tabs = overlayStickerPackTabs(setOf("obzhory"))
        assertTrue(tabs.any { it.packKey == "obzhory" })
    }
}
