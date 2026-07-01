package com.lastasylum.alliance.game

import android.content.Context
import com.lastasylum.alliance.di.AppContainer
import org.json.JSONArray

/**
 * Очередь прямых перемещений для точек маршрута, привязанных к текущему игроку.
 * Используется при команде «Переместить всех» (локально и по сообщению в «Рейд»).
 */
object RouteRelocateAllExecutor {
    private val queue = ArrayDeque<RoutePlannerPoint>()
    private var active = false
    private var totalCount = 0
    private var completedCount = 0
    private val recentBatchIds = LinkedHashSet<String>()
    private const val MAX_RECENT_BATCHES = 64

    fun isActive(): Boolean = active

    fun start(context: Context, batchId: String, points: List<RoutePlannerPoint>): Boolean {
        val bid = batchId.trim()
        if (bid.isNotEmpty() && bid in recentBatchIds) return false
        if (bid.isNotEmpty()) rememberBatch(bid)
        val mine = filterPointsForCurrentPlayer(context, points)
        if (mine.isEmpty()) return false
        queue.clear()
        queue.addAll(mine)
        totalCount = mine.size
        completedCount = 0
        active = true
        dispatchNext(context)
        return true
    }

    fun onRelocateResult(context: Context, ok: Boolean): Boolean {
        if (!active) return false
        if (!ok) {
            finish()
            return true
        }
        completedCount++
        dispatchNext(context)
        return true
    }

    fun progressLabel(context: Context, point: RoutePlannerPoint): String {
        val current = (completedCount + 1).coerceAtMost(totalCount)
        return context.getString(
            com.lastasylum.alliance.R.string.overlay_route_relocate_all_progress,
            current,
            totalCount,
            point.memberName,
        )
    }

    private fun dispatchNext(context: Context) {
        val next = queue.removeFirstOrNull()
        if (next == null) {
            finish()
            return
        }
        GameCityTeleportBridge.sendDirect(context, next.x, next.y, next.sid)
    }

    private fun finish() {
        queue.clear()
        active = false
        totalCount = 0
        completedCount = 0
    }

    private fun rememberBatch(batchId: String) {
        recentBatchIds.add(batchId)
        while (recentBatchIds.size > MAX_RECENT_BATCHES) {
            val oldest = recentBatchIds.iterator().next()
            recentBatchIds.remove(oldest)
        }
    }

    /** Точки, где memberName / memberId совпадает с активным игровым ником. */
    fun filterPointsForCurrentPlayer(
        context: Context,
        points: List<RoutePlannerPoint>,
    ): List<RoutePlannerPoint> {
        if (points.isEmpty()) return emptyList()
        val nicks = resolveCurrentGameNicknames(context)
        val rosterIds = resolveCurrentRosterMemberIds(context, nicks)
        return points.filter { point ->
            nicks.any { it.equals(point.memberName, ignoreCase = true) } ||
                (point.memberId != null && point.memberId in rosterIds)
        }.sortedBy { it.createdAtMs }
    }

    private fun resolveCurrentGameNicknames(context: Context): Set<String> {
        val repo = AppContainer.from(context).usersRepository
        val profile = repo.peekMyProfile() ?: repo.peekMyProfileDisk() ?: return emptySet()
        val out = LinkedHashSet<String>()
        profile.activeGameNickname?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        profile.gameIdentities.forEach { identity ->
            identity.gameNickname.trim().takeIf { it.isNotEmpty() }?.let { out.add(it) }
        }
        return out
    }

    private fun resolveCurrentRosterMemberIds(
        context: Context,
        nicks: Set<String>,
    ): Set<String> {
        if (nicks.isEmpty()) return emptySet()
        val raw = AppContainer.from(context).userSettingsPreferences.getAllianceRosterJson()
        val roster = parseRosterIdName(raw)
        return roster
            .filter { (_, name) -> nicks.any { it.equals(name, ignoreCase = true) } }
            .map { (id, _) -> id }
            .toSet()
    }

    private fun parseRosterIdName(json: String?): List<Pair<String, String>> {
        val raw = json?.trim().orEmpty()
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val out = ArrayList<Pair<String, String>>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val name = o.optString("name").trim()
                if (id.isNotEmpty() && name.isNotEmpty()) out.add(id to name)
            }
            out
        }.getOrDefault(emptyList())
    }
}
