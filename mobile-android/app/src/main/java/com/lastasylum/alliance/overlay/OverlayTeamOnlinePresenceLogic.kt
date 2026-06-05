package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamPresenceSocketEvent
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.data.voice.VoicePeerState
import com.lastasylum.alliance.ui.util.OVERLAY_INGAME_PRESENCE_STALE_MS
import com.lastasylum.alliance.ui.util.isOverlayIngameNow
import com.lastasylum.alliance.ui.util.parseIsoInstant
import java.time.Duration
import java.time.Instant

enum class PresenceSectionKind {
    Ingame,
    Recent,
}

enum class PresenceFreshness {
    Fresh,
    StaleSoon,
    Stale,
}

enum class OverlayOnlineFilterChip {
    All,
    IngameOnly,
    WithMic,
    RecentOnly,
}

data class OverlayOnlineMemberUiModel(
    val userId: String,
    val username: String,
    val telegramUsername: String?,
    val teamRole: String,
    val isLeader: Boolean,
    val presenceStatus: String?,
    val lastPresenceAt: String?,
    val isSelf: Boolean,
    val inGameNow: Boolean,
    val freshness: PresenceFreshness,
)

data class OverlayOnlinePresenceSection(
    val kind: PresenceSectionKind,
    val items: List<OverlayOnlineMemberUiModel>,
)

data class VoiceMemberFlags(
    val micOn: Boolean,
    val soundOn: Boolean,
)

data class OverlayOnlinePresenceLists(
    val ingame: List<PlayerTeamMemberDto>,
    val recentlyActive: List<PlayerTeamMemberDto>,
)

internal const val OVERLAY_PRESENCE_STALE_SOON_MS = 60_000L

fun presenceFreshness(
    lastPresenceAt: String?,
    now: Instant = Instant.now(),
): PresenceFreshness {
    val iso = lastPresenceAt?.trim().orEmpty()
    if (iso.isEmpty()) return PresenceFreshness.Stale
    val instant = parseIsoInstant(iso) ?: return PresenceFreshness.Stale
    val ageMs = Duration.between(instant, now).toMillis().coerceAtLeast(0)
    return when {
        ageMs <= OVERLAY_PRESENCE_STALE_SOON_MS -> PresenceFreshness.Fresh
        ageMs < OVERLAY_INGAME_PRESENCE_STALE_MS -> PresenceFreshness.StaleSoon
        else -> PresenceFreshness.Stale
    }
}

fun buildPresenceSections(
    ingame: List<PlayerTeamMemberDto>,
    recentlyActive: List<PlayerTeamMemberDto>,
    selfUserId: String?,
): List<OverlayOnlinePresenceSection> {
    val ingameIds = ingame.map { it.userId }.toSet()
    val ingameModels = sortIngameMembers(
        ingame.filter { isOverlayIngameNow(it.presenceStatus, it.lastPresenceAt) },
        selfUserId,
    ).map { it.toUiModel(selfUserId, inGameNow = true) }
    val recentModels = sortRecentMembers(
        recentlyActive.filter {
            it.userId !in ingameIds &&
                isRecentlyActiveOverlay(it.presenceStatus, it.lastPresenceAt)
        },
    ).map { it.toUiModel(selfUserId, inGameNow = false) }
    return listOf(
        OverlayOnlinePresenceSection(PresenceSectionKind.Ingame, ingameModels),
        OverlayOnlinePresenceSection(PresenceSectionKind.Recent, recentModels),
    )
}

fun isRecentlyActiveOverlay(
    presenceStatus: String?,
    lastPresenceAt: String?,
    now: Instant = Instant.now(),
): Boolean {
    val status = presenceStatus?.trim()?.lowercase().orEmpty()
    if (status == "ingame") return false
    if (isOverlayIngameNow(presenceStatus, lastPresenceAt)) return false
    val iso = lastPresenceAt?.trim().orEmpty()
    if (iso.isEmpty()) return false
    val instant = parseIsoInstant(iso) ?: return false
    val ageMs = Duration.between(instant, now).toMillis().coerceAtLeast(0)
    return ageMs <= OVERLAY_INGAME_PRESENCE_STALE_MS
}

fun mergePresenceSocketEvent(
    lists: OverlayOnlinePresenceLists,
    event: TeamPresenceSocketEvent,
    fallbackMember: PlayerTeamMemberDto? = null,
): OverlayOnlinePresenceLists {
    val ingame = lists.ingame.toMutableList()
    val recent = lists.recentlyActive.toMutableList()
    val nowIngame = isOverlayIngameNow(event.presenceStatus, event.lastPresenceAt)
    val existing = ingame.firstOrNull { it.userId == event.userId }
        ?: recent.firstOrNull { it.userId == event.userId }
        ?: fallbackMember
        ?: event.toMinimalMember()
    if (nowIngame) {
        recent.removeAll { it.userId == event.userId }
        val patched = existing.copy(
            presenceStatus = event.presenceStatus ?: existing.presenceStatus,
            lastPresenceAt = event.lastPresenceAt ?: existing.lastPresenceAt,
        )
        ingame.removeAll { it.userId == event.userId }
        ingame.add(patched)
    } else {
        ingame.removeAll { it.userId == event.userId }
        recent.removeAll { it.userId == event.userId }
        if (isRecentlyActiveOverlay(event.presenceStatus, event.lastPresenceAt)) {
            val patched = existing.copy(
                presenceStatus = event.presenceStatus ?: existing.presenceStatus,
                lastPresenceAt = event.lastPresenceAt ?: existing.lastPresenceAt,
            )
            recent.add(patched)
        }
    }
    return OverlayOnlinePresenceLists(ingame = ingame, recentlyActive = recent)
}

fun filterByQuery(
    sections: List<OverlayOnlinePresenceSection>,
    query: String,
): List<OverlayOnlinePresenceSection> {
    val q = query.trim()
    if (q.isEmpty()) return sections
    return sections.map { section ->
        section.copy(
            items = section.items.filter { member ->
                member.username.contains(q, ignoreCase = true) ||
                    member.telegramUsername?.contains(q, ignoreCase = true) == true
            },
        )
    }
}

fun filterByChip(
    sections: List<OverlayOnlinePresenceSection>,
    chip: OverlayOnlineFilterChip,
    voiceFlagsByUserId: Map<String, VoiceMemberFlags>,
): List<OverlayOnlinePresenceSection> {
    return when (chip) {
        OverlayOnlineFilterChip.All -> sections
        OverlayOnlineFilterChip.IngameOnly -> sections.map { section ->
            if (section.kind == PresenceSectionKind.Recent) {
                section.copy(items = emptyList())
            } else {
                section
            }
        }
        OverlayOnlineFilterChip.RecentOnly -> sections.map { section ->
            if (section.kind == PresenceSectionKind.Ingame) {
                section.copy(items = emptyList())
            } else {
                section
            }
        }
        OverlayOnlineFilterChip.WithMic -> sections.map { section ->
            section.copy(
                items = section.items.filter { member ->
                    voiceFlagsByUserId[member.userId]?.micOn == true
                },
            )
        }
    }
}

fun applyOnlinePanelFilters(
    baseSections: List<OverlayOnlinePresenceSection>,
    query: String,
    chip: OverlayOnlineFilterChip,
    voiceFlagsByUserId: Map<String, VoiceMemberFlags>,
): List<OverlayOnlinePresenceSection> {
    var sections = baseSections
    sections = filterByChip(sections, chip, voiceFlagsByUserId)
    sections = filterByQuery(sections, query)
    return sections
}

fun ingameCountFromSections(sections: List<OverlayOnlinePresenceSection>): Int =
    sections.firstOrNull { it.kind == PresenceSectionKind.Ingame }?.items?.size ?: 0

fun countFreshIngameMembers(ingame: List<PlayerTeamMemberDto>): Int =
    ingame.count { isOverlayIngameNow(it.presenceStatus, it.lastPresenceAt) }

fun rawIngameCount(ingame: List<PlayerTeamMemberDto>): Int =
    countFreshIngameMembers(ingame)

/** Drop stale ingame rows and mirror display buckets without an extra API call. */
fun reconcilePresenceLists(
    ingame: List<PlayerTeamMemberDto>,
    recentlyActive: List<PlayerTeamMemberDto>,
): OverlayOnlinePresenceLists {
    val freshIngame = ingame.filter { isOverlayIngameNow(it.presenceStatus, it.lastPresenceAt) }
    val ingameIds = freshIngame.map { it.userId }.toSet()
    val freshRecent = recentlyActive.filter { member ->
        member.userId !in ingameIds &&
            isRecentlyActiveOverlay(member.presenceStatus, member.lastPresenceAt)
    }
    return OverlayOnlinePresenceLists(ingame = freshIngame, recentlyActive = freshRecent)
}

fun rawRecentCount(
    ingame: List<PlayerTeamMemberDto>,
    recentlyActive: List<PlayerTeamMemberDto>,
): Int {
    val ingameIds = ingame.map { it.userId }.toSet()
    return recentlyActive.count {
        it.userId !in ingameIds &&
            isRecentlyActiveOverlay(it.presenceStatus, it.lastPresenceAt)
    }
}

private fun sortIngameMembers(
    members: List<PlayerTeamMemberDto>,
    selfUserId: String?,
): List<PlayerTeamMemberDto> {
    val selfId = selfUserId?.trim().orEmpty()
    return members.sortedWith(
        compareByDescending<PlayerTeamMemberDto> { if (selfId.isNotEmpty() && it.userId == selfId) 1 else 0 }
            .thenByDescending { squadRoleRank(it.teamRole) }
            .thenBy { it.username.lowercase() },
    )
}

private fun sortRecentMembers(members: List<PlayerTeamMemberDto>): List<PlayerTeamMemberDto> =
    members.sortedWith(
        compareByDescending<PlayerTeamMemberDto> { squadRoleRank(it.teamRole) }
            .thenBy { it.username.lowercase() },
    )

private fun squadRoleRank(role: String): Int {
    val r = role.trim().uppercase()
    return when {
        r.startsWith("R") -> r.drop(1).toIntOrNull()?.coerceIn(1, 5) ?: 1
        else -> 1
    }
}

internal fun overlayPresenceMemberListsEqual(
    a: List<PlayerTeamMemberDto>,
    b: List<PlayerTeamMemberDto>,
): Boolean {
    if (a.size != b.size) return false
    return a.indices.all { i ->
        val lhs = a[i]
        val rhs = b[i]
        lhs.userId == rhs.userId &&
            lhs.username == rhs.username &&
            lhs.teamRole == rhs.teamRole &&
            lhs.isLeader == rhs.isLeader &&
            lhs.presenceStatus == rhs.presenceStatus &&
            lhs.lastPresenceAt == rhs.lastPresenceAt
    }
}

/**
 * Voice badges are shown only for users in the team voice room (or self with a live overlay session).
 * Avoids stale mic/sound icons for teammates who are ingame but not connected to voice.
 */
internal fun shouldShowVoiceBadgesForMember(
    userId: String,
    selfUserId: String?,
    voicePeers: Map<String, VoicePeerState>,
    hasLocalVoiceSession: Boolean,
): Boolean {
    if (!selfUserId.isNullOrBlank() && userId == selfUserId && hasLocalVoiceSession) {
        return true
    }
    return voicePeers.containsKey(userId)
}

fun buildVoiceFlagsMap(
    memberUserIds: Collection<String>,
    voicePeers: Map<String, VoicePeerState>,
    selfUserId: String?,
    localMicOn: Boolean?,
    localSoundOn: Boolean?,
): Map<String, VoiceMemberFlags> {
    return memberUserIds.associateWith { userId ->
        val (micOn, soundOn) = TeamVoicePresenceStore.voiceFlagsForMember(
            memberUserId = userId,
            selfUserId = selfUserId,
            peers = voicePeers,
            localMicOn = localMicOn,
            localSoundOn = localSoundOn,
        )
        VoiceMemberFlags(micOn = micOn, soundOn = soundOn)
    }
}

private fun TeamPresenceSocketEvent.toMinimalMember(): PlayerTeamMemberDto =
    PlayerTeamMemberDto(
        userId = userId,
        username = userId.take(12),
        isLeader = false,
        teamRole = "R1",
        telegramUsername = null,
        presenceStatus = presenceStatus,
        lastPresenceAt = lastPresenceAt,
    )

private fun PlayerTeamMemberDto.toUiModel(
    selfUserId: String?,
    inGameNow: Boolean,
): OverlayOnlineMemberUiModel {
    val freshness = presenceFreshness(lastPresenceAt)
    return OverlayOnlineMemberUiModel(
        userId = userId,
        username = username,
        telegramUsername = telegramUsername,
        teamRole = teamRole.trim().uppercase().ifBlank { "R1" },
        isLeader = isLeader,
        presenceStatus = presenceStatus,
        lastPresenceAt = lastPresenceAt,
        isSelf = !selfUserId.isNullOrBlank() && userId == selfUserId,
        inGameNow = inGameNow,
        freshness = freshness,
    )
}
