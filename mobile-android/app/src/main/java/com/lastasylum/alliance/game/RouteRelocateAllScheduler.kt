package com.lastasylum.alliance.game

import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicReference

/** Отложенный запуск «Переместить всех» по маршруту. */
class RouteRelocateAllScheduler(
    private val mainHandler: Handler,
    private val onFire: (RoutePlannerRoute) -> Unit,
) {
    private val pendingRoute = AtomicReference<RoutePlannerRoute?>(null)
    private val pendingAtMs = AtomicReference(0L)
    private val runnable = Runnable {
        val route = pendingRoute.getAndSet(null) ?: return@Runnable
        pendingAtMs.set(0L)
        onFire(route)
    }

    fun schedule(route: RoutePlannerRoute, delayMs: Long) {
        cancel()
        pendingRoute.set(route)
        pendingAtMs.set(System.currentTimeMillis() + delayMs.coerceAtLeast(0L))
        mainHandler.postDelayed(runnable, delayMs.coerceAtLeast(0L))
    }

    fun cancel() {
        mainHandler.removeCallbacks(runnable)
        pendingRoute.set(null)
        pendingAtMs.set(0L)
    }

    fun pendingRoute(): RoutePlannerRoute? = pendingRoute.get()

    fun pendingAtMs(): Long = pendingAtMs.get()

    fun isScheduled(): Boolean = pendingRoute.get() != null
}
