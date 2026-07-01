package com.lastasylum.alliance.game

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RoutePlannerType(val storageKey: String) {
    PVP("pvp"),
    PVE("pve"),
    ;

    companion object {
        fun fromKey(key: String?): RoutePlannerType? =
            entries.firstOrNull { it.storageKey.equals(key?.trim(), ignoreCase = true) }
    }
}

/** Маршрут планировщика (локально на устройстве, по teamId). */
data class RoutePlannerRoute(
    val id: String,
    val name: String,
    val type: RoutePlannerType,
    val createdAtMs: Long,
    val points: List<RoutePlannerPoint> = emptyList(),
) {
    /** Точки в порядке добавления (шаг 1 → N). */
    fun orderedPoints(): List<RoutePlannerPoint> =
        points.sortedBy { it.createdAtMs }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.storageKey)
        put("createdAtMs", createdAtMs)
        if (points.isNotEmpty()) {
            val arr = JSONArray()
            points.forEach { arr.put(it.toJson()) }
            put("points", arr)
        }
    }

    fun withPoint(point: RoutePlannerPoint): RoutePlannerRoute =
        copy(points = listOf(point) + points.filterNot { it.id == point.id })

    companion object {
        fun create(name: String, type: RoutePlannerType): RoutePlannerRoute {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty()) { "name required" }
            return RoutePlannerRoute(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                type = type,
                createdAtMs = System.currentTimeMillis(),
            )
        }

        fun fromJson(obj: JSONObject?): RoutePlannerRoute? {
            if (obj == null) return null
            val id = obj.optString("id").trim()
            val name = obj.optString("name").trim()
            val type = RoutePlannerType.fromKey(obj.optString("type")) ?: return null
            if (id.isEmpty() || name.isEmpty()) return null
            val pointsArr = obj.optJSONArray("points")
            val points = if (pointsArr != null) {
                (0 until pointsArr.length()).mapNotNull { i ->
                    RoutePlannerPoint.fromJson(pointsArr.optJSONObject(i))
                }
            } else {
                emptyList()
            }
            return RoutePlannerRoute(
                id = id,
                name = name,
                type = type,
                createdAtMs = obj.optLong("createdAtMs", 0L),
                points = points,
            )
        }

        fun parseArray(raw: String?): List<RoutePlannerRoute> {
            val json = raw?.trim().orEmpty()
            if (json.isEmpty()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    fromJson(arr.optJSONObject(i))
                }
            }.getOrDefault(emptyList())
        }
    }
}
