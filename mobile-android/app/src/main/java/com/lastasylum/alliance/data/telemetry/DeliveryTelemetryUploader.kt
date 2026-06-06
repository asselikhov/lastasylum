package com.lastasylum.alliance.data.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Sampled upload of delivery latency aggregates to backend (~1% of flush cycles).
 */
class DeliveryTelemetryUploader(
    private val telemetryApi: TelemetryApi,
    private val tracker: DeliveryLatencyTracker,
    private val scope: CoroutineScope,
) {
    fun startPeriodicUpload(intervalMs: Long = UPLOAD_INTERVAL_MS) {
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(intervalMs)
                flushSampleIfLucky()
            }
        }
    }

    fun flushSampleIfLucky() {
        if (System.currentTimeMillis() % 100L != 0L) return
        scope.launch(Dispatchers.IO) {
            val snap = tracker.snapshot()
            if (snap.sampleCount == 0) return@launch
            val samples = snap.byType.mapNotNull { (type, stats) ->
                if (stats.count <= 0) return@mapNotNull null
                DeliverySampleDto(
                    spanType = type.wire,
                    correlationId = "agg:${type.wire}",
                    durationMs = stats.p95Ms.coerceAtLeast(stats.p50Ms),
                    outcome = "ok",
                )
            }
            if (samples.isEmpty()) return@launch
            runCatching {
                telemetryApi.postDelivery(DeliveryBatchDto(samples = samples.take(50)))
            }
        }
    }

    companion object {
        private const val UPLOAD_INTERVAL_MS = 10 * 60_000L
    }
}
