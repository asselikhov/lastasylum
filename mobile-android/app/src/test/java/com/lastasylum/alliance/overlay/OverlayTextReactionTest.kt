package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTextReactionTest {

    @Test
    fun encodeDecode_roundTrip() {
        val id = encodeTextReactionId("Привет, команда!")
        assertNotNull(id)
        assertTrue(isTextReactionId(id!!))
        assertEquals("Привет, команда!", decodeTextReactionId(id))
    }

    @Test
    fun blank_returnsNull() {
        assertNull(encodeTextReactionId("   "))
        assertNull(decodeTextReactionId("heart"))
    }

    @Test
    fun trimsAndCollapsesWhitespace() {
        val id = encodeTextReactionId("  два   слова  ")
        assertEquals("два слова", decodeTextReactionId(id!!))
    }
}
