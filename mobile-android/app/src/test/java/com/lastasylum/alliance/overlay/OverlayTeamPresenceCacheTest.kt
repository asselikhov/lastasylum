package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamOverlayPresenceDto
import com.lastasylum.alliance.ui.util.OVERLAY_INGAME_PRESENCE_STALE_MS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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
        assertFalse(OverlayTeamPresenceCache.isCacheValid("team-1", nowMs = now + 66_000L))
    }

    @Test
    fun peek_returnsPresenceWhenCacheValid() {
        val now = System.currentTimeMillis()
        val presence = TeamOverlayPresenceDto(ingame = emptyList())
        OverlayTeamPresenceCache.seedCacheForTest("team-1", presence, atMs = now)
        assertEquals(presence, OverlayTeamPresenceCache.peek("team-1"))
        assertEquals(null, OverlayTeamPresenceCache.peek("team-2"))
    }

    @Test
    fun seedFromDisk_restoresPresenceForPeek() {
        val presence = TeamOverlayPresenceDto(
            ingame = listOf(
                PlayerTeamMemberDto(
                    userId = "u1",
                    username = "alice",
                    isLeader = false,
                    avatarRelativeUrl = null,
                    presenceStatus = "ingame",
                    lastPresenceAt = Instant.now().toString(),
                ),
            ),
        )
        OverlayTeamPresenceCache.seedFromDisk("team-1", presence)
        assertEquals("alice", OverlayTeamPresenceCache.peek("team-1")?.ingame?.first()?.username)
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

    @Test
    fun isCacheValid_missWhenTeamChanges() {
        val now = System.currentTimeMillis()
        OverlayTeamPresenceCache.seedCacheForTest(
            teamId = "team-1",
            presence = TeamOverlayPresenceDto(ingame = emptyList()),
            atMs = now,
        )
        assertFalse(OverlayTeamPresenceCache.isCacheValid("team-2", nowMs = now + 1_000L))
    }

    @Test
    fun countFreshIngameMembers_excludesStalePing() {
        val freshAt = Instant.now().minusSeconds(30).toString()
        val staleAt = Instant.now()
            .minusMillis(OVERLAY_INGAME_PRESENCE_STALE_MS + 1_000)
            .toString()
        val members = listOf(
            PlayerTeamMemberDto(
                userId = "u1",
                username = "alice",
                isLeader = false,
                avatarRelativeUrl = null,
                presenceStatus = "ingame",
                lastPresenceAt = freshAt,
            ),
            PlayerTeamMemberDto(
                userId = "u2",
                username = "bob",
                isLeader = false,
                avatarRelativeUrl = null,
                presenceStatus = "ingame",
                lastPresenceAt = staleAt,
            ),
        )
        assertEquals(1, countFreshIngameMembers(members))
    }

    @Test
    fun applySocketEvent_addsFreshIngameMember() {
        val freshAt = Instant.now().minusSeconds(15).toString()
        OverlayTeamPresenceCache.applySocketEvent(
            teamId = "team-1",
            event = com.lastasylum.alliance.data.teams.TeamPresenceSocketEvent(
                userId = "u2",
                presenceStatus = "ingame",
                lastPresenceAt = freshAt,
                username = "bob",
            ),
        )
        val ingame = OverlayTeamPresenceCache.peek("team-1")?.ingame.orEmpty()
        assertEquals(listOf("u2"), ingame.map { it.userId })
        assertEquals("bob", ingame.first().username)
    }

    @Test
    fun storeMergedLists_reconcilesStaleRows() {
        val freshAt = Instant.now().minusSeconds(20).toString()
        val staleAt = Instant.now()
            .minusMillis(OVERLAY_INGAME_PRESENCE_STALE_MS + 1_000)
            .toString()
        OverlayTeamPresenceCache.storeMergedLists(
            teamId = "team-1",
            ingame = listOf(
                PlayerTeamMemberDto(
                    userId = "u1",
                    username = "alice",
                    isLeader = false,
                    avatarRelativeUrl = null,
                    presenceStatus = "ingame",
                    lastPresenceAt = freshAt,
                ),
                PlayerTeamMemberDto(
                    userId = "u2",
                    username = "bob",
                    isLeader = false,
                    avatarRelativeUrl = null,
                    presenceStatus = "ingame",
                    lastPresenceAt = staleAt,
                ),
            ),
            recentlyActive = emptyList(),
        )
        assertEquals(1, OverlayTeamPresenceCache.peek("team-1")?.ingame?.size)
    }
}
