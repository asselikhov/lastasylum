package com.lastasylum.alliance.overlay

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Detects whether the user is likely in the target game using [UsageStatsManager].
 * Requires the user to grant **Settings → Special app access → Usage access** for SquadRelay.
 */
object GameForegroundGate {
    /** Default CSV: Google Play RU (`com.phs.global`) + com.lastasylum.plague*; override in settings. */
    const val DEFAULT_TARGET_GAME_PACKAGES_CSV =
        "com.phs.global,com.lastasylum.plague,com.lastasylum.plague.debug"

    private val ttfLock = Any()
    private val observedTotalTimeForeground = ConcurrentHashMap<String, Long>()
    @Volatile
    private var ttfWatchTargetsKey: String = ""

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
            val uid = Process.myUid()
            val pkg = context.packageName
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    uid,
                    pkg,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    uid,
                    pkg,
                )
            }
            when (mode) {
                AppOpsManager.MODE_ALLOWED -> true
                // HyperOS/MIUI: при включённом доступе иногда MODE_DEFAULT, хотя query* уже отдаёт данные.
                AppOpsManager.MODE_DEFAULT -> usageStatsProbeReturnsData(context)
                else -> false
            }
        } catch (_: Throwable) {
            false
        }
    }

    /** Реально ли отвечает UsageStatsManager (важно для MODE_DEFAULT на части прошивок). */
    private fun usageStatsProbeReturnsData(context: Context): Boolean {
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@runCatching false
            val end = System.currentTimeMillis()
            val begin = end - 86_400_000L
            val list = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
            !list.isNullOrEmpty()
        }.getOrDefault(false)
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
        val daily = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, endMs).orEmpty()
        if (daily.isNotEmpty()) return daily
        val weekly = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, begin, endMs).orEmpty()
        if (weekly.isNotEmpty()) return weekly
        return usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, begin, endMs).orEmpty()
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
        val targetSet = targets.toSet()
        val hinted = lastResumedPackage(context)
        if (hinted == alliance) return true
        if (hinted != null && targetSet.contains(hinted)) return true
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val end = System.currentTimeMillis()
        // API 28+: события только по пакету игры — на MIUI глобальный queryEvents часто пуст/обрезан.
        for (t in targets) {
            if (runCatching { targetForegroundFromPackageOnlyEvents(usm, t, end) }.getOrDefault(false)) {
                return true
            }
        }
        // На части прошивок «последний» RESUME — не игра, а лаунчер/сервис; lastTimeUsed при долгой сессии
        // в Unity не обновляется. Сравниваем макс. время RESUME цели с прочими (кроме декора и SquadRelay).
        if (runCatching {
                targetWinsResumeTimeline(usm, end, RESUME_VS_RESUME_WINDOW_MS, targetSet, alliance)
            }.getOrDefault(false)
        ) {
            return true
        }
        val stats = runCatching {
            queryUsageStatsMerged(usm, end, USAGE_STATS_LOOKBACK_MS)
        }.getOrDefault(emptyList())
        // MIUI/HyperOS: события и lastTimeUsed часто «молчат» в Unity; TTF между тиками гейта растёт только в фокусе.
        if (targetForegroundByGrowingTotalTime(stats, targetSet)) {
            return true
        }
        if (stats.isEmpty()) return false
        val effectiveMap = stats.associate { it.packageName to effectiveUsageTimestamp(it) }
        return targets.any { target ->
            isTargetLastUsedNearLeader(effectiveMap, target, TARGET_USAGE_TIE_SLOP_MS)
        }
    }

    /**
     * Сравнивает [UsageStats.getTotalTimeInForeground] между последовательными вызовами [shouldShowOverlay]
     * для тех же целевых пакетов: в фокусе время накапливается, в фоне — нет.
     */
    internal fun totalTimeForegroundIncreased(prev: Long?, now: Long): Boolean =
        prev != null && now > prev

    private fun targetForegroundByGrowingTotalTime(
        stats: List<UsageStats>,
        targets: Set<String>,
    ): Boolean = synchronized(ttfLock) {
        val key = targets.sorted().joinToString(",")
        if (key != ttfWatchTargetsKey) {
            observedTotalTimeForeground.clear()
            ttfWatchTargetsKey = key
        }
        var grown = false
        for (t in targets) {
            val stat = stats.find { it.packageName == t } ?: continue
            val ttf = stat.totalTimeInForeground
            val prev = observedTotalTimeForeground[t]
            observedTotalTimeForeground[t] = ttf
            if (totalTimeForegroundIncreased(prev, ttf)) {
                grown = true
            }
        }
        grown
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

    /** API 29+: учитываем [UsageStats.getLastTimeVisible] — при длительной игре он часто свежее [getLastTimeUsed]. */
    private fun effectiveUsageTimestamp(stat: UsageStats): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return max(stat.lastTimeUsed, stat.lastTimeVisible)
        }
        return stat.lastTimeUsed
    }

    /**
     * True, если среди RESUME в окне у целевой игры метка времени не меньше, чем у любого «чужого»
     * приложения (не SquadRelay, не декор). Так видно «кто реально выиграл фокус», даже если
     * [selectMeaningfulForegroundResume] вернул не пакет игры.
     */
    private fun targetWinsResumeTimeline(
        usm: UsageStatsManager,
        endMs: Long,
        windowMs: Long,
        targets: Set<String>,
        alliance: String,
    ): Boolean {
        val pairs = collectResumePairs(usm, endMs - windowMs, endMs)
        return targetWinsResumeTimelineFromPairs(pairs, targets, alliance)
    }

    private fun collectResumePairs(
        usm: UsageStatsManager,
        beginMs: Long,
        endMs: Long,
    ): List<Pair<Long, String>> {
        val events = usm.queryEvents(beginMs, endMs)
        val ev = UsageEvents.Event()
        return buildList {
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
                    val p = ev.packageName ?: continue
                    add(ev.timeStamp to p)
                }
            }
        }
    }

    internal fun targetWinsResumeTimelineFromPairs(
        resumesChronological: List<Pair<Long, String>>,
        targets: Set<String>,
        alliance: String,
    ): Boolean {
        var lastTargetTs = Long.MIN_VALUE
        var lastOtherTs = Long.MIN_VALUE
        for ((ts, p) in resumesChronological) {
            when {
                p in RESUME_DECOR_PACKAGES -> continue
                p in targets -> lastTargetTs = maxOf(lastTargetTs, ts)
                p == alliance -> continue
                else -> lastOtherTs = maxOf(lastOtherTs, ts)
            }
        }
        if (lastTargetTs == Long.MIN_VALUE) return false
        return lastTargetTs >= lastOtherTs
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
    private const val TARGET_USAGE_TIE_SLOP_MS = 45_000L

    /**
     * Окно для сравнения «последний RESUME игры» vs «последний RESUME другого приложения».
     * Должно перекрывать типичную сессию без смены активности.
     */
    private const val RESUME_VS_RESUME_WINDOW_MS = 10 * 60 * 1000L

    /** Окно [UsageStatsManager.queryEventsForPackage] — только события выбранного пакета. */
    private const val PACKAGE_ONLY_EVENTS_WINDOW_MS = 30 * 60 * 1000L

    /**
     * Состояние «на экране» по хронологии событий одного пакета ([UsageStatsManager.queryEventsForPackage]).
     */
    internal fun foregroundStateAfterPackageEventTypes(chronologicalTypes: List<Int>): Boolean {
        var seen = false
        var inFg = false
        for (type in chronologicalTypes) {
            seen = true
            when {
                isForegroundEnterEvent(type) -> inFg = true
                isForegroundLeaveEvent(type) -> inFg = false
            }
        }
        return seen && inFg
    }

    private fun isForegroundEnterEvent(type: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return type == UsageEvents.Event.ACTIVITY_RESUMED ||
                type == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        @Suppress("DEPRECATION")
        return type == UsageEvents.Event.MOVE_TO_FOREGROUND
    }

    private fun isForegroundLeaveEvent(type: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return type == UsageEvents.Event.ACTIVITY_PAUSED ||
                type == UsageEvents.Event.MOVE_TO_BACKGROUND
        }
        @Suppress("DEPRECATION")
        return type == UsageEvents.Event.MOVE_TO_BACKGROUND
    }

    /**
     * [UsageStatsManager.queryEventsForPackage] (API 28+) — вызов через reflection из‑за несовпадения stubs в toolchain.
     */
    private fun queryEventsForPackageCompat(
        usm: UsageStatsManager,
        beginMs: Long,
        endMs: Long,
        packageName: String,
    ): UsageEvents? {
        return runCatching {
            val m = UsageStatsManager::class.java.getMethod(
                "queryEventsForPackage",
                java.lang.Long.TYPE,
                java.lang.Long.TYPE,
                String::class.java,
            )
            m.invoke(usm, beginMs, endMs, packageName) as UsageEvents
        }.getOrNull()
    }

    private fun targetForegroundFromPackageOnlyEvents(
        usm: UsageStatsManager,
        target: String,
        endMs: Long,
    ): Boolean {
        val begin = endMs - PACKAGE_ONLY_EVENTS_WINDOW_MS
        val events = queryEventsForPackageCompat(usm, begin, endMs, target) ?: return false
        val ev = UsageEvents.Event()
        val types = ArrayList<Int>(32)
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            types.add(ev.eventType)
        }
        return foregroundStateAfterPackageEventTypes(types)
    }
}
