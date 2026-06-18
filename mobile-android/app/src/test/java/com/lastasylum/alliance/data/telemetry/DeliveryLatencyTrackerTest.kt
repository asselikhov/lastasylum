package com.lastasylum.alliance.data.telemetry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeliveryLatencyTrackerTest {
    private lateinit var tracker: DeliveryLatencyTracker

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = SquadRelayDatabase.createInMemory(context)
        tracker = DeliveryLatencyTracker(db, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun endSpanByCorrelation_recordsSampleInSnapshot() {
        tracker.startSpan(LatencySpanType.ChatSendToSocket, "client-abc")
        Thread.sleep(5)
        tracker.endSpanByCorrelation(LatencySpanType.ChatSendToSocket, "client-abc", "ok")
        val stats = tracker.snapshot().byType[LatencySpanType.ChatSendToSocket]
        assertTrue((stats?.count ?: 0) >= 1)
        assertTrue((stats?.p50Ms ?: 0L) >= 0L)
    }

    @Test
    fun overlayRaidQuickCommandSend_recordsSampleInSnapshot() {
        tracker.startSpan(LatencySpanType.OverlayRaidQuickCommandSend, "client-abc")
        tracker.endSpanByCorrelation(LatencySpanType.OverlayRaidQuickCommandSend, "client-abc", "ok")
        val stats = tracker.snapshot().byType[LatencySpanType.OverlayRaidQuickCommandSend]
        assertEquals(1, stats?.count)
    }

    @Test
    fun sendChain_optimisticPaint_httpAck_socketAndPeerVisible() {
        val clientId = "chain-client-1"
        tracker.startSpan(LatencySpanType.ChatSendToOptimisticPaint, clientId)
        tracker.startSpan(LatencySpanType.ChatSendToHttpAck, clientId)
        tracker.startSpan(LatencySpanType.ChatSendToSocket, clientId)
        tracker.endSpanByCorrelation(LatencySpanType.ChatSendToOptimisticPaint, clientId, "ok")
        tracker.endSpanByCorrelation(LatencySpanType.ChatSendToHttpAck, clientId, "ok")
        tracker.endSpanByCorrelation(LatencySpanType.ChatSendToSocket, clientId, "ok")
        val peerId = "507f1f77bcf86cd799439011"
        tracker.startSpan(LatencySpanType.ChatPeerMessageVisible, peerId)
        tracker.endSpanByCorrelation(LatencySpanType.ChatPeerMessageVisible, peerId, "ok")
        val snap = tracker.snapshot()
        assertEquals(1, snap.byType[LatencySpanType.ChatSendToOptimisticPaint]?.count)
        assertEquals(1, snap.byType[LatencySpanType.ChatSendToHttpAck]?.count)
        assertEquals(1, snap.byType[LatencySpanType.ChatSendToSocket]?.count)
        assertEquals(1, snap.byType[LatencySpanType.ChatPeerMessageVisible]?.count)
    }

    @Test
    fun snapshot_aggregatesMultipleSamples() {
        repeat(3) { i ->
            val id = tracker.startSpan(LatencySpanType.ChatReadReceipt, "msg-$i")
            tracker.endSpan(id, "ok")
        }
        val stats = tracker.snapshot().byType[LatencySpanType.ChatReadReceipt]
        assertEquals(3, stats?.count)
    }
}
