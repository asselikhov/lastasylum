package com.lastasylum.alliance.game

import com.lastasylum.alliance.overlay.AllianceMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerAccessTest {
    @Test
    fun isSquadOfficer_acceptsR4R5() {
        assertTrue(RoutePlannerAccess.isSquadOfficer("R4"))
        assertTrue(RoutePlannerAccess.isSquadOfficer("r5"))
    }

    @Test
    fun isSquadOfficer_rejectsLowerRanks() {
        assertFalse(RoutePlannerAccess.isSquadOfficer("R3"))
        assertFalse(RoutePlannerAccess.isSquadOfficer(null))
    }
}

class RoutePlannerRouteTest {
    @Test
    fun create_rejectsBlankName() {
        assertFalse(runCatching { RoutePlannerRoute.create("  ", RoutePlannerType.PVE) }.isSuccess)
    }

    @Test
    fun create_trimsName() {
        val route = RoutePlannerRoute.create("  Alpha  ", RoutePlannerType.PVE)
        assertEquals("Alpha", route.name)
        assertEquals(RoutePlannerType.PVE, route.type)
    }

    @Test
    fun orderedPoints_sortsByCreatedAt() {
        val p1 = RoutePlannerPoint.create(100, 200, 109, "1", "Alpha").copy(createdAtMs = 100L)
        val p2 = RoutePlannerPoint.create(101, 201, 109, "2", "Beta").copy(createdAtMs = 200L)
        val route = RoutePlannerRoute.create("Route", RoutePlannerType.PVP).copy(points = listOf(p2, p1))
        val ordered = route.orderedPoints()
        assertEquals("Alpha", ordered[0].memberName)
        assertEquals("Beta", ordered[1].memberName)
    }

    @Test
    fun point_withMember_and_withCoords() {
        val point = RoutePlannerPoint.create(100, 200, 109, "1", "Alpha")
        val moved = point.withCoords(150, 250, 110)
        assertEquals(150, moved.x)
        assertEquals(110, moved.sid)
        val reassigned = point.withMember("2", "Beta")
        assertEquals("Beta", reassigned.memberName)
        assertEquals("2", reassigned.memberId)
    }
}

class RoutePlannerPointStatusTest {
    @Test
    fun resolve_onPlace_whenRosterMatchesPoint() {
        val point = RoutePlannerPoint.create(100, 200, 109, "1", "Alpha")
        val member = AllianceMember(
            id = "1",
            name = "Alpha",
            power = 0,
            level = 1,
            castle = 1,
            rank = 1,
            kills = 0,
            x = 100,
            y = 200,
            sid = 109,
            logoutMs = 0,
        )
        assertEquals(RoutePointStatus.OnPlace, RoutePlannerPointStatus.resolve(member, point))
    }

    @Test
    fun resolve_notMoved_whenCoordsDiffer() {
        val point = RoutePlannerPoint.create(100, 200, 109, "1", "Alpha")
        val member = AllianceMember(
            id = "1",
            name = "Alpha",
            power = 0,
            level = 1,
            castle = 1,
            rank = 1,
            kills = 0,
            x = 500,
            y = 600,
            sid = 109,
            logoutMs = 0,
        )
        assertEquals(RoutePointStatus.NotMoved, RoutePlannerPointStatus.resolve(member, point))
    }
}

class RouteRelocateAllCommandTest {
    @Test
    fun marker_usesPrivateUseAreaPrefix() {
        assertEquals('\uE003', RouteRelocateAllCommand.MARKER[0])
    }
}
