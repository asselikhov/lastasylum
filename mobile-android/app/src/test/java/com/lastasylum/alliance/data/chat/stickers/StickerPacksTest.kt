package com.lastasylum.alliance.data.chat.stickers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerPacksTest {
    @Test
    fun parse_zlobyakaWire() {
        val parsed = StickerPacks.parse("[[zlobyaka:1-96632-512b]]")
        assertNotNull(parsed)
        assertEquals("zlobyaka", parsed!!.packKey)
        assertEquals("1-96632-512b", parsed.stem)
    }

    @Test
    fun enabledPacks_filtersUnknownKeys() {
        val packs = StickerPacks.enabledPacks(setOf("zlobyaka", "unknown"))
        assertEquals(1, packs.size)
        assertEquals("zlobyaka", packs.first().packKey)
    }

    @Test
    fun enabledPacks_emptyWhenNoKeys() {
        assertTrue(StickerPacks.enabledPacks(emptySet()).isEmpty())
    }

    @Test
    fun parse_chushuyWire() {
        val parsed = StickerPacks.parse("[[chushuy:0b305b8a]]")
        assertNotNull(parsed)
        assertEquals("chushuy", parsed!!.packKey)
        assertEquals("0b305b8a", parsed.stem)
    }

    @Test
    fun enabledPacks_includesChushuyWhenGranted() {
        val packs = StickerPacks.enabledPacks(setOf("zlobyaka", "chushuy"))
        assertEquals(2, packs.size)
        assertEquals(setOf("zlobyaka", "chushuy"), packs.map { it.packKey }.toSet())
    }

    @Test
    fun parse_soidowCatWire() {
        val parsed = StickerPacks.parse("[[soidow_cat:00ef3379]]")
        assertNotNull(parsed)
        assertEquals("soidow_cat", parsed!!.packKey)
        assertEquals("00ef3379", parsed.stem)
    }

    @Test
    fun enabledPacks_includesSoidowCatWhenGranted() {
        val packs = StickerPacks.enabledPacks(setOf("soidow_cat"))
        assertEquals(1, packs.size)
        assertEquals("soidow_cat", packs.first().packKey)
    }

    @Test
    fun parse_obzhoryWire() {
        val parsed = StickerPacks.parse("[[obzhory:2]]")
        assertNotNull(parsed)
        assertEquals("obzhory", parsed!!.packKey)
        assertEquals("2", parsed.stem)
    }

    @Test
    fun enabledPacks_includesObzhoryWhenGranted() {
        val packs = StickerPacks.enabledPacks(setOf("obzhory"))
        assertEquals(1, packs.size)
        assertEquals("obzhory", packs.first().packKey)
    }
}
