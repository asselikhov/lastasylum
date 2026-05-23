package com.lastasylum.alliance.data

import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh

/** Binds or clears per-user inbox read cursors shared by chat, forum, and overlay. */
object ReadCursorSession {
    fun bind(
        chatRoomPreferences: ChatRoomPreferences,
        teamForumPreferences: TeamForumPreferences,
        userId: String,
    ) {
        val id = userId.trim()
        if (id.isEmpty()) return
        chatRoomPreferences.bindUser(id)
        teamForumPreferences.bindUser(id)
    }

    fun clearAll(
        chatRoomPreferences: ChatRoomPreferences,
        teamForumPreferences: TeamForumPreferences,
    ) {
        chatRoomPreferences.clear()
        teamForumPreferences.clear()
        ChatSessionCache.clear()
        OverlayGameStatusHudRefresh.invalidateNewsForumCache()
        JwtAccessTokenClaims.invalidateCache()
    }
}
