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

    @Volatile
    private var usageAccessCacheAtMs: Long = 0L

    @Volatile
    private var usageAccessCacheValue: Boolean = false

    @Volatile
    private var mergedStatsCachedAtMs: Long = 0L

    @Volatile
    private var mergedStatsCache: List<UsageStats> = emptyList()

    private val fullHeuristicCacheLock = Any()

    @Volatile
    private var fullHeuristicCacheAtMs: Long = 0L

    @Volatile
    private var fullHeuristicCacheValue: Boolean = false

    @Volatile
    private var fullHeuristicCacheKey: String = ""

    /** Быстрый ответ по кэшированному RESUME; [NEED_FULL_HEURISTICS] — тяжёлый [shouldShowOverlay]. */
    enum class QuickForegroundProbe {
        IN_TARGET,
        NOT_IN_TARGET,
        NEED_FULL_HEURISTICS,
    }

    data class ForegroundComponent(
        val packageName: String,
        val className: String?,
    )

    private data class CachedForeground(
        val eventWindowMs: Long,
        val cachedAtMs: Long,
        val component: ForegroundComponent?,
    )

    /** Сброс кэша после возврата из «Доступ к данным об использовании» и т.п. */
    fun invalidateUsageAccessCache() {
        usageAccessCacheAtMs = 0L
    }

    /** Сброс кэша [lastResumedPackage], чтобы гейт не держал игру «на экране» 1.5s после смены приложения. */
    fun invalidateForegroundHintCache() {
        cachedForeground = null
        mergedStatsCachedAtMs = 0L
        mergedStatsCache = emptyList()
        synchronized(fullHeuristicCacheLock) {
            fullHeuristicCacheAtMs = 0L
            fullHeuristicCacheKey = ""
        }
    }

    /**
     * Лёгкий путь для [CombatOverlayService.tickGameGate]: без queryUsageStats, если RESUME однозначен.
     */
    fun quickTargetForegroundProbe(
        context: Context,
        targetGamePackages: Collection<String>,
        allowedActivitySubstrings: Collection<String> = emptyList(),
        alliancePackage: String = context.packageName,
        /** Last known foreground package from [CombatOverlayService] — skips UsageEvents when decisive. */
        preferredForegroundPackage: String? = null,
    ): QuickForegroundProbe {
        val targets = targetGamePackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (targets.isEmpty()) return QuickForegroundProbe.NOT_IN_TARGET
        if (!hasUsageStatsAccess(context)) return QuickForegroundProbe.NOT_IN_TARGET
        val targetSet = targets.toSet()
        val allowed = allowedActivitySubstrings.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val preferred = preferredForegroundPackage?.trim().orEmpty()
        // preferred — только для ускоренного «не в игре»; IN_TARGET по устаревшему пакету оставляет HUD после minimize.
        if (preferred.isNotEmpty()) {
            if (preferred == alliancePackage) return QuickForegroundProbe.NOT_IN_TARGET
            if (isConflictingForegroundHint(preferred, targetSet, alliancePackage)) {
                return QuickForegroundProbe.NOT_IN_TARGET
            }
        }
        val hintedComp = lastResumedComponent(context) ?: return QuickForegroundProbe.NEED_FULL_HEURISTICS
        val hinted = hintedComp.packageName
        if (hinted == alliancePackage) return QuickForegroundProbe.NOT_IN_TARGET
        if (isConflictingForegroundHint(hinted, targetSet, alliancePackage)) {
            return QuickForegroundProbe.NOT_IN_TARGET
        }
        if (!targetSet.contains(hinted)) return QuickForegroundProbe.NEED_FULL_HEURISTICS
        if (allowed.isEmpty()) return QuickForegroundProbe.IN_TARGET
        val cls = hintedComp.className?.trim().orEmpty()
        if (cls.isBlank()) return QuickForegroundProbe.IN_TARGET
        return if (allowed.any { token -> cls.contains(token, ignoreCase = true) }) {
            QuickForegroundProbe.IN_TARGET
        } else {
            QuickForegroundProbe.NOT_IN_TARGET
        }
    }

    fun hasUsageStatsAccess(context: Context): Boolean {
        val now = System.currentTimeMillis()
        if (now - usageAccessCacheAtMs < USAGE_ACCESS_CACHE_MS) {
            return usageAccessCacheValue
        }
        val v = computeHasUsageStatsAccess(context)
        usageAccessCacheAtMs = now
        usageAccessCacheValue = v
        return v
    }

    private fun computeHasUsageStatsAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            val uid = Process.myUid()
            val pkg = context.packageName
            val prefs = context.applicationContext.getSharedPreferences(
                USAGE_ACCESS_PREFS,
                Context.MODE_PRIVATE,
            )
            if (prefs.getBoolean(KEY_USAGE_ACCESS_GRANTED, false)) {
                val quickAllowed = isUsageStatsOpAllowed(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                    },
                )
                if (quickAllowed) return true
                prefs.edit().remove(KEY_USAGE_ACCESS_GRANTED).apply()
            }
            val modeUnsafe = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            val modeCheck = runCatching {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    uid,
                    pkg,
                )
            }.getOrElse { modeUnsafe }
            val granted = if (isUsageStatsOpAllowed(modeUnsafe) || isUsageStatsOpAllowed(modeCheck)) {
                true
            } else {
                usageStatsProbeReturnsAny(context)
            }
            if (granted) {
                prefs.edit().putBoolean(KEY_USAGE_ACCESS_GRANTED, true).apply()
            }
            granted
        } catch (_: Throwable) {
            false
        }
    }

    /** MODE_FOREGROUND (API 29+): доступ пока приложение на переднем плане — для FGS оверлея это ок. */
    private fun isUsageStatsOpAllowed(mode: Int): Boolean {
        if (mode == AppOpsManager.MODE_ALLOWED) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mode == AppOpsManager.MODE_FOREGROUND) {
            return true
        }
        return false
    }

    /** Есть ли фактический доступ: непустая статистика или поток событий. */
    private fun usageStatsProbeReturnsAny(context: Context): Boolean {
        if (usageStatsProbeReturnsData(context)) return true
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val end = System.currentTimeMillis()
        if (usageEventsProbeHasAny(usm, end, 5 * 60_000L)) return true
        return usageEventsProbeHasAny(usm, end, 24 * 60 * 60 * 1000L)
    }

    private fun usageEventsProbeHasAny(usm: UsageStatsManager, endMs: Long, windowMs: Long): Boolean {
        return runCatching {
            usm.queryEvents(endMs - windowMs, endMs).hasNextEvent()
        }.getOrDefault(false)
    }

    private fun usageStatsProbeReturnsData(context: Context): Boolean {
        return runCatching {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return@runCatching false
            val end = System.currentTimeMillis()
            val spans = listOf(
                86_400_000L to UsageStatsManager.INTERVAL_DAILY,
                7 * 86_400_000L to UsageStatsManager.INTERVAL_WEEKLY,
                90 * 86_400_000L to UsageStatsManager.INTERVAL_MONTHLY,
            )
            for ((spanMs, interval) in spans) {
                val begin = end - spanMs
                val list = usm.queryUsageStats(interval, begin, end)
                if (!list.isNullOrEmpty()) return@runCatching true
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Last package that received ACTIVITY_RESUMED / MOVE_TO_FOREGROUND in an effective window
     * (at least [RESUME_EVENTS_WINDOW_MS]), or — if there were no resume events — the package with the greatest
     * [UsageStats.getLastTimeUsed] over [USAGE_STATS_LOOKBACK_MS] (steady gameplay otherwise
     * produced no events in a short window and the overlay gate hid the UI).
     */
    fun lastResumedPackage(context: Context, windowMs: Long = RESUME_EVENTS_WINDOW_MS): String? {
        return lastResumedComponent(context, windowMs)?.packageName
    }

    /**
     * Foreground package + (when available) the resumed activity class name from [UsageEvents].
     * If there were no resume events, falls back to last-used package (className will be null).
     */
    fun lastResumedComponent(
        context: Context,
        windowMs: Long = RESUME_EVENTS_WINDOW_MS,
        forceRefresh: Boolean = false,
    ): ForegroundComponent? {
        if (!hasUsageStatsAccess(context)) return null
        if (forceRefresh) {
            cachedForeground = null
        }
        val now = System.currentTimeMillis()
        val effectiveWindow = max(windowMs, RESUME_EVENTS_WINDOW_MS)
        cachedForeground?.takeIf {
            it.eventWindowMs == effectiveWindow && now - it.cachedAtMs <= FOREGROUND_CACHE_MS
        }?.let { return it.component }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        return try {
            val fromEvents = lastMeaningfulResumeComponentFromEvents(usm, now, effectiveWindow)
            val resolved = fromEvents ?: mostRecentPackageByLastTimeUsed(usm, now, USAGE_STATS_LOOKBACK_MS)
                ?.let { ForegroundComponent(packageName = it, className = null) }
            cachedForeground = CachedForeground(
                eventWindowMs = effectiveWindow,
                cachedAtMs = now,
                component = resolved,
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
    private fun lastMeaningfulResumeComponentFromEvents(
        usm: UsageStatsManager,
        endMs: Long,
        windowMs: Long,
    ): ForegroundComponent? {
        val begin = endMs - windowMs
        val events = usm.queryEvents(begin, endMs)
        val ev = UsageEvents.Event()
        val resumes = ArrayList<Pair<Long, ForegroundComponent>>(48)
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
                val pkg = ev.packageName ?: continue
                val cls = runCatching { ev.className }.getOrNull()
                resumes.add(ev.timeStamp to ForegroundComponent(pkg, cls))
            }
        }
        if (resumes.isEmpty()) return null
        return selectMeaningfulForegroundResume(resumes.map { it.second })
    }

    /**
     * Из хронологического списка RESUME (старые → новые) — последний пакет не из [RESUME_DECOR_PACKAGES].
     * Для тестов и для [lastMeaningfulResumePackageFromEvents].
     */
    internal fun selectMeaningfulForegroundResume(
        resumesChronologicalOrder: List<ForegroundComponent>,
    ): ForegroundComponent? {
        if (resumesChronologicalOrder.isEmpty()) return null
        for (c in resumesChronologicalOrder.asReversed()) {
            if (c.packageName in RESUME_DECOR_PACKAGES) continue
            return c
        }
        return resumesChronologicalOrder.last()
    }

    private fun mostRecentPackageByLastTimeUsed(
        usm: UsageStatsManager,
        endMs: Long,
        lookbackMs: Long,
    ): String? {
        val stats = queryUsageStatsMergedCached(usm, endMs, lookbackMs)
        if (stats.isEmpty()) return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun queryUsageStatsMergedCached(
        usm: UsageStatsManager,
        endMs: Long,
        lookbackMs: Long,
    ): List<UsageStats> {
        val cachedAt = mergedStatsCachedAtMs
        if (cachedAt > 0L && endMs - cachedAt <= MERGED_STATS_CACHE_MS && mergedStatsCache.isNotEmpty()) {
            return mergedStatsCache
        }
        val fresh = queryUsageStatsMerged(usm, endMs, lookbackMs)
        mergedStatsCachedAtMs = endMs
        mergedStatsCache = fresh
        return fresh
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
        return emptyList()
    }

    /**
     * Показывать оверлей только когда в фокусе целевая игра (или по эвристикам usage stats ниже).
     * Раньше при `lastResumedPackage == SquadRelay` всегда возвращали true — из‑за этого при открытом
     * приложении SquadRelay снова появлялись все кнопки оверлея даже в режиме «только в игре».
     *
     * [targetGamePackages] — один или несколько applicationId (например release и debug через запятую в настройках).
     */
    /**
     * Дорогой путь ([shouldShowOverlay]): кэшируется на [FULL_HEURISTIC_CACHE_MS], чтобы
     * [CombatOverlayService.tickGameGate] не гонял queryUsageStats каждые ~900 ms.
     */
    fun shouldShowOverlayCached(
        context: Context,
        targetGamePackages: Collection<String>,
        allowedActivitySubstrings: Collection<String> = emptyList(),
    ): Boolean {
        val targetsKey = targetGamePackages.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
        val allowedKey = allowedActivitySubstrings.map { it.trim() }.filter { it.isNotEmpty() }.sorted().joinToString(",")
        val cacheKey = "$targetsKey|$allowedKey"
        val now = System.currentTimeMillis()
        synchronized(fullHeuristicCacheLock) {
            if (cacheKey == fullHeuristicCacheKey &&
                now - fullHeuristicCacheAtMs < FULL_HEURISTIC_CACHE_MS
            ) {
                return fullHeuristicCacheValue
            }
        }
        val result = shouldShowOverlay(context, targetGamePackages, allowedActivitySubstrings)
        synchronized(fullHeuristicCacheLock) {
            fullHeuristicCacheKey = cacheKey
            fullHeuristicCacheAtMs = now
            fullHeuristicCacheValue = result
        }
        return result
    }

    fun shouldShowOverlay(
        context: Context,
        targetGamePackages: Collection<String>,
        allowedActivitySubstrings: Collection<String> = emptyList(),
    ): Boolean {
        val targets = targetGamePackages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (targets.isEmpty()) return false
        if (!hasUsageStatsAccess(context)) return false
        val allowed = allowedActivitySubstrings.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val hasActivityFilter = allowed.isNotEmpty()
        val alliance = context.packageName
        val targetSet = targets.toSet()
        val hintedComp = lastResumedComponent(context)
        val hinted = hintedComp?.packageName
        // Never show the overlay while the SquadRelay app itself is in foreground.
        // Otherwise, after minimizing the game and opening SquadRelay, heuristics (TTF/lastUsed)
        // may still think the game "wins" and bring the overlay back on top of the app.
        if (hinted == alliance) return false
        if (hinted != null && isConflictingForegroundHint(hinted, targetSet, alliance)) {
            return false
        }
        // If the OS tells us the user most recently resumed some other app (launcher/settings/etc),
        // do NOT immediately return false: on some ROMs (MIUI/HyperOS), resume events may temporarily
        // point to launcher/system while the game is still on screen. Instead, fall through to the
        // rest of heuristics (package-only events, resume timeline, TTF growth, lastUsed tie).
        if (hinted != null && targetSet.contains(hinted)) {
            if (!hasActivityFilter) return true
            val cls = hintedComp.className?.trim().orEmpty()
            // Some ROMs / games do not report activity className reliably via UsageEvents.
            // When we know the game package is foreground but className is missing, fall back to package-only gating
            // instead of hiding the overlay everywhere.
            if (cls.isBlank()) return true
            return allowed.any { token -> cls.contains(token, ignoreCase = true) }
        }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return false
        val end = System.currentTimeMillis()
        // With an activity filter, heuristics below can't reliably tell in-game "pages" (only the package),
        // so we don't use them for restricting to specific screens. The filter is applied only when we have a className
        // from resume events; otherwise we fall back to package-only gating above.
        if (hasActivityFilter) {
            return false
        }
        // API 28+: события только по пакету игры — на MIUI глобальный queryEvents часто пуст/обрезан.
        // Не применять, если usage уже показывает другое приложение (лаунчер и т.д.) — иначе оверлей
        // остаётся после сворачивания игры.
        if (!isConflictingForegroundHint(hinted, targetSet, alliance)) {
            for (t in targets) {
                if (runCatching { targetForegroundFromPackageOnlyEvents(usm, t, end) }.getOrDefault(false)) {
                    return true
                }
            }
        }
        // На части прошивок «последний» RESUME — не игра, а лаунчер/сервис; lastTimeUsed при долгой сессии
        // в Unity не обновляется. Сравниваем макс. время RESUME цели с прочими (кроме декора и SquadRelay).
        if (!isConflictingForegroundHint(hinted, targetSet, alliance) &&
            runCatching {
                targetWinsResumeTimeline(usm, end, RESUME_VS_RESUME_WINDOW_MS, targetSet, alliance)
            }.getOrDefault(false)
        ) {
            return true
        }
        val stats = runCatching {
            queryUsageStatsMergedCached(usm, end, USAGE_STATS_LOOKBACK_MS)
        }.getOrDefault(emptyList())
        // MIUI/HyperOS: события и lastTimeUsed часто «молчат» в Unity; TTF между тиками гейта растёт только в фокусе.
        if (!isConflictingForegroundHint(hinted, targetSet, alliance) &&
            targetForegroundByGrowingTotalTime(stats, targetSet)
        ) {
            return true
        }
        if (stats.isEmpty()) return false
        val effectiveMap = stats.associate { it.packageName to effectiveUsageTimestamp(it) }
        // Без этого: после сворачивания игры lastTimeUsed игры долго остаётся «рядом с лидером» (до ~45s slop),
        // и оверлей не убирался, хотя lastResumed уже лаунчер/другое приложение.
        if (!allowLastUsedNearLeaderFallback(hinted, targetSet, alliance)) return false
        return targets.any { target ->
            isTargetLastUsedNearLeader(effectiveMap, target, TARGET_USAGE_TIE_SLOP_MS)
        }
    }

    /**
     * Слабый fallback по lastTimeUsed имеет смысл только пока нет явного «другого приложения» на переднем плане.
     * (Иначе после minimize игры usage ещё долго «лидерит» пакет игры.)
     */
    /** Другое приложение явно на переднем плане (не игра, не SquadRelay, не системный декор). */
    internal fun isConflictingForegroundHint(
        lastForegroundPackageHint: String?,
        targetPackages: Set<String>,
        alliancePackage: String,
    ): Boolean {
        val hinted = lastForegroundPackageHint ?: return false
        if (hinted in targetPackages) return false
        if (hinted == alliancePackage) return false
        if (hinted in RESUME_DECOR_PACKAGES) return false
        return true
    }

    internal fun allowLastUsedNearLeaderFallback(
        lastForegroundPackageHint: String?,
        targetPackages: Set<String>,
        alliancePackage: String,
    ): Boolean {
        val hinted = lastForegroundPackageHint ?: return true
        if (hinted in targetPackages) return true
        if (hinted == alliancePackage) return true
        if (hinted in RESUME_DECOR_PACKAGES) return true
        return false
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

    private const val FOREGROUND_CACHE_MS = 2_500L
    private const val FULL_HEURISTIC_CACHE_MS = 8_000L

    /** Кэш merged queryUsageStats внутри одного тика / короткой серии тиков гейта. */
    private const val MERGED_STATS_CACHE_MS = 2_500L

    private const val USAGE_ACCESS_CACHE_MS = 15_000L

    private const val USAGE_ACCESS_PREFS = "game_foreground_gate"
    private const val KEY_USAGE_ACCESS_GRANTED = "usage_access_granted_v1"

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
    private const val RESUME_VS_RESUME_WINDOW_MS = 5 * 60 * 1000L

    /** Окно [UsageStatsManager.queryEventsForPackage] — только события выбранного пакета. */
    private const val PACKAGE_ONLY_EVENTS_WINDOW_MS = 8 * 60 * 1000L

    /**
     * Состояние «на экране» по хронологии событий одного пакета ([UsageStatsManager.queryEventsForPackage]).
     */
    /**
     * Последнее «вход в fg» должно быть позже последнего «выхода», иначе Unity может слать только часть типов.
     */
    internal fun foregroundStateAfterPackageEventTypes(chronologicalTypes: List<Int>): Boolean {
        if (chronologicalTypes.isEmpty()) return false
        var lastEnterIndex = -1
        var lastLeaveIndex = -1
        for (i in chronologicalTypes.indices) {
            val type = chronologicalTypes[i]
            when {
                isForegroundEnterEvent(type) -> lastEnterIndex = i
                isForegroundLeaveEvent(type) -> lastLeaveIndex = i
            }
        }
        if (lastEnterIndex < 0) return false
        return lastEnterIndex > lastLeaveIndex
    }

    @Suppress("DEPRECATION")
    private fun isForegroundEnterEvent(type: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return type == UsageEvents.Event.ACTIVITY_RESUMED ||
                type == UsageEvents.Event.MOVE_TO_FOREGROUND
        }
        return type == UsageEvents.Event.MOVE_TO_FOREGROUND
    }

    @Suppress("DEPRECATION")
    private fun isForegroundLeaveEvent(type: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return type == UsageEvents.Event.ACTIVITY_PAUSED ||
                type == UsageEvents.Event.MOVE_TO_BACKGROUND
        }
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
