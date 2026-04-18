package com.lastasylum.alliance.overlay

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Detects whether the user is likely in the target game using [UsageStatsManager].
 * Requires the user to grant **Settings → Special app access → Usage access** for SquadRelay.
 */
object GameForegroundGate {
    /** Default for «Last Asylum: Plague»; can be overridden in settings. */
    const val DEFAULT_TARGET_GAME_PACKAGE = "com.lastasylum.plague"
    @Volatile
    private var cachedForeground: CachedForeground? = null

    private data class CachedForeground(
        val windowMs: Long,
        val cachedAtMs: Long,
        val packageName: String?,
    )

    fun hasUsageStatsAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Last package that received ACTIVITY_RESUMED / MOVE_TO_FOREGROUND in a short window.
     * Returns null if usage access is missing or query failed.
     */
    fun lastResumedPackage(context: Context, windowMs: Long = 20_000L): String? {
        if (!hasUsageStatsAccess(context)) return null
        val now = System.currentTimeMillis()
        cachedForeground?.takeIf {
            it.windowMs == windowMs && now - it.cachedAtMs <= FOREGROUND_CACHE_MS
        }?.let { return it.packageName }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = now
        val begin = end - windowMs
        return try {
            val events = usm.queryEvents(begin, end)
            val ev = UsageEvents.Event()
            var last: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                val type = ev.eventType
                val isResume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    type == UsageEvents.Event.ACTIVITY_RESUMED
                } else {
                    @Suppress("DEPRECATION")
                    type == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (isResume) {
                    last = ev.packageName
                }
            }
            cachedForeground = CachedForeground(
                windowMs = windowMs,
                cachedAtMs = now,
                packageName = last,
            )
            last
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Show overlay when the target game is in foreground, or when the user is touching our app
     * (overlay / launcher) so the strip does not disappear while interacting with SquadRelay.
     */
    fun shouldShowOverlay(
        context: Context,
        targetGamePackage: String,
    ): Boolean {
        val target = targetGamePackage.trim()
        if (target.isEmpty()) return false
        val last = lastResumedPackage(context) ?: return false
        val alliance = context.packageName
        if (last == alliance) return true
        return last == target
    }

    /** Pure helper for unit tests. */
    internal fun isEligibleForegroundResume(
        lastResumedPackage: String?,
        alliancePackage: String,
        targetGamePackage: String,
    ): Boolean {
        val target = targetGamePackage.trim()
        if (target.isEmpty()) return false
        val last = lastResumedPackage ?: return false
        if (last == alliancePackage) return true
        return last == target
    }

    private const val FOREGROUND_CACHE_MS = 1_500L
}
