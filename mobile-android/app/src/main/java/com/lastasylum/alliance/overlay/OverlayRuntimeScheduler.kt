package com.lastasylum.alliance.overlay

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.settings.UserSettingsPreferences

/**
 * Периодически поднимает [CombatOverlayService], если процесс SquadRelay убит (recents/OEM),
 * но панель включена — иначе гейт игры некому опрашивать.
 */
object OverlayRuntimeScheduler {
    const val ACTION_WATCHDOG = "com.lastasylum.alliance.overlay.WATCHDOG"
    private const val TAG = "OverlayRuntimeScheduler"
    private const val REQUEST_CODE = 41_021
    private const val INTERVAL_SERVICE_UP_MS = 90 * 1000L
    private const val INTERVAL_SERVICE_DOWN_MS = 15 * 1000L
    private const val INTERVAL_SERVICE_DOWN_MAX_MS = 5 * 60 * 1000L
    private const val RETRY_AFTER_KILL_MS = 5_000L
    @Volatile
    private var downBackoffMs: Long = INTERVAL_SERVICE_DOWN_MS

    fun syncSchedule(context: Context) {
        val app = context.applicationContext
        if (!shouldKeepWatchdog(app)) {
            cancel(app)
            return
        }
        val delayMs = if (CombatOverlayService.isServiceInstanceActive) {
            downBackoffMs = INTERVAL_SERVICE_DOWN_MS
            INTERVAL_SERVICE_UP_MS
        } else {
            val current = downBackoffMs
            downBackoffMs = (current * 2).coerceAtMost(INTERVAL_SERVICE_DOWN_MAX_MS)
            current
        }
        schedule(app, delayMs = delayMs)
    }

    fun scheduleImmediateRetry(context: Context, delayMs: Long = RETRY_AFTER_KILL_MS) {
        val app = context.applicationContext
        if (!shouldKeepWatchdog(app)) return
        downBackoffMs = INTERVAL_SERVICE_DOWN_MS
        schedule(app, delayMs = delayMs.coerceAtLeast(3_000L))
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.cancel(watchdogPendingIntent(context.applicationContext)) }
        downBackoffMs = INTERVAL_SERVICE_DOWN_MS
    }

    internal fun scheduleNextWatchdog(context: Context) {
        syncSchedule(context)
    }

    private fun shouldKeepWatchdog(app: Context): Boolean {
        if (!UserSettingsPreferences(app).isOverlayPanelEnabled()) return false
        return runCatching { AppContainer.from(app).authRepository.hasSession() }.getOrDefault(false)
    }

    private fun schedule(context: Context, delayMs: Long) {
        val app = context.applicationContext
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        val pi = watchdogPendingIntent(app)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "watchdog scheduled in ${delayMs}ms serviceUp=${CombatOverlayService.isServiceInstanceActive}")
        }.onFailure { e ->
            Log.w(TAG, "watchdog schedule failed", e)
        }
    }

    private fun watchdogPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, OverlayRuntimeReceiver::class.java).apply {
            action = ACTION_WATCHDOG
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
