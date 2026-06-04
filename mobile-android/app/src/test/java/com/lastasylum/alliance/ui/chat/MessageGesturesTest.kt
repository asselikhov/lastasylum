package com.lastasylum.alliance.ui.chat

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageGesturesTest {

    @Test
    fun messageActionOpenRequest_carriesAnchor() {
        val rect = Rect(10f, 20f, 110f, 80f)
        val req = MessageActionOpenRequest(messageId = "m1", anchorBounds = rect)
        assertEquals("m1", req.messageId)
        assertEquals(rect, req.anchorBounds)
    }
}
