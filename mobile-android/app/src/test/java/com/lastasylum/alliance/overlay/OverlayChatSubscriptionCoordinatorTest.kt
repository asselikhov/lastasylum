package com.lastasylum.alliance.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class OverlayChatSubscriptionCoordinatorTest {
    @Test
    fun begin_whenListenerActive_runsRefreshOnly() {
        val coordinator = OverlayChatSubscriptionCoordinator()
        var refreshed = false
        var started = false
        coordinator.begin(
            hasActiveListener = true,
            onAlreadySubscribed = { refreshed = true },
            onStartSubscription = { started = true },
        )
        assertTrue(refreshed)
        assertFalse(started)
    }

    @Test
    fun begin_concurrentStarts_runBootstrapOnce() {
        val coordinator = OverlayChatSubscriptionCoordinator()
        val pool = Executors.newFixedThreadPool(8)
        val latch = CountDownLatch(1)
        val starts = AtomicInteger(0)
        try {
            repeat(16) {
                pool.submit {
                    latch.await(5, TimeUnit.SECONDS)
                    coordinator.begin(
                        hasActiveListener = false,
                        onAlreadySubscribed = {},
                        onStartSubscription = {
                            starts.incrementAndGet()
                            Thread.sleep(50)
                        },
                    )
                }
            }
            latch.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, starts.get())
    }
}
