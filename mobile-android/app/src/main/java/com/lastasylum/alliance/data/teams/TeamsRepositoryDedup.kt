package com.lastasylum.alliance.data.teams

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InFlightDedup<K, V> {
    private val mutex = Mutex()
    private var inFlight: Pair<K, CompletableDeferred<Result<V>>>? = null

    suspend fun run(key: K, block: suspend () -> Result<V>): Result<V> {
        val (isLeader, deferred) = mutex.withLock {
            val current = inFlight
            if (current?.first == key) {
                return@withLock false to current.second
            }
            val next = CompletableDeferred<Result<V>>()
            inFlight = key to next
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
                    if (inFlight?.second === deferred) {
                        inFlight = null
                    }
                }
            }
        } else {
            deferred.await()
        }
    }
}
