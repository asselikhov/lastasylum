package com.lastasylum.alliance.data.teams

class TeamsRepository(
    private val teamsApi: TeamsApi,
) {
    suspend fun createTeam(displayName: String, tag: String): Result<CreatePlayerTeamResponse> =
        runCatching {
            teamsApi.createTeam(CreatePlayerTeamBody(displayName = displayName, tag = tag))
        }

    suspend fun searchTeams(q: String): Result<List<TeamSearchResultDto>> =
        runCatching { teamsApi.searchTeams(q = q, limit = 30) }

    suspend fun listPendingJoinRequests(): Result<List<TeamJoinRequestDto>> =
        runCatching { teamsApi.listMyJoinRequests() }

    suspend fun getTeam(teamId: String): Result<TeamDetailDto> =
        runCatching { teamsApi.getTeam(teamId) }

    suspend fun submitJoinRequest(teamId: String): Result<SubmitJoinResponse> =
        runCatching { teamsApi.submitJoinRequest(teamId) }

    suspend fun acceptJoinRequest(requestId: String): Result<Unit> =
        runCatching { teamsApi.acceptJoinRequest(requestId).let { } }

    suspend fun rejectJoinRequest(requestId: String): Result<Unit> =
        runCatching { teamsApi.rejectJoinRequest(requestId).let { } }

    suspend fun addMember(teamId: String, username: String): Result<Unit> =
        runCatching {
            teamsApi.addMember(teamId, AddTeamMemberBody(username = username.trim())).let { }
        }

    suspend fun removeMember(teamId: String, userId: String): Result<Unit> =
        runCatching { teamsApi.removeMember(teamId, userId).let { } }

    suspend fun updateTeamBranding(
        teamId: String,
        displayName: String,
        tag: String,
    ): Result<Unit> =
        runCatching {
            teamsApi.updateTeamDisplay(
                teamId,
                UpdateTeamDisplayBody(
                    displayName = displayName.trim(),
                    tag = tag.trim(),
                ),
            ).let { }
        }

    suspend fun updateMemberSquadRole(
        teamId: String,
        memberUserId: String,
        role: String,
    ): Result<Unit> =
        runCatching {
            teamsApi.updateMemberSquadRole(
                teamId,
                memberUserId,
                UpdateSquadMemberRoleBody(role = role.trim()),
            ).let { }
        }
}
