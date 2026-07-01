package com.lastasylum.alliance.game

import android.content.Context
import com.lastasylum.alliance.di.AppContainer
import org.json.JSONArray
import java.util.UUID

/** Локальное хранилище маршрутов планировщика (ключ — playerTeamId). */
object RoutePlannerStore {
    private const val PREFS = "overlay_route_planner"
    private const val MAX_ROUTES = 200
    private const val MAX_POINTS_PER_ROUTE = 500

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun storageKey(teamId: String): String = "routes_${teamId.trim()}"

    private fun revisionKey(teamId: String): String = "routes_rev_${teamId.trim()}"

    fun localRevisionMs(context: Context, teamId: String): Long {
        val key = revisionKey(teamId)
        if (key == "routes_rev_") return 0L
        return prefs(context).getLong(key, 0L)
    }

    fun setLocalRevision(context: Context, teamId: String, updatedAtMs: Long) {
        val key = revisionKey(teamId)
        if (key == "routes_rev_") return
        prefs(context).edit().putLong(key, updatedAtMs.coerceAtLeast(0L)).apply()
    }

    fun replaceAll(
        context: Context,
        teamId: String,
        routes: List<RoutePlannerRoute>,
        updatedAtMs: Long,
        publishMarkers: Boolean = true,
    ) {
        val key = storageKey(teamId)
        if (key == "routes_") return
        persist(context, teamId, key, routes.take(MAX_ROUTES), publishMarkers)
        setLocalRevision(context, teamId, updatedAtMs)
    }

    fun list(context: Context, teamId: String): List<RoutePlannerRoute> {
        val key = storageKey(teamId)
        if (key == "routes_") return emptyList()
        val raw = prefs(context).getString(key, null) ?: return emptyList()
        return RoutePlannerRoute.parseArray(raw)
            .sortedByDescending { it.createdAtMs }
    }

    fun get(context: Context, teamId: String, routeId: String): RoutePlannerRoute? =
        list(context, teamId).firstOrNull { it.id == routeId }

    fun add(context: Context, teamId: String, route: RoutePlannerRoute): Boolean {
        val key = storageKey(teamId)
        if (key == "routes_") return false
        val current = list(context, teamId)
        val updated = listOf(route) + current.filterNot { it.id == route.id }
        persist(context, teamId, key, updated.take(MAX_ROUTES))
        return true
    }

    fun updateRoute(
        context: Context,
        teamId: String,
        routeId: String,
        name: String,
        type: RoutePlannerType,
    ): RoutePlannerRoute? {
        val key = storageKey(teamId)
        if (key == "routes_") return null
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val current = list(context, teamId)
        val route = current.firstOrNull { it.id == routeId } ?: return null
        val updatedRoute = route.copy(name = trimmed, type = type)
        val updated = current.map { if (it.id == routeId) updatedRoute else it }
        persist(context, teamId, key, updated)
        return updatedRoute
    }

    fun deleteRoute(context: Context, teamId: String, routeId: String): Boolean {
        val key = storageKey(teamId)
        if (key == "routes_") return false
        val current = list(context, teamId)
        if (current.none { it.id == routeId }) return false
        val updated = current.filterNot { it.id == routeId }
        persist(context, teamId, key, updated)
        return true
    }

    fun duplicateRoute(context: Context, teamId: String, routeId: String): RoutePlannerRoute? {
        val source = get(context, teamId, routeId) ?: return null
        val copy = RoutePlannerRoute(
            id = UUID.randomUUID().toString(),
            name = source.name + " (копия)",
            type = source.type,
            createdAtMs = System.currentTimeMillis(),
            points = source.orderedPoints().map { point ->
                point.copy(
                    id = UUID.randomUUID().toString(),
                    createdAtMs = System.currentTimeMillis(),
                )
            },
        )
        add(context, teamId, copy)
        RouteMapMarkersSync.publishDebounced(context, teamId, list(context, teamId))
        return copy
    }

    fun addPoint(
        context: Context,
        teamId: String,
        routeId: String,
        point: RoutePlannerPoint,
    ): RoutePlannerRoute? = upsertPoint(context, teamId, routeId, point, prepend = true)

    fun updatePoint(
        context: Context,
        teamId: String,
        routeId: String,
        point: RoutePlannerPoint,
    ): RoutePlannerRoute? = upsertPoint(context, teamId, routeId, point, prepend = false)

    fun deletePoint(
        context: Context,
        teamId: String,
        routeId: String,
        pointId: String,
    ): RoutePlannerRoute? {
        val key = storageKey(teamId)
        if (key == "routes_") return null
        val current = list(context, teamId)
        val route = current.firstOrNull { it.id == routeId } ?: return null
        val updatedRoute = route.copy(
            points = route.points.filterNot { it.id == pointId.trim() },
        )
        val updated = current.map { if (it.id == routeId) updatedRoute else it }
        persist(context, teamId, key, updated)
        return updatedRoute
    }

    fun movePoint(
        context: Context,
        teamId: String,
        routeId: String,
        pointId: String,
        delta: Int,
    ): RoutePlannerRoute? {
        if (delta == 0) return get(context, teamId, routeId)
        val key = storageKey(teamId)
        if (key == "routes_") return null
        val current = list(context, teamId)
        val route = current.firstOrNull { it.id == routeId } ?: return null
        val ordered = route.orderedPoints().toMutableList()
        val index = ordered.indexOfFirst { it.id == pointId.trim() }
        if (index < 0) return null
        val target = index + delta
        if (target !in ordered.indices) return route
        val a = ordered[index]
        val b = ordered[target]
        val swappedA = a.copy(createdAtMs = b.createdAtMs)
        val swappedB = b.copy(createdAtMs = a.createdAtMs)
        ordered[index] = swappedB
        ordered[target] = swappedA
        val updatedRoute = route.copy(points = ordered)
        val updated = current.map { if (it.id == routeId) updatedRoute else it }
        persist(context, teamId, key, updated)
        return updatedRoute
    }

    fun refreshMapMarkers(context: Context, teamId: String) {
        RouteMapMarkersSync.publish(context, teamId, list(context, teamId))
    }

    private fun upsertPoint(
        context: Context,
        teamId: String,
        routeId: String,
        point: RoutePlannerPoint,
        prepend: Boolean,
    ): RoutePlannerRoute? {
        val key = storageKey(teamId)
        if (key == "routes_") return null
        val current = list(context, teamId)
        val route = current.firstOrNull { it.id == routeId } ?: return null
        val without = route.points.filterNot { it.id == point.id }
        val merged = if (prepend) {
            listOf(point) + without
        } else {
            route.points.map { existing -> if (existing.id == point.id) point else existing }
        }
        val updatedRoute = route.copy(points = merged.take(MAX_POINTS_PER_ROUTE))
        val updated = current.map { if (it.id == routeId) updatedRoute else it }
        persist(context, teamId, key, updated)
        return updatedRoute
    }

    private fun persist(
        context: Context,
        teamId: String,
        key: String,
        routes: List<RoutePlannerRoute>,
        publishMarkers: Boolean = true,
    ) {
        save(context, key, routes)
        if (publishMarkers) {
            RouteMapMarkersSync.publishDebounced(context, teamId, routes)
        }
    }

    private fun save(context: Context, key: String, routes: List<RoutePlannerRoute>) {
        val arr = JSONArray()
        routes.forEach { arr.put(it.toJson()) }
        prefs(context).edit().putString(key, arr.toString()).apply()
    }
}
