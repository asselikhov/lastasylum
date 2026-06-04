package com.lastasylum.alliance.data.chat.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObzhoryStickerPackTest {
    @Test
    fun encodeAndParseStem() {
        val stem = "1"
        val wire = ObzhoryStickerPack.encode(stem)
        assertEquals("[[obzhory:1]]", wire)
        assertEquals(stem, ObzhoryStickerPack.parseStem(wire))
    }

    @Test
    fun parseStem_rejectsInvalid() {
        assertNull(ObzhoryStickerPack.parseStem("hello"))
        assertNull(ObzhoryStickerPack.parseStem("[[obzhory:incomplete"))
    }
}
