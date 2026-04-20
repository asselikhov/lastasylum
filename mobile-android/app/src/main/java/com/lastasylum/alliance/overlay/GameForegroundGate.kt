package com.lastasylum.alliance.overlay

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlin.math.max

/**
 * Detects whether the user is likely in the target game using [UsageStatsManager].
 * Requires the user to grant **Settings → Special app access → Usage access** for SquadRelay.
 */
object GameForegroundGate {
    /** Default CSV: Google Play RU (`com.phs.global`) + com.lastasylum.plague*; override in settings. */
    const val DEFAULT_TARGET_GAME_PACKAGES_CSV =
        "com.phs.global,com.lastasylum.plague,com.lastasylum.plague.debug"
    @Volatile
    private var cachedForeground: CachedForeground? = null

    private data class CachedForeground(
        val eventWindowMs: Long,
        val cachedAtMs: Long,
        val packageName: String?,
    )

    fun hasUsageStatsAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                ) == AppOpsManager.MODE_ALLOWED
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                ) == AppOpsManager.MODE_ALLOWED
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Last package that received ACTIVITY_RESUMED / MOVE_TO_FOREGROUND in an effective window
     * (at least [RESUME_EVENTS_WINDOW_MS]), or — if there were no resume events — the package with the greatest
     * [UsageStats.getLastTimeUsed] over [USAGE_STATS_LOOKBACK_MS] (steady gameplay otherwise
     * produced no events in a short window and the overlay gate hid the UI).
     */
    fun lastResumedPackage(context: Context, windowMs: Long = RESUME_EVENTS_WINDOW_MS): String? {
        if (!hasUsageStatsAccess(context)) return null
        val now = System.currentTimeMillis()
        val effectiveWindow = max(windowMs, RESUME_EVENTS_WINDOW_MS)
        cachedForeground?.takeIf {
            it.eventWindowMs == effectiveWindow && now - it.cachedAtMs <= FOREGROUND_CACHE_MS
        }?.let { return it.packageName }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        return try {
            val fromEvents = lastMeaningfulResumePackageFromEvents(usm, now, effectiveWindow)
            val resolved = fromEvents ?: mostRecentPackageByLastTimeUsed(usm, now, USAGE_STATS_LOOKBACK_MS)
            cachedForeground = CachedForeground(
                eventWindowMs = effectiveWindow,
                cachedAtMs = now,
                packageName = resolved,
            )
            resolved
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Последний пакет с RESUME в окне, но игнорируя «шум» (System UI и т.п.),
     * иначе последнее событие часто оказывается [com.android.systemui] при жестах/шторке —
     * гейт думает, что игра не на экране, хотя пользователь в игре.
     */
    private fun lastMeaningfulResumePackageFromEvents(
        usm: UsageStatsManager,
        endMs: Long,
        windowMs: Long,
    ): String? {
        val begin = endMs - windowMs
        val events = usm.queryEvents(begin, endMs)
        val ev = UsageEvents.Event()
        val resumes = ArrayList<Pair<Long, String>>(48)
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
                resumes.add(ev.timeStamp to ev.packageName)
            }
        }
        if (resumes.isEmpty()) return null
        return selectMeaningfulForegroundResume(resumes.map { it.second })
    }

    /**
     * Из хронологического списка RESUME (старые → новые) — последний пакет не из [RESUME_DECOR_PACKAGES].
     * Для тестов и для [lastMeaningfulResumePackageFromEvents].
     */
    internal fun selectMeaningfulForegroundResume(resumePackagesChronologicalOrder: List<String>): String? {
        if (resumePackagesChronologicalOrder.isEmpty()) return null
        for (pkg in resumePackagesChronologicalOrder.asReversed()) {
            if (pkg in RESUME_DECOR_PACKAGES) continue
            return pkg
        }
        return resumePackagesChronologicalOrder.last()
    }

    private fun mostRecentPackageByLastTimeUsed(
        usm: UsageStatsManager,
        endMs: Long,
        lookbackMs: Long,
    ): String? {
        val stats = queryUsageStatsMerged(usm, endMs, lookbackMs)
        if (stats.isEmpty()) return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun queryUsageStatsMerged(
        usm: UsageStatsManager,
        endMs: Long,
        lookbackMs: Long,
    ): List<UsageStats> {
        val begin = endMs - lookbackMs
        val best = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, endMs)
        if (!best.isNullOrEmpty()) return best
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, endMs).orEmpty()
    }

    /**
     * Show overlay when the target game is in foreground, or when the user is touching our app
     * (overlay / launcher) so the strip does not disappear while interacting with SquadRelay.
     *
     * [targetGamePackages] — один или несколько applicationId (например release и debug через запятую в настройках).
     */
    fun shouldShowOverlay(
        context: Context,
        targetGamePackages: Collection<String>,
    ): Boolean {
        val targets = targetGamePackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (targets.isEmpty()) return false
        if (!hasUsageStatsAccess(context)) return false
        val alliance = context.packageName
        val hinted = lastResumedPackage(context)
        if (hinted == alliance) return true
        if (hinted != null && targets.contains(hinted)) return true
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val end = System.currentTimeMillis()
        return targets.any { target ->
            isTargetUsageNearLeader(usm, end, target, USAGE_STATS_LOOKBACK_MS, TARGET_USAGE_TIE_SLOP_MS)
        }
    }

    /**
     * True if [target]'s last-used timestamp is within [slopMs] of the global maximum in [lastUsedByPackage].
     * Handles Unity/in-game sessions where UsageEvents name System UI, but the game still has an up-to-date
     * [UsageStats.getLastTimeUsed].
     */
    internal fun isTargetLastUsedNearLeader(
        lastUsedByPackage: Map<String, Long>,
        target: String,
        slopMs: Long,
    ): Boolean {
        val t = lastUsedByPackage[target] ?: return false
        val maxLast = lastUsedByPackage.values.maxOrNull() ?: return false
        return t >= maxLast - slopMs
    }

    private fun isTargetUsageNearLeader(
        usm: UsageStatsManager,
        endMs: Long,
        target: String,
        lookbackMs: Long,
        slopMs: Long,
    ): Boolean {
        val stats = queryUsageStatsMerged(usm, endMs, lookbackMs)
        if (stats.isEmpty()) return false
        val map = stats.associate { it.packageName to it.lastTimeUsed }
        return isTargetLastUsedNearLeader(map, target, slopMs)
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

    /**
     * Окно поиска RESUME-событий: при длинной сессии в игре новых событий может не быть долго;
     * слишком короткое окно даёт пустой поток и слабый fallback по [lastTimeUsed].
     */
    private const val RESUME_EVENTS_WINDOW_MS = 120_000L

    /** Long lookback for [UsageStatsManager.queryUsageStats] when the resume-event stream is empty. */
    private const val USAGE_STATS_LOOKBACK_MS = 60 * 60 * 1000L

    /**
     * Пакеты, чей MOVE_TO_FOREGROUND не должен «перебивать» игру для гейта оверлея.
     */
    private val RESUME_DECOR_PACKAGES: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
    )

    /**
     * How much the game's [UsageStats.getLastTimeUsed] may lag behind the global leader (e.g. System UI)
     * while the user is still effectively in-game.
     */
    private const val TARGET_USAGE_TIE_SLOP_MS = 20_000L
}
