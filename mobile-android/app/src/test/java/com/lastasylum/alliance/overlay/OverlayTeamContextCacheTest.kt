package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.users.MyProfileDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTeamContextCacheTest {

    @After
    fun tearDown() {
        OverlayTeamContextCache.invalidate()
    }

    @Test
    fun peekValid_returnsNullWhenEmpty() {
        OverlayTeamContextCache.invalidate()
        assertNull(OverlayTeamContextCache.peekValid())
    }

    @Test
    fun peekForPanel_returnsStaleAfterFreshTtlExpires() {
        val profile = sampleProfile()
        val team = sampleTeam()
        OverlayTeamContextCache.seedFromDisk(profile, team)
        val seededAt = System.currentTimeMillis()
        assertNotNull(OverlayTeamContextCache.peekValid(nowMs = seededAt + 60_000L))
        assertNull(OverlayTeamContextCache.peekValid(nowMs = seededAt + 6 * 60_000L))
        assertNotNull(OverlayTeamContextCache.peekForPanel())
        assertSame(
            OverlayTeamContextCache.peekForPanel(),
            OverlayTeamContextCache.peekStale(),
        )
    }

    @Test
    fun seedFromDisk_populatesContextAndTeamRoster() {
        val profile = sampleProfile()
        val team = sampleTeam()
        OverlayTeamContextCache.seedFromDisk(profile, team)
        val ctx = OverlayTeamContextCache.peekForPanel()
        assertNotNull(ctx)
        assertEquals(teamId, ctx?.teamId)
        assertEquals(userId, ctx?.currentUserId)
        assertEquals("TAG", ctx?.teamTag)
        assertEquals(team, OverlayTeamContextCache.peekCachedTeam())
    }

    @Test
    fun isFresh_reflectsSeedTimestamp() {
        OverlayTeamContextCache.seedFromDisk(sampleProfile(), sampleTeam())
        assertTrue(OverlayTeamContextCache.isFresh())
        assertFalse(
            OverlayTeamContextCache.isFresh(
                nowMs = System.currentTimeMillis() + 6 * 60_000L,
            ),
        )
    }

    private val userId = "user_ctx_1"
    private val teamId = "team_ctx_1"

    private fun sampleProfile(): MyProfileDto = MyProfileDto(
        id = userId,
        username = "tester",
        email = "t@test.com",
        role = "member",
        allianceName = "Alliance",
        membershipStatus = "active",
        playerTeamId = teamId,
    )

    private fun sampleTeam(): TeamDetailDto = TeamDetailDto(
        id = teamId,
        tag = "TAG",
        displayName = "Team",
        leaderUserId = userId,
        members = emptyList(),
    )
}
