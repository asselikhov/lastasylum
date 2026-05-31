package com.lastasylum.alliance.overlay

import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.ui.util.isOverlayIngameNow

/** Resolves team member presence for overlay reaction cards and similar UI. */
object OverlayMemberPresenceLookup {
    fun resolveMember(userId: String): PlayerTeamMemberDto? {
        val id = userId.trim()
        if (id.isEmpty()) return null
        return OverlayTeamPresenceCache.findMember(id)
            ?: OverlayTeamContextCache.memberDto(id)
    }

    fun isInGameNow(userId: String): Boolean {
        val member = resolveMember(userId) ?: return false
        return isOverlayIngameNow(member.presenceStatus, member.lastPresenceAt)
    }
}
