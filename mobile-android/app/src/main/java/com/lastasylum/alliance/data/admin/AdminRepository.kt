package com.lastasylum.alliance.data.admin

import com.lastasylum.alliance.data.users.TeamMemberDto

class AdminRepository(
    private val adminApi: AdminApi,
) {
    suspend fun getOverview(): Result<AdminOverviewDto> =
        runCatching { adminApi.getOverview() }

    suspend fun listPlayerTeams(): Result<List<PlayerTeamAdminDto>> =
        runCatching { adminApi.listPlayerTeams() }

    suspend fun getPlayerTeam(teamId: String): Result<PlayerTeamDetailAdminDto> =
        runCatching { adminApi.getPlayerTeam(teamId) }

    suspend fun listUsersWithoutTeam(
        q: String? = null,
        skip: Int = 0,
        limit: Int = 300,
    ): Result<List<TeamMemberDto>> =
        runCatching {
            adminApi.listUsersWithoutTeam(
                q = q?.trim()?.takeIf { it.isNotEmpty() },
                skip = skip,
                limit = limit,
            )
        }

    suspend fun listAlliances(): Result<List<AllianceAdminDto>> =
        runCatching { adminApi.listAlliances() }

    suspend fun setOverlayEnabled(
        publicId: String,
        enabled: Boolean,
    ): Result<AllianceAdminDto> =
        runCatching {
            adminApi.updateAllianceOverlay(
                publicId,
                UpdateAllianceOverlayBody(overlayEnabled = enabled),
            )
        }

    suspend fun getStickerAccess(allianceCode: String): Result<AllianceStickerAccessDto> =
        runCatching { adminApi.getStickerAccess(allianceCode) }

    suspend fun putStickerAccess(
        allianceCode: String,
        body: PutAllianceStickerAccessBody,
    ): Result<AllianceStickerAccessDto> =
        runCatching { adminApi.putStickerAccess(allianceCode, body) }

    suspend fun listGameServers(): Result<List<AdminServerSummaryDto>> =
        runCatching { adminApi.listGameServers() }

    suspend fun listUsersOnServers(
        serverNumber: Int? = null,
        q: String? = null,
    ): Result<List<AdminUserOnServerDto>> =
        runCatching {
            adminApi.listUsersOnServers(
                serverNumber = serverNumber,
                q = q?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

    suspend fun updateGameIdentity(
        userId: String,
        identityId: String,
        gameNickname: String,
        serverNumber: Int? = null,
    ): Result<Unit> =
        runCatching {
            adminApi.updateGameIdentity(
                userId = userId,
                identityId = identityId,
                body = AdminUpdateGameIdentityBody(
                    gameNickname = gameNickname,
                    serverNumber = serverNumber,
                ),
            )
        }
}
