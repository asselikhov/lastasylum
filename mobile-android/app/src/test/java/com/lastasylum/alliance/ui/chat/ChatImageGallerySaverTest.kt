package com.lastasylum.alliance.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatImageGallerySaverTest {
    @Test
    fun sniffImageMime_detectsJpeg() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00)
        assertEquals("image/jpeg", sniffImageMime(bytes))
    }

    @Test
    fun sniffImageMime_detectsPng() {
        val bytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte())
        assertEquals("image/png", sniffImageMime(bytes))
    }

    @Test
    fun sniffImageMime_returnsNullForTooShort() {
        assertNull(sniffImageMime(byteArrayOf(0x01)))
    }
}
