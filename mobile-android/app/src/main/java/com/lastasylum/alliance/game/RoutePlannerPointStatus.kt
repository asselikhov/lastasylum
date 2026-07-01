package com.lastasylum.alliance.game

import android.content.Context
import com.lastasylum.alliance.overlay.AllianceMember
import com.lastasylum.alliance.overlay.AllianceRosterCache
import kotlin.math.abs
import kotlin.math.max

enum class RoutePointStatus {
    Unknown,
    OnPlace,
    NotMoved,
}

object RoutePlannerPointStatus {
    private const val COORD_TOLERANCE = 1

    fun resolve(member: AllianceMember, point: RoutePlannerPoint): RoutePointStatus {
        if (!member.hasCoords || member.sid != point.sid) {
            return RoutePointStatus.NotMoved
        }
        val dx = abs(member.x - point.x)
        val dy = abs(member.y - point.y)
        return if (max(dx, dy) <= COORD_TOLERANCE) {
            RoutePointStatus.OnPlace
        } else {
            RoutePointStatus.NotMoved
        }
    }

    fun resolve(context: Context, point: RoutePlannerPoint): RoutePointStatus {
        val member = AllianceRosterCache.peek().firstOrNull { m ->
            m.name.equals(point.memberName, ignoreCase = true) ||
                (!point.memberId.isNullOrBlank() && m.id == point.memberId)
        } ?: return RoutePointStatus.Unknown
        return resolve(member, point)
    }
}

object RoutePlannerRelocateStats {
    data class Summary(
        val totalPoints: Int,
        val onlineInOverlay: Int,
        val unassignedMembers: Int,
    )

    fun forRoute(
        route: RoutePlannerRoute,
        onlineMemberNames: Set<String>,
    ): Summary {
        val assigned = route.points.map { it.memberName.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val onlineAssigned = assigned.count { name -> onlineMemberNames.any { it.equals(name, ignoreCase = true) } }
        return Summary(
            totalPoints = route.points.size,
            onlineInOverlay = onlineAssigned,
            unassignedMembers = route.points.count { it.memberName.isBlank() },
        )
    }
}
