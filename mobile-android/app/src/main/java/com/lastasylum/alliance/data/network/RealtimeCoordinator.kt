package com.lastasylum.alliance.data.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Debounces socket reconnects after a single access-token refresh.
 */
class RealtimeCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reconnectHandlers = CopyOnWriteArrayList<() -> Unit>()
    private var debouncedReconnectJob: Job? = null

    fun registerReconnect(handler: () -> Unit) {
        if (!reconnectHandlers.contains(handler)) {
            reconnectHandlers.add(handler)
        }
    }

    fun unregisterReconnect(handler: () -> Unit) {
        reconnectHandlers.remove(handler)
    }

    fun onAccessTokenRefreshed() {
        debouncedReconnectJob?.cancel()
        debouncedReconnectJob = scope.launch {
            delay(RECONNECT_DEBOUNCE_MS)
            reconnectHandlers.forEach { handler ->
                runCatching { handler() }
            }
        }
    }

    private companion object {
        private const val RECONNECT_DEBOUNCE_MS = 400L
    }
}
