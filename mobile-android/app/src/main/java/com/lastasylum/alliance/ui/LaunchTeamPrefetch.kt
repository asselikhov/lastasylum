package com.lastasylum.alliance.ui

import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.di.AppContainer

/**
 * Background prefetch of team tab data into disk cache (news, forum, team detail).
 */
suspend fun prefetchTeamLaunchContent(
    userId: String,
    profile: MyProfileDto,
    container: AppContainer,
) {
    if (userId.isBlank()) return
    val cache = container.launchDiskCache
    cache.saveProfile(userId, profile)
    val teamId = profile.playerTeamId?.trim().orEmpty()
    if (teamId.isEmpty()) return
    val teams = container.teamsRepository
    teams.getTeam(teamId)
        .onSuccess { cache.saveTeam(userId, it) }
    teams.listTeamNews(teamId, cursor = null, limit = 40)
        .onSuccess { cache.saveTeamNews(userId, teamId, it) }
    teams.listForumTopics(teamId)
        .onSuccess { cache.saveForumTopics(userId, teamId, it) }
}
