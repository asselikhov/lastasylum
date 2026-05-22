package com.lastasylum.alliance.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardUtilsTest {
    @Test
    fun appendTextToDraft_appendsWithSpace() {
        assertEquals("hello world", appendTextToDraft("hello", "world"))
    }

    @Test
    fun appendTextToDraft_emptyCurrent() {
        assertEquals("paste", appendTextToDraft("", "paste"))
    }
}
