package com.lastasylum.alliance.data.teams

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InFlightDedup<K, V> {
    private val mutex = Mutex()
    private val inFlight = ConcurrentHashMap<K, CompletableDeferred<Result<V>>>()

    suspend fun run(key: K, block: suspend () -> Result<V>): Result<V> {
        val (isLeader, deferred) = mutex.withLock {
            inFlight[key]?.let { existing ->
                return@withLock false to existing
            }
            val next = CompletableDeferred<Result<V>>()
            inFlight[key] = next
            true to next
        }
        return if (isLeader) {
            try {
                val result = block()
                deferred.complete(result)
                result
            } catch (e: Exception) {
                val failure = Result.failure<V>(e)
                deferred.complete(failure)
                failure
            } finally {
                mutex.withLock {
                    inFlight.remove(key, deferred)
                }
            }
        } else {
            deferred.await()
        }
    }
}
