package com.lastasylum.alliance.data

import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.chat.ChatRoomPreferences
import com.lastasylum.alliance.data.chat.ChatSessionCache
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamNewsReadCursorSync
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Binds or clears per-user inbox read cursors shared by chat, forum, news, and overlay. */
object ReadCursorSession {
    fun bind(
        chatRoomPreferences: ChatRoomPreferences,
        teamForumPreferences: TeamForumPreferences,
        userSettingsPreferences: UserSettingsPreferences,
        userId: String,
    ) {
        val id = userId.trim()
        if (id.isEmpty()) return
        chatRoomPreferences.bindUser(id)
        teamForumPreferences.bindUser(id)
        userSettingsPreferences.bindUser(id)
    }

    /** Pull/push team news read cursor after login or overlay bind (reinstall-safe). */
    suspend fun syncTeamNewsReadCursor(
        usersRepository: UsersRepository,
        teamsRepository: TeamsRepository,
        userSettingsPreferences: UserSettingsPreferences,
    ) {
        withContext(Dispatchers.IO) {
            val teamId = usersRepository.getMyProfile().getOrNull()?.playerTeamId?.trim().orEmpty()
            if (teamId.isEmpty()) return@withContext
            TeamNewsReadCursorSync.pushPrefsToServerIfNewer(
                teamsRepository,
                userSettingsPreferences,
                teamId,
            )
            TeamNewsReadCursorSync.pullServerCursorIntoPrefs(
                teamsRepository,
                userSettingsPreferences,
                teamId,
            )
        }
    }

    fun clearAll(
        chatRoomPreferences: ChatRoomPreferences,
        teamForumPreferences: TeamForumPreferences,
        userSettingsPreferences: UserSettingsPreferences,
    ) {
        chatRoomPreferences.clear()
        teamForumPreferences.clear()
        userSettingsPreferences.clearNewsReadCursor()
        ChatSessionCache.clear()
        OverlayGameStatusHudRefresh.invalidateNewsForumCache()
        JwtAccessTokenClaims.invalidateCache()
    }
}
