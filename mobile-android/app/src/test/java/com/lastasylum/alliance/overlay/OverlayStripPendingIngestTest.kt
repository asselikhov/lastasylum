package com.lastasylum.alliance.overlay

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayStripPendingIngestTest {
    @Before
    fun setUp() {
        // Touch ApplicationProvider so android.util.Log is initialized for ChatDeliveryMetrics.
        ApplicationProvider.getApplicationContext<Context>()
        OverlayStripPendingIngest.flush { }
    }

    @Test
    fun flush_deliversPendingMessages() {
        val msg = ChatMessage(
            _id = "m1",
            allianceId = "",
            roomId = "",
            senderId = "u2",
            senderUsername = "ally",
            senderRole = "R1",
            text = "Raid ping",
            createdAt = "2026-01-01T00:00:00Z",
        )
        OverlayStripPendingIngest.enqueue(msg)
        val ingested = mutableListOf<ChatMessage>()
        val count = OverlayStripPendingIngest.flush { ingested.add(it) }
        assertEquals(1, count)
        assertEquals("m1", ingested.single()._id)
        assertEquals(0, OverlayStripPendingIngest.pendingCount())
    }

    @Test
    fun enqueue_withoutId_usesFingerprintKey() {
        val msg = ChatMessage(
            _id = null,
            allianceId = "",
            roomId = "",
            senderId = "u2",
            senderUsername = "ally",
            senderRole = "R1",
            text = "No id yet",
            createdAt = "2026-01-01T00:00:00Z",
        )
        OverlayStripPendingIngest.enqueue(msg)
        assertTrue(OverlayStripPendingIngest.pendingCount() >= 1)
    }
}
