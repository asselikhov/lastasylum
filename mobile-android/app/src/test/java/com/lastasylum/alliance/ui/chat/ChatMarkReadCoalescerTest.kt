package com.lastasylum.alliance.ui.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatMarkReadCoalescerTest {
    @Test
    fun coalesce_multipleIds_singleNetworkWithMax() = runTest {
        val coalescer = ChatMarkReadCoalescer(this)
        var cursor = "507f1f77bcf86cd799439010"
        val optimistic = mutableListOf<String>()
        val network = mutableListOf<String>()

        coalescer.schedule(
            roomId = "room-1",
            messageId = "507f1f77bcf86cd799439011",
            forceSync = false,
            getCurrentCursor = { cursor },
            onOptimisticAdvance = { _, mid ->
                optimistic.add(mid)
                cursor = mid
            },
            onNetworkMarkRead = { _, mid -> network.add(mid); true },
        )
        coalescer.schedule(
            roomId = "room-1",
            messageId = "507f1f77bcf86cd799439012",
            forceSync = false,
            getCurrentCursor = { cursor },
            onOptimisticAdvance = { _, mid ->
                optimistic.add(mid)
                cursor = mid
            },
            onNetworkMarkRead = { _, mid -> network.add(mid); true },
        )

        assertTrue(optimistic.isNotEmpty())
        advanceTimeBy(MARK_READ_NETWORK_DEBOUNCE_MS + 100)
        assertEquals(listOf("507f1f77bcf86cd799439012"), network)
    }

    @Test
    fun duplicateId_noNetworkCall() = runTest {
        val coalescer = ChatMarkReadCoalescer(this)
        var cursor = "507f1f77bcf86cd799439012"
        val network = mutableListOf<String>()

        coalescer.schedule(
            roomId = "room-1",
            messageId = "507f1f77bcf86cd799439011",
            forceSync = false,
            getCurrentCursor = { cursor },
            onOptimisticAdvance = { _, _ -> },
            onNetworkMarkRead = { _, mid -> network.add(mid); true },
        )

        advanceTimeBy(MARK_READ_NETWORK_DEBOUNCE_MS + 100)
        assertTrue(network.isEmpty())
    }

    @Test
    fun optimisticAdvance_beforeNetwork() = runTest {
        val coalescer = ChatMarkReadCoalescer(this)
        var cursor: String? = null
        val events = mutableListOf<String>()

        coalescer.schedule(
            roomId = "room-1",
            messageId = "507f1f77bcf86cd799439011",
            forceSync = false,
            getCurrentCursor = { cursor },
            onOptimisticAdvance = { _, mid ->
                events.add("opt")
                cursor = mid
            },
            onNetworkMarkRead = { _, _ ->
                events.add("net")
                true
            },
        )

        assertEquals(listOf("opt"), events)
        advanceTimeBy(MARK_READ_NETWORK_DEBOUNCE_MS + 50)
        assertEquals(listOf("opt", "net"), events)
    }

    @Test
    fun flushAndAwait_postsImmediatelyWithoutDebounce() = runTest {
        val coalescer = ChatMarkReadCoalescer(this)
        val network = mutableListOf<String>()
        coalescer.schedule(
            roomId = "room-1",
            messageId = "507f1f77bcf86cd799439011",
            forceSync = false,
            getCurrentCursor = { null },
            onOptimisticAdvance = { _, _ -> },
            onNetworkMarkRead = { _, mid -> network.add(mid); true },
        )
        assertTrue(network.isEmpty())
        coalescer.flushAndAwait("room-1")
        assertEquals(listOf("507f1f77bcf86cd799439011"), network)
    }

    @Test
    fun flushAndAwait_allRooms() = runTest {
        val coalescer = ChatMarkReadCoalescer(this)
        val network = mutableListOf<Pair<String, String>>()
        coalescer.schedule(
            roomId = "room-a",
            messageId = "507f1f77bcf86cd799439011",
            forceSync = false,
            getCurrentCursor = { null },
            onOptimisticAdvance = { _, _ -> },
            onNetworkMarkRead = { rid, mid -> network.add(rid to mid); true },
        )
        coalescer.schedule(
            roomId = "room-b",
            messageId = "507f1f77bcf86cd799439012",
            forceSync = false,
            getCurrentCursor = { null },
            onOptimisticAdvance = { _, _ -> },
            onNetworkMarkRead = { rid, mid -> network.add(rid to mid); true },
        )
        coalescer.flushAndAwait()
        assertEquals(2, network.size)
        assertTrue(network.any { it.first == "room-a" })
        assertTrue(network.any { it.first == "room-b" })
    }

    @Test
    fun networkFailure_retainsPendingForRetry() = runTest {
        val coalescer = ChatMarkReadCoalescer(this)
        var attempts = 0
        coalescer.schedule(
            roomId = "room-1",
            messageId = "507f1f77bcf86cd799439011",
            forceSync = false,
            getCurrentCursor = { null },
            onOptimisticAdvance = { _, _ -> },
            onNetworkMarkRead = { _, _ ->
                attempts++
                false
            },
        )
        advanceTimeBy(MARK_READ_NETWORK_DEBOUNCE_MS + 100)
        assertEquals(1, attempts)
        assertTrue(coalescer.hasPending("room-1"))
        coalescer.schedule(
            roomId = "room-1",
            messageId = "507f1f77bcf86cd799439012",
            forceSync = false,
            getCurrentCursor = { "507f1f77bcf86cd799439011" },
            onOptimisticAdvance = { _, _ -> },
            onNetworkMarkRead = { _, mid ->
                attempts++
                mid == "507f1f77bcf86cd799439012"
            },
        )
        advanceTimeBy(MARK_READ_NETWORK_DEBOUNCE_MS + 100)
        assertEquals(2, attempts)
        assertFalse(coalescer.hasPending("room-1"))
    }

    @Test
    fun shouldFetchPeerReadCursor_respectsInterval() {
        val now = 100_000L
        assertTrue(shouldFetchPeerReadCursor(lastAtMs = 0L, nowMs = now))
        assertFalse(shouldFetchPeerReadCursor(lastAtMs = now - 5_000L, nowMs = now))
        assertTrue(shouldFetchPeerReadCursor(lastAtMs = now - 31_000L, nowMs = now))
        assertFalse(
            shouldFetchPeerReadCursor(
                lastAtMs = now - 5_000L,
                nowMs = now,
                force = true,
            ),
        )
        assertTrue(
            shouldFetchPeerReadCursor(
                lastAtMs = now - 31_000L,
                nowMs = now,
                force = true,
            ),
        )
    }
}
