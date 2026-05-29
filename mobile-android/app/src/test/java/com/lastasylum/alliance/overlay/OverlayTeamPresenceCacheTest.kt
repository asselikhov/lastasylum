package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.TeamOverlayPresenceDto
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTeamPresenceCacheTest {
    @After
    fun tearDown() {
        OverlayTeamPresenceCache.invalidate()
    }

    @Test
    fun isCacheValid_hitWithinTtl() {
        val now = System.currentTimeMillis()
        OverlayTeamPresenceCache.seedCacheForTest(
            teamId = "team-1",
            presence = TeamOverlayPresenceDto(ingame = emptyList()),
            atMs = now,
        )
        assertTrue(OverlayTeamPresenceCache.isCacheValid("team-1", nowMs = now + 30_000L))
    }

    @Test
    fun isCacheValid_missAfterTtl() {
        val now = System.currentTimeMillis()
        OverlayTeamPresenceCache.seedCacheForTest(
            teamId = "team-1",
            presence = TeamOverlayPresenceDto(ingame = emptyList()),
            atMs = now,
        )
        assertFalse(OverlayTeamPresenceCache.isCacheValid("team-1", nowMs = now + 60_000L))
    }

    @Test
    fun invalidate_clearsCache() {
        OverlayTeamPresenceCache.seedCacheForTest(
            teamId = "team-1",
            presence = TeamOverlayPresenceDto(ingame = emptyList()),
        )
        OverlayTeamPresenceCache.invalidate()
        assertFalse(OverlayTeamPresenceCache.isCacheValid("team-1"))
    }
}
