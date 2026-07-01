package com.lastasylum.alliance.game

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Команда «переместить всех» по маршруту — payload в сообщении канала «Рейд». */
object RouteRelocateAllCommand {
    /** Невидимый маркер (Private Use Area), как у рейд-шаринга. */
    const val MARKER = "\uE003"

    data class Payload(
        val version: Int,
        val routeId: String,
        val routeName: String,
        val batchId: String,
        val points: List<Point>,
    ) {
        data class Point(
            val memberName: String,
            val memberId: String?,
            val x: Int,
            val y: Int,
            val sid: Int,
        )

        fun toPlannerPoints(): List<RoutePlannerPoint> =
            points.map { p ->
                RoutePlannerPoint(
                    id = "$batchId:${p.memberName}:${p.x}:${p.y}",
                    x = p.x,
                    y = p.y,
                    sid = p.sid,
                    memberId = p.memberId,
                    memberName = p.memberName,
                    createdAtMs = 0L,
                )
            }
    }

    data class EncodeResult(
        val batchId: String,
        val messageText: String,
        val pointCount: Int,
    )

    fun encode(route: RoutePlannerRoute): EncodeResult {
        val batchId = UUID.randomUUID().toString()
        val pointsArr = JSONArray()
        route.points.forEach { point ->
            pointsArr.put(
                JSONObject().apply {
                    put("memberName", point.memberName)
                    if (!point.memberId.isNullOrBlank()) put("memberId", point.memberId)
                    put("x", point.x)
                    put("y", point.y)
                    put("sid", point.sid)
                },
            )
        }
        val json = JSONObject().apply {
            put("v", 1)
            put("routeId", route.id)
            put("routeName", route.name)
            put("batchId", batchId)
            put("points", pointsArr)
        }
        val messageText = buildString {
            append("🗺️ Маршрут «")
            append(route.name)
            append("»: перемещение всех участников (")
            append(route.points.size)
            append(")\n")
            append(MARKER)
            append(json)
        }
        return EncodeResult(
            batchId = batchId,
            messageText = messageText,
            pointCount = route.points.size,
        )
    }

    fun parse(text: String?): Payload? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val markerIdx = raw.indexOf(MARKER)
        if (markerIdx < 0) return null
        val jsonPart = raw.substring(markerIdx + MARKER.length).trim()
        if (jsonPart.isEmpty()) return null
        return runCatching {
            val obj = JSONObject(jsonPart)
            if (obj.optInt("v", 0) != 1) return null
            val routeId = obj.optString("routeId").trim()
            val routeName = obj.optString("routeName").trim()
            val batchId = obj.optString("batchId").trim()
            if (routeId.isEmpty() || batchId.isEmpty()) return null
            val arr = obj.optJSONArray("points") ?: return null
            val points = (0 until arr.length()).mapNotNull { i ->
                val p = arr.optJSONObject(i) ?: return@mapNotNull null
                val memberName = p.optString("memberName").trim()
                val x = p.optInt("x", 0)
                val y = p.optInt("y", 0)
                val sid = p.optInt("sid", 0)
                if (memberName.isEmpty() || x <= 0 || y <= 0 || sid <= 0) return@mapNotNull null
                Payload.Point(
                    memberName = memberName,
                    memberId = p.optString("memberId").trim().takeIf { it.isNotEmpty() },
                    x = x,
                    y = y,
                    sid = sid,
                )
            }
            if (points.isEmpty()) return null
            Payload(
                version = 1,
                routeId = routeId,
                routeName = routeName.ifEmpty { routeId },
                batchId = batchId,
                points = points,
            )
        }.getOrNull()
    }
}
