package com.lastasylum.alliance.data.admin

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamNewsListPageDto
import com.lastasylum.alliance.data.users.TeamMemberDto

class AdminRepository(
    private val adminApi: AdminApi,
) {
    suspend fun getOverview(): Result<AdminOverviewDto> =
        runCatching { adminApi.getOverview() }

    suspend fun listPlayerTeams(
        serverNumber: Int? = null,
        skip: Int = 0,
        limit: Int = 50,
    ): Result<AdminTeamsPageDto> =
        runCatching {
            adminApi.listPlayerTeams(
                serverNumber = serverNumber,
                skip = skip,
                limit = limit,
            )
        }

    suspend fun getPlayerTeam(teamId: String): Result<PlayerTeamDetailAdminDto> =
        runCatching { adminApi.getPlayerTeam(teamId) }

    suspend fun updatePlayerTeam(
        teamId: String,
        displayName: String?,
        tag: String?,
    ): Result<Unit> =
        runCatching {
            adminApi.updatePlayerTeam(
                teamId,
                AdminUpdatePlayerTeamBody(
                    displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
                    tag = tag?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }

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

    suspend fun patchUserStickerAccess(
        allianceCode: String,
        userId: String,
        packKeys: List<String>,
    ): Result<AllianceStickerAccessDto> =
        runCatching {
            adminApi.patchUserStickerAccess(
                allianceCode,
                userId,
                PatchUserStickerAccessBody(packKeys),
            )
        }

    suspend fun listGameServers(): Result<List<AdminServerSummaryDto>> =
        runCatching { adminApi.listGameServers() }

    suspend fun listUsersOnServers(
        serverNumber: Int? = null,
        q: String? = null,
        withoutTeam: Boolean = false,
        skip: Int = 0,
        limit: Int = 50,
    ): Result<AdminUsersPageDto> =
        runCatching {
            adminApi.listUsersOnServers(
                serverNumber = serverNumber,
                q = q?.trim()?.takeIf { it.isNotEmpty() },
                withoutTeam = if (withoutTeam) true else null,
                skip = skip,
                limit = limit,
            )
        }

    suspend fun listTeamChatRooms(teamId: String): Result<List<ChatRoomDto>> =
        runCatching { adminApi.listTeamChatRooms(teamId) }

    suspend fun listTeamNews(teamId: String): Result<TeamNewsListPageDto> =
        runCatching { adminApi.listTeamNews(teamId) }

    suspend fun listTeamForumTopics(teamId: String): Result<List<TeamForumTopicDto>> =
        runCatching { adminApi.listTeamForumTopics(teamId) }

    suspend fun listTeamForumMessages(
        teamId: String,
        topicId: String,
    ): Result<List<TeamForumMessageDto>> =
        runCatching { adminApi.listTeamForumMessages(teamId, topicId) }

    suspend fun listChatRoomMessages(
        roomId: String,
        before: String? = null,
    ): Result<List<ChatMessage>> =
        runCatching {
            adminApi.listChatRoomMessages(roomId = roomId, before = before)
        }

    suspend fun createGameIdentity(
        userId: String,
        gameNickname: String,
        serverNumber: Int,
    ): Result<Unit> =
        runCatching {
            adminApi.createGameIdentity(
                userId = userId,
                body = AdminCreateGameIdentityBody(
                    serverNumber = serverNumber,
                    gameNickname = gameNickname.trim(),
                ),
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
                    gameNickname = gameNickname.trim(),
                    serverNumber = serverNumber,
                ),
            )
        }
}
