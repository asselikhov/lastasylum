package com.lastasylum.alliance.data.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class PercentileAggregatorTest {
    @Test
    fun stats_computesP50AndP95() {
        val values = (1L..100L).toList()
        val stats = PercentileAggregator.stats(values)
        assertEquals(100, stats.count)
        assertEquals(50L, stats.p50Ms)
        assertEquals(95L, stats.p95Ms)
    }

    @Test
    fun stats_empty_returnsZeros() {
        val stats = PercentileAggregator.stats(emptyList())
        assertEquals(0, stats.count)
        assertEquals(0L, stats.p50Ms)
    }
}
