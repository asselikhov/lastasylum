package com.lastasylum.alliance.overlay

import android.os.Handler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Periodically reports presence while the combat overlay is active (e.g. user in another app / game).
 * Main-app [Navigation] already pings "online" on a timer; this path marks "ingame" for allies.
 */
class OverlayPresenceHeartbeat(
    private val mainHandler: Handler,
    private val scope: CoroutineScope,
    private val intervalMs: Long,
    private val ping: suspend () -> Unit,
) {
    private val tickRunnable = Runnable { tick() }

    private fun tick() {
        scope.launch {
            runCatching { ping() }
            mainHandler.postDelayed(tickRunnable, intervalMs)
        }
    }

    fun start() {
        stop()
        mainHandler.post(tickRunnable)
    }

    fun stop() {
        mainHandler.removeCallbacks(tickRunnable)
    }
}
