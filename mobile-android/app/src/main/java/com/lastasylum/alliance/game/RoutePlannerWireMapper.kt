package com.lastasylum.alliance.game

import com.lastasylum.alliance.data.teams.RoutePlannerPointWireDto
import com.lastasylum.alliance.data.teams.RoutePlannerRouteWireDto
import com.lastasylum.alliance.data.teams.TeamRoutePlannerSnapshotDto

internal fun RoutePlannerRoute.toWire(): RoutePlannerRouteWireDto =
    RoutePlannerRouteWireDto(
        id = id,
        name = name,
        type = type.storageKey,
        createdAtMs = createdAtMs,
        points = points.map { it.toWire() },
    )

internal fun RoutePlannerPoint.toWire(): RoutePlannerPointWireDto =
    RoutePlannerPointWireDto(
        id = id,
        x = x,
        y = y,
        sid = sid,
        memberId = memberId,
        memberName = memberName,
        createdAtMs = createdAtMs,
    )

internal fun RoutePlannerRouteWireDto.toDomain(): RoutePlannerRoute? {
    val routeType = RoutePlannerType.fromKey(type) ?: return null
    return RoutePlannerRoute(
        id = id.trim(),
        name = name.trim(),
        type = routeType,
        createdAtMs = createdAtMs,
        points = points.mapNotNull { it.toDomain() },
    )
}

internal fun RoutePlannerPointWireDto.toDomain(): RoutePlannerPoint? {
    val nick = memberName.trim()
    if (id.isBlank() || nick.isEmpty() || x <= 0 || y <= 0 || sid <= 0) return null
    return RoutePlannerPoint(
        id = id.trim(),
        x = x,
        y = y,
        sid = sid,
        memberId = memberId?.trim()?.takeIf { it.isNotEmpty() },
        memberName = nick,
        createdAtMs = createdAtMs,
    )
}

internal fun TeamRoutePlannerSnapshotDto.toDomainRoutes(): List<RoutePlannerRoute> =
    routes.mapNotNull { it.toDomain() }

internal fun List<RoutePlannerRoute>.toWire(): List<RoutePlannerRouteWireDto> =
    map { it.toWire() }
