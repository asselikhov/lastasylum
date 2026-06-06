package com.lastasylum.alliance.data.telemetry

import android.util.Log
import com.lastasylum.alliance.BuildConfig
import com.lastasylum.alliance.data.chat.store.LatencySampleEntity
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class DeliveryLatencyTracker(
    private val db: SquadRelayDatabase,
    private val scope: CoroutineScope,
) {
    private val nextSpanId = AtomicLong(1L)
    private val openSpans = ConcurrentHashMap<Long, OpenSpan>()
    private val ringBuffer = ArrayDeque<CompletedSample>(RING_CAPACITY)

    data class OpenSpan(
        val id: Long,
        val type: LatencySpanType,
        val correlationId: String,
        val startedAtMs: Long,
    )

    private data class CompletedSample(
        val type: LatencySpanType,
        val correlationId: String,
        val startedAtMs: Long,
        val durationMs: Long,
        val outcome: String,
    )

    fun startSpan(type: LatencySpanType, correlationId: String): Long {
        val id = nextSpanId.incrementAndGet()
        openSpans[id] = OpenSpan(
            id = id,
            type = type,
            correlationId = correlationId.trim(),
            startedAtMs = System.currentTimeMillis(),
        )
        return id
    }

    fun endSpan(spanId: Long, outcome: String = "ok") {
        val open = openSpans.remove(spanId) ?: return
        record(open.type, open.correlationId, open.startedAtMs, outcome)
    }

    fun endSpanByCorrelation(type: LatencySpanType, correlationId: String, outcome: String = "ok") {
        val cid = correlationId.trim()
        val match = openSpans.values.firstOrNull {
            it.type == type && it.correlationId == cid
        } ?: return
        endSpan(match.id, outcome)
    }

    fun snapshot(windowMs: Long = FIVE_MIN_MS): LatencySnapshot {
        val cutoff = System.currentTimeMillis() - windowMs
        val recent = ringBuffer.filter { it.startedAtMs >= cutoff }
        val grouped = recent.groupBy { it.type }
        val byType = LatencySpanType.entries.associateWith { type ->
            PercentileAggregator.stats(grouped[type].orEmpty().map { it.durationMs })
        }
        return LatencySnapshot(
            byType = byType,
            sampleCount = recent.size,
            windowStartedAtMs = cutoff,
        )
    }

    fun logSnapshotIfDebug() {
        if (!BuildConfig.DEBUG) return
        val snap = snapshot()
        Log.i(TAG, snap.toLogLine())
    }

    private fun record(type: LatencySpanType, correlationId: String, startedAtMs: Long, outcome: String) {
        val durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
        val sample = CompletedSample(
            type = type,
            correlationId = correlationId,
            startedAtMs = startedAtMs,
            durationMs = durationMs,
            outcome = outcome,
        )
        synchronized(ringBuffer) {
            ringBuffer.addLast(sample)
            while (ringBuffer.size > RING_CAPACITY) {
                ringBuffer.removeFirst()
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "${type.wire} cid=$correlationId ms=$durationMs outcome=$outcome")
        }
        if (shouldPersistSample()) {
            scope.launch(Dispatchers.IO) {
                db.latencySampleDao().insert(
                    LatencySampleEntity(
                        spanType = type.wire,
                        correlationId = correlationId,
                        startedAtMs = startedAtMs,
                        durationMs = durationMs,
                        outcome = outcome,
                    ),
                )
            }
        }
    }

    private fun shouldPersistSample(): Boolean = (System.currentTimeMillis() % 10L) == 0L

    private fun LatencySnapshot.toLogLine(): String {
        val parts = byType.entries.joinToString(" ") { (type, stats) ->
            "${type.wire}:n=${stats.count} p50=${stats.p50Ms} p95=${stats.p95Ms}"
        }
        return "samples=$sampleCount $parts"
    }

    companion object {
        private const val TAG = "SR_Latency"
        private const val RING_CAPACITY = 500
        private const val FIVE_MIN_MS = 5 * 60_000L
    }
}
