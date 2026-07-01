package com.lastasylum.alliance.game

/** Текст маршрута для канала «Рейд». */
object RoutePlannerExport {
    fun buildRaidMessage(route: RoutePlannerRoute): String {
        val typeLabel = when (route.type) {
            RoutePlannerType.PVP -> "PvP"
            RoutePlannerType.PVE -> "PvE"
        }
        val lines = buildString {
            append("🗺️ Маршрут «")
            append(route.name)
            append("» (")
            append(typeLabel)
            append(")\n")
            route.orderedPoints().forEachIndexed { index, point ->
                append(index + 1)
                append(". #")
                append(point.sid)
                append(" X:")
                append(point.x)
                append(" Y:")
                append(point.y)
                append(" · ")
                append(point.memberName)
                append('\n')
            }
        }
        return lines.trimEnd()
    }
}
