package com.lastasylum.alliance.data.teams

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TeamsRepositoryDedupTest {
    @Test
    fun inFlightDedup_sharesSingleResultForParallelCalls() = runBlocking {
        val dedup = InFlightDedup<String, String>()
        val calls = AtomicInteger(0)
        val leaderStarted = CompletableDeferred<Unit>()
        val first = async {
            dedup.run("team-1") {
                calls.incrementAndGet()
                leaderStarted.complete(Unit)
                delay(100)
                Result.success("ok")
            }
        }
        leaderStarted.await()
        val second = async {
            dedup.run("team-1") {
                calls.incrementAndGet()
                Result.success("should-not-run")
            }
        }
        assertEquals("ok", first.await().getOrThrow())
        assertEquals("ok", second.await().getOrThrow())
        assertEquals(1, calls.get())
    }

    @Test
    fun inFlightDedup_allowsSeparateKeys() = runBlocking {
        val dedup = InFlightDedup<String, Int>()
        val a = dedup.run("a") { Result.success(1) }
        val b = dedup.run("b") { Result.success(2) }
        assertTrue(a.isSuccess)
        assertTrue(b.isSuccess)
        assertEquals(1, a.getOrThrow())
        assertEquals(2, b.getOrThrow())
    }
}
