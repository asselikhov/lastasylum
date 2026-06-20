package com.lastasylum.alliance.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.data.settings.UserSettingsPreferences

/**
 * BOOT / разблокировка / watchdog: держит [CombatOverlayService] живым без открытия UI SquadRelay.
 */
class OverlayRuntimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext
        val pending = goAsync()
        try {
            when (action) {
                OverlayRuntimeScheduler.ACTION_WATCHDOG -> {
                    handleWatchdog(app)
                }
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED -> {
                    wakeOverlayRuntime(app, "boot")
                }
                Intent.ACTION_USER_UNLOCKED -> {
                    wakeOverlayRuntime(app, "unlock")
                }
                Intent.ACTION_USER_PRESENT -> {
                    wakeOverlayRuntime(app, "present")
                }
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    OverlayRuntimeScheduler.scheduleImmediateRetry(app, delayMs = 45_000L)
                }
                else -> Unit
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onReceive failed action=$action", t)
        } finally {
            pending.finish()
        }
    }

    private fun handleWatchdog(app: Context) {
        OverlayRuntimeScheduler.scheduleNextWatchdog(app)
        if (!UserSettingsPreferences(app).isOverlayPanelEnabled()) return
        if (!AppContainer.from(app).authRepository.hasSession()) return
        val targets = UserSettingsPreferences(app).getOverlayTargetGamePackages()
        val usageForOverlay = GameForegroundGate.hasUsageStatsAccessForOverlay(app)
        val inGame = targets.isNotEmpty() &&
            usageForOverlay &&
            GameForegroundGate.shouldShowOverlayCached(
                app,
                targets,
                UserSettingsPreferences(app).getOverlayTargetGameActivityTokens(),
            )
        val started = CombatOverlayService.ensureRuntimeIfUserEnabled(app, showErrorToast = false)
        CombatOverlayService.requestGateRecheckIfRunning(app)
        Log.i(
            TAG,
            "watchdog serviceActive=${CombatOverlayService.isServiceInstanceActive} " +
                "ensureStarted=$started usageOverlay=$usageForOverlay inGame=$inGame " +
                "targets=${targets.joinToString()}",
        )
    }

    private fun wakeOverlayRuntime(app: Context, reason: String) {
        val started = CombatOverlayService.ensureRuntimeIfUserEnabled(app, showErrorToast = false)
        OverlayRuntimeScheduler.syncSchedule(app)
        runCatching {
            com.lastasylum.alliance.push.PushTokenRefreshScheduler.scheduleImmediate(app)
        }
        Log.i(TAG, "wakeOverlayRuntime reason=$reason started=$started")
    }

    companion object {
        private const val TAG = "OverlayRuntimeReceiver"
    }
}
