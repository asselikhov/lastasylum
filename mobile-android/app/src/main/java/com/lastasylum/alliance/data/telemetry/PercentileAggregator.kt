package com.lastasylum.alliance.data.telemetry

object PercentileAggregator {
    fun percentile(sortedValues: List<Long>, p: Double): Long {
        if (sortedValues.isEmpty()) return 0L
        if (sortedValues.size == 1) return sortedValues.first()
        val clamped = p.coerceIn(0.0, 1.0)
        val index = ((sortedValues.size - 1) * clamped).toInt()
        return sortedValues[index.coerceIn(0, sortedValues.lastIndex)]
    }

    fun stats(values: List<Long>): LatencyStats {
        if (values.isEmpty()) {
            return LatencyStats(count = 0, p50Ms = 0, p95Ms = 0, p99Ms = 0)
        }
        val sorted = values.sorted()
        return LatencyStats(
            count = sorted.size,
            p50Ms = percentile(sorted, 0.50),
            p95Ms = percentile(sorted, 0.95),
            p99Ms = percentile(sorted, 0.99),
        )
    }
}
