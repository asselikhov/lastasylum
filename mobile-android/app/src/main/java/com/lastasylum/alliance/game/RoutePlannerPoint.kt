package com.lastasylum.alliance.game

import org.json.JSONObject
import java.util.UUID

/** Точка маршрута на карте (координаты + привязанный игрок). */
data class RoutePlannerPoint(
    val id: String,
    val x: Int,
    val y: Int,
    val sid: Int,
    val memberId: String?,
    val memberName: String,
    val createdAtMs: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x)
        put("y", y)
        put("sid", sid)
        if (!memberId.isNullOrBlank()) put("memberId", memberId)
        put("memberName", memberName)
        put("createdAtMs", createdAtMs)
    }

    fun mapLabel(routeName: String): String {
        val nick = memberName.trim().ifEmpty { "?" }
        return "${routeName.trim()} · $nick"
    }

    fun withMember(memberId: String?, memberName: String): RoutePlannerPoint {
        val nick = memberName.trim()
        require(nick.isNotEmpty()) { "memberName required" }
        return copy(
            memberId = memberId?.trim()?.takeIf { it.isNotEmpty() },
            memberName = nick,
        )
    }

    fun withCoords(x: Int, y: Int, sid: Int): RoutePlannerPoint {
        require(x > 0 && y > 0 && sid > 0) { "invalid coords" }
        return copy(x = x, y = y, sid = sid)
    }

    companion object {
        fun create(
            x: Int,
            y: Int,
            sid: Int,
            memberId: String?,
            memberName: String,
        ): RoutePlannerPoint {
            require(x > 0 && y > 0 && sid > 0) { "invalid coords" }
            val nick = memberName.trim()
            require(nick.isNotEmpty()) { "memberName required" }
            return RoutePlannerPoint(
                id = UUID.randomUUID().toString(),
                x = x,
                y = y,
                sid = sid,
                memberId = memberId?.trim()?.takeIf { it.isNotEmpty() },
                memberName = nick,
                createdAtMs = System.currentTimeMillis(),
            )
        }

        fun fromJson(obj: JSONObject?): RoutePlannerPoint? {
            if (obj == null) return null
            val id = obj.optString("id").trim()
            val x = obj.optInt("x", 0)
            val y = obj.optInt("y", 0)
            val sid = obj.optInt("sid", 0)
            val memberName = obj.optString("memberName").trim()
            if (id.isEmpty() || x <= 0 || y <= 0 || sid <= 0 || memberName.isEmpty()) return null
            return RoutePlannerPoint(
                id = id,
                x = x,
                y = y,
                sid = sid,
                memberId = obj.optString("memberId").trim().takeIf { it.isNotEmpty() },
                memberName = memberName,
                createdAtMs = obj.optLong("createdAtMs", 0L),
            )
        }
    }
}
