package com.lastasylum.alliance.data.users

class UsersRepository(
    private val usersApi: UsersApi,
) {
    suspend fun getMyProfile(): Result<MyProfileDto> =
        runCatching { usersApi.getMyProfile() }

    suspend fun updateMyTelegram(username: String): Result<MyProfileDto> =
        runCatching { usersApi.updateMyTelegram(UpdateTelegramBody(username = username)) }

    suspend fun updateMyUsername(username: String): Result<MyProfileDto> =
        runCatching {
            usersApi.updateMyUsername(UpdateUsernameBody(username = username.trim()))
        }

    suspend fun updateMyTeamDisplay(name: String, tag: String): Result<MyProfileDto> =
        runCatching {
            usersApi.updateMyTeamDisplay(UpdateTeamDisplayBody(name = name, tag = tag))
        }

    suspend fun registerPushToken(token: String): Result<Unit> =
        runCatching { usersApi.registerPushToken(PushTokenBody(token)) }

    suspend fun clearPushTokens(): Result<Unit> =
        runCatching { usersApi.clearPushTokens() }

    suspend fun updatePresence(status: String): Result<Unit> =
        runCatching { usersApi.updatePresence(PresenceBody(status)) }

    suspend fun listMembers(
        allianceCode: String? = null,
        q: String? = null,
        skip: Int = 0,
        limit: Int = 300,
    ): Result<List<TeamMemberDto>> =
        runCatching {
            usersApi.listMembers(
                allianceCode = allianceCode?.takeIf { it.isNotBlank() },
                q = q?.trim()?.takeIf { it.isNotEmpty() },
                skip = skip,
                limit = limit,
            )
        }

    suspend fun updateMembership(userId: String, status: String): Result<TeamMemberDto> =
        runCatching {
            usersApi.updateMembership(userId, UpdateMembershipBody(status = status))
        }

    suspend fun updateRole(userId: String, role: String): Result<TeamMemberDto> =
        runCatching {
            usersApi.updateRole(UpdateRoleBody(userId = userId, role = role))
        }

    suspend fun updateUsername(userId: String, username: String): Result<TeamMemberDto> =
        runCatching {
            usersApi.updateUsername(userId, UpdateUsernameBody(username = username.trim()))
        }

    suspend fun deleteUser(userId: String): Result<Unit> =
        runCatching { usersApi.deleteUser(userId).let { } }
}
