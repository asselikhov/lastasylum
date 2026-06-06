package com.lastasylum.alliance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.data.telemetry.LatencySpanType

@Composable
fun DeliveryLatencyDebugScreen(
    tracker: DeliveryLatencyTracker,
    modifier: Modifier = Modifier,
) {
    val snapshot = remember(tracker) { tracker.snapshot() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Delivery latency (5 min window, n=${snapshot.sampleCount})",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(LatencySpanType.entries) { type ->
            val stats = snapshot.byType[type]
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(text = type.wire, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "count=${stats?.count ?: 0} p50=${stats?.p50Ms ?: 0}ms " +
                        "p95=${stats?.p95Ms ?: 0}ms p99=${stats?.p99Ms ?: 0}ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
