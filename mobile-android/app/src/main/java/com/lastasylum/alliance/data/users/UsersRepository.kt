package com.lastasylum.alliance.data.users

class UsersRepository(
    private val usersApi: UsersApi,
) {
    suspend fun listMembers(): Result<List<TeamMemberDto>> =
        runCatching { usersApi.listMembers() }

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
