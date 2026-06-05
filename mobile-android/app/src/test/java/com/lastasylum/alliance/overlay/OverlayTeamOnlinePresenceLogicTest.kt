package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamPresenceSocketEvent
import com.lastasylum.alliance.data.voice.VoicePeerState
import com.lastasylum.alliance.ui.util.OVERLAY_INGAME_PRESENCE_STALE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OverlayTeamOnlinePresenceLogicTest {
    private val selfId = "self-user"

    private fun freshIso(secondsAgo: Long = 30L): String =
        Instant.now().minusSeconds(secondsAgo).toString()

    private fun member(
        userId: String,
        username: String,
        role: String = "R1",
        presenceStatus: String? = "ingame",
        lastPresenceAt: String? = freshIso(),
        isLeader: Boolean = false,
    ) = PlayerTeamMemberDto(
        userId = userId,
        username = username,
        isLeader = isLeader,
        teamRole = role,
        telegramUsername = null,
        presenceStatus = presenceStatus,
        lastPresenceAt = lastPresenceAt,
    )

    @Test
    fun sort_self_first_then_rank_then_username() {
        val sections = buildPresenceSections(
            ingame = listOf(
                member("u3", "charlie", role = "R2"),
                member(selfId, "me", role = "R1"),
                member("u1", "alice", role = "R5"),
                member("u2", "bob", role = "R5"),
            ),
            recentlyActive = emptyList(),
            selfUserId = selfId,
        )
        val ids = sections.first().items.map { it.userId }
        assertEquals(listOf(selfId, "u1", "u2", "u3"), ids)
    }

    @Test
    fun dedupe_recent_when_already_ingame() {
        val shared = member("u1", "alice")
        val sections = buildPresenceSections(
            ingame = listOf(shared),
            recentlyActive = listOf(shared),
            selfUserId = null,
        )
        assertEquals(1, sections.first { it.kind == PresenceSectionKind.Ingame }.items.size)
        assertTrue(sections.first { it.kind == PresenceSectionKind.Recent }.items.isEmpty())
    }

    @Test
    fun socket_remove_stale_ingame() {
        val lists = OverlayOnlinePresenceLists(
            ingame = listOf(member("u1", "alice")),
            recentlyActive = emptyList(),
        )
        val staleAt = Instant.now()
            .minusMillis(OVERLAY_INGAME_PRESENCE_STALE_MS + 1_000)
            .toString()
        val merged = mergePresenceSocketEvent(
            lists = lists,
            event = TeamPresenceSocketEvent("u1", "ingame", staleAt),
            fallbackMember = member("u1", "alice"),
        )
        assertTrue(merged.ingame.isEmpty())
        assertTrue(merged.recentlyActive.isEmpty())
    }

    @Test
    fun socket_refresh_ingame_member() {
        val lists = OverlayOnlinePresenceLists(
            ingame = listOf(member("u1", "alice", lastPresenceAt = freshIso(45))),
            recentlyActive = emptyList(),
        )
        val freshAt = freshIso(5)
        val merged = mergePresenceSocketEvent(
            lists = lists,
            event = TeamPresenceSocketEvent("u1", "ingame", freshAt),
            fallbackMember = member("u1", "alice"),
        )
        assertEquals(1, merged.ingame.size)
        assertEquals(freshAt, merged.ingame.first().lastPresenceAt)
    }

    @Test
    fun search_filter_by_username_and_telegram() {
        val sections = buildPresenceSections(
            ingame = listOf(
                member("u1", "alice").copy(telegramUsername = "ali_tg"),
                member("u2", "bob"),
            ),
            recentlyActive = emptyList(),
            selfUserId = null,
        )
        val filtered = filterByQuery(sections, "ali")
        assertEquals(1, filtered.first().items.size)
        assertEquals("u1", filtered.first().items.first().userId)

        val byTg = filterByQuery(sections, "tg")
        assertEquals(1, byTg.first().items.size)
    }

    @Test
    fun presenceFreshness_boundaries() {
        val now = Instant.now()
        val freshAt = now.minusMillis(59_000).toString()
        val staleSoonAt = now.minusMillis(75_000).toString()
        val staleAt = now.minusMillis(91_000).toString()
        assertEquals(PresenceFreshness.Fresh, presenceFreshness(freshAt, now))
        assertEquals(PresenceFreshness.StaleSoon, presenceFreshness(staleSoonAt, now))
        assertEquals(PresenceFreshness.Stale, presenceFreshness(staleAt, now))
    }

    @Test
    fun reconcilePresenceLists_dropsStaleIngame() {
        val staleAt = Instant.now()
            .minusMillis(OVERLAY_INGAME_PRESENCE_STALE_MS + 1_000)
            .toString()
        val stale = member("u1", "alice", lastPresenceAt = staleAt)
        val reconciled = reconcilePresenceLists(
            ingame = listOf(stale),
            recentlyActive = emptyList(),
        )
        assertTrue(reconciled.ingame.isEmpty())
        assertTrue(reconciled.recentlyActive.isEmpty())
    }

    @Test
    fun reconcilePresenceLists_keepsFreshIngame() {
        val fresh = member("u1", "alice")
        val reconciled = reconcilePresenceLists(
            ingame = listOf(fresh),
            recentlyActive = emptyList(),
        )
        assertEquals(1, reconciled.ingame.size)
        assertTrue(reconciled.recentlyActive.isEmpty())
    }

    @Test
    fun ingameCount_extraction_for_hud() {
        val ingame = listOf(
            member("u1", "a"),
            member("u2", "b", lastPresenceAt = freshIso(120)),
        )
        assertEquals(1, rawIngameCount(ingame))
        val sections = buildPresenceSections(ingame, emptyList(), null)
        assertEquals(1, ingameCountFromSections(sections))
    }

    @Test
    fun shouldShowVoiceBadges_onlyForVoiceRoomMembersOrSelfWithSession() {
        val peers = mapOf("u1" to VoicePeerState("u1", "a", micOn = true, soundOn = false))
        assertTrue(
            shouldShowVoiceBadgesForMember(
                userId = "u1",
                selfUserId = "self",
                voicePeers = peers,
                hasLocalVoiceSession = true,
            ),
        )
        assertTrue(
            shouldShowVoiceBadgesForMember(
                userId = "self",
                selfUserId = "self",
                voicePeers = peers,
                hasLocalVoiceSession = true,
            ),
        )
        assertFalse(
            shouldShowVoiceBadgesForMember(
                userId = "u2",
                selfUserId = "self",
                voicePeers = peers,
                hasLocalVoiceSession = true,
            ),
        )
        assertFalse(
            shouldShowVoiceBadgesForMember(
                userId = "self",
                selfUserId = "self",
                voicePeers = peers,
                hasLocalVoiceSession = false,
            ),
        )
    }

    @Test
    fun filterByChip_all_ingame_recent_with_mic() {
        val sections = buildPresenceSections(
            ingame = listOf(member("u1", "a"), member("u2", "b")),
            recentlyActive = listOf(
                member(
                    "u3",
                    "c",
                    presenceStatus = "online",
                    lastPresenceAt = freshIso(45),
                ),
            ),
            selfUserId = null,
        )
        val voice = mapOf(
            "u1" to VoiceMemberFlags(micOn = true, soundOn = false),
            "u2" to VoiceMemberFlags(micOn = false, soundOn = true),
        )
        val ingameOnly = filterByChip(sections, OverlayOnlineFilterChip.IngameOnly, voice)
        assertEquals(2, ingameOnly.first { it.kind == PresenceSectionKind.Ingame }.items.size)
        assertTrue(ingameOnly.first { it.kind == PresenceSectionKind.Recent }.items.isEmpty())

        val recentOnly = filterByChip(sections, OverlayOnlineFilterChip.RecentOnly, voice)
        assertTrue(recentOnly.first { it.kind == PresenceSectionKind.Ingame }.items.isEmpty())
        assertEquals(1, recentOnly.first { it.kind == PresenceSectionKind.Recent }.items.size)

        val withMic = filterByChip(sections, OverlayOnlineFilterChip.WithMic, voice)
        assertEquals(1, withMic.first { it.kind == PresenceSectionKind.Ingame }.items.size)
        assertEquals("u1", withMic.first { it.kind == PresenceSectionKind.Ingame }.items.first().userId)
    }

    @Test
    fun combined_search_and_chip_filter() {
        val sections = buildPresenceSections(
            ingame = listOf(member("u1", "alice"), member("u2", "bob")),
            recentlyActive = emptyList(),
            selfUserId = null,
        )
        val voice = mapOf(
            "u1" to VoiceMemberFlags(micOn = true, soundOn = false),
            "u2" to VoiceMemberFlags(micOn = false, soundOn = false),
        )
        val result = applyOnlinePanelFilters(
            baseSections = sections,
            query = "ali",
            chip = OverlayOnlineFilterChip.WithMic,
            voiceFlagsByUserId = voice,
        )
        assertEquals(1, result.first().items.size)
        assertEquals("u1", result.first().items.first().userId)
    }

    @Test
    fun rawRecentCount_excludes_ingame_dupes() {
        val ingame = listOf(member("u1", "a"))
        val recent = listOf(
            member("u1", "a"),
            member(
                "u2",
                "b",
                presenceStatus = "online",
                lastPresenceAt = freshIso(45),
            ),
        )
        assertEquals(1, rawRecentCount(ingame, recent))
    }

    @Test
    fun socket_addsUnknownIngameMemberFromEvent() {
        val lists = OverlayOnlinePresenceLists(
            ingame = emptyList(),
            recentlyActive = emptyList(),
        )
        val freshAt = freshIso(5)
        val merged = mergePresenceSocketEvent(
            lists = lists,
            event = TeamPresenceSocketEvent("new-user", "ingame", freshAt),
            fallbackMember = null,
        )
        assertEquals(1, merged.ingame.size)
        assertEquals("new-user", merged.ingame.first().userId)
    }

    @Test
    fun isRecentlyActiveOverlay_rejectsIngameAndStale() {
        assertFalse(isRecentlyActiveOverlay("ingame", freshIso(30)))
        assertFalse(isRecentlyActiveOverlay("online", freshIso(120)))
        assertTrue(isRecentlyActiveOverlay("online", freshIso(30)))
    }

    @Test
    fun filterFreshIngameRecipients_excludesSelfAndStale() {
        val fresh = member("u1", "alice")
        val stale = member(
            "u2",
            "bob",
            lastPresenceAt = Instant.now()
                .minusMillis(OVERLAY_INGAME_PRESENCE_STALE_MS + 1_000)
                .toString(),
        )
        val result = filterFreshIngameRecipients(
            members = listOf(fresh, stale, member(selfId, "me")),
            selfUserId = selfId,
        )
        assertEquals(listOf("u1"), result.map { it.userId })
    }

    @Test
    fun overlayPresenceMemberListsEqual_sameMembers() {
        val a = listOf(member("u1", "alice"), member("u2", "bob"))
        val b = listOf(member("u1", "alice"), member("u2", "bob"))
        assertTrue(overlayPresenceMemberListsEqual(a, b))
    }

    @Test
    fun overlayPresenceMemberListsEqual_diffPresenceStatus() {
        val a = listOf(member("u1", "alice", presenceStatus = "ingame"))
        val b = listOf(member("u1", "alice", presenceStatus = "online"))
        assertFalse(overlayPresenceMemberListsEqual(a, b))
    }

    @Test
    fun overlayPresenceMemberListsEqual_diffSize() {
        val a = listOf(member("u1", "alice"))
        val b = emptyList<PlayerTeamMemberDto>()
        assertFalse(overlayPresenceMemberListsEqual(a, b))
    }
}
