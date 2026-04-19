package com.lastasylum.alliance.data.chat.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZlobyakaStickerPackTest {
    @Test
    fun encode_parse_roundTrip() {
        val stem = "1-96632-512b"
        val wire = ZlobyakaStickerPack.encode(stem)
        assertEquals("[[zlobyaka:1-96632-512b]]", wire)
        assertEquals(stem, ZlobyakaStickerPack.parseStem(wire))
    }

    @Test
    fun parse_rejects_plainText() {
        assertNull(ZlobyakaStickerPack.parseStem("hello"))
        assertNull(ZlobyakaStickerPack.parseStem("[[zlobyaka:incomplete"))
    }
}
