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
    fun snapshot_aggregatesMultipleSamples() {
        repeat(3) { i ->
            val id = tracker.startSpan(LatencySpanType.ChatReadReceipt, "msg-$i")
            tracker.endSpan(id, "ok")
        }
        val stats = tracker.snapshot().byType[LatencySpanType.ChatReadReceipt]
        assertEquals(3, stats?.count)
    }
}
