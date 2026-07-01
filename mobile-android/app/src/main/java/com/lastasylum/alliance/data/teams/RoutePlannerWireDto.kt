package com.lastasylum.alliance.data.teams

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RoutePlannerPointWireDto(
    val id: String,
    val x: Int,
    val y: Int,
    val sid: Int,
    val memberId: String? = null,
    val memberName: String,
    val createdAtMs: Long,
)

@JsonClass(generateAdapter = true)
data class RoutePlannerRouteWireDto(
    val id: String,
    val name: String,
    val type: String,
    val createdAtMs: Long,
    val points: List<RoutePlannerPointWireDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TeamRoutePlannerSnapshotDto(
    val routes: List<RoutePlannerRouteWireDto> = emptyList(),
    val updatedAtMs: Long = 0L,
    val updatedByUserId: String = "",
)

@JsonClass(generateAdapter = true)
data class PutTeamRoutePlannerBody(
    val routes: List<RoutePlannerRouteWireDto>,
    val clientUpdatedAtMs: Long? = null,
)
