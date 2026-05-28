package com.lastasylum.alliance.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Periodically reports presence while the combat overlay is active (e.g. user in another app / game).
 * Main-app [Navigation] already pings "online" on a timer; this path marks "ingame" for allies.
 */
class OverlayPresenceHeartbeat(
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val ping: suspend () -> Unit,
) {
    private var heartbeatJob: Job? = null

    fun start() {
        stop()
        heartbeatJob = scope.launch {
            while (isActive) {
                runCatching { ping() }
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}
