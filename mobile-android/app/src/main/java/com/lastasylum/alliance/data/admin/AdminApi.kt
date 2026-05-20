package com.lastasylum.alliance.data.admin

import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamNewsListPageDto
import com.lastasylum.alliance.data.users.TeamMemberDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminApi {
    @GET("admin/overview")
    suspend fun getOverview(): AdminOverviewDto

    @GET("admin/player-teams")
    suspend fun listPlayerTeams(
        @Query("serverNumber") serverNumber: Int? = null,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 50,
    ): AdminTeamsPageDto

    @GET("admin/player-teams/{teamId}")
    suspend fun getPlayerTeam(
        @Path("teamId") teamId: String,
    ): PlayerTeamDetailAdminDto

    @PATCH("admin/player-teams/{teamId}")
    suspend fun updatePlayerTeam(
        @Path("teamId") teamId: String,
        @Body body: AdminUpdatePlayerTeamBody,
    ): Map<String, Boolean>

    @GET("admin/users/without-team")
    suspend fun listUsersWithoutTeam(
        @Query("q") q: String? = null,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 300,
    ): List<TeamMemberDto>

    @GET("admin/alliances")
    suspend fun listAlliances(): List<AllianceAdminDto>

    @PATCH("admin/alliances/{publicId}/overlay")
    suspend fun updateAllianceOverlay(
        @Path("publicId") publicId: String,
        @Body body: UpdateAllianceOverlayBody,
    ): AllianceAdminDto

    @GET("admin/sticker-access/{allianceCode}")
    suspend fun getStickerAccess(
        @Path("allianceCode") allianceCode: String,
    ): AllianceStickerAccessDto

    @PUT("admin/sticker-access/{allianceCode}")
    suspend fun putStickerAccess(
        @Path("allianceCode") allianceCode: String,
        @Body body: PutAllianceStickerAccessBody,
    ): AllianceStickerAccessDto

    @GET("admin/game-servers")
    suspend fun listGameServers(): List<AdminServerSummaryDto>

    @GET("admin/game-identities/users")
    suspend fun listUsersOnServers(
        @Query("serverNumber") serverNumber: Int? = null,
        @Query("q") q: String? = null,
        @Query("withoutTeam") withoutTeam: Boolean? = null,
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 50,
    ): AdminUsersPageDto

    @GET("admin/player-teams/{teamId}/chat-rooms")
    suspend fun listTeamChatRooms(
        @Path("teamId") teamId: String,
    ): List<ChatRoomDto>

    @GET("admin/player-teams/{teamId}/news")
    suspend fun listTeamNews(
        @Path("teamId") teamId: String,
        @Query("limit") limit: Int = 100,
    ): TeamNewsListPageDto

    @GET("admin/player-teams/{teamId}/forum/topics")
    suspend fun listTeamForumTopics(
        @Path("teamId") teamId: String,
    ): List<TeamForumTopicDto>

    @GET("admin/player-teams/{teamId}/forum/topics/{topicId}/messages")
    suspend fun listTeamForumMessages(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
        @Query("limit") limit: Int = 100,
    ): List<TeamForumMessageDto>

    @GET("admin/chat-rooms/{roomId}/messages")
    suspend fun listChatRoomMessages(
        @Path("roomId") roomId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int = 80,
    ): List<ChatMessage>

    @PATCH("admin/users/{userId}/game-identities/{identityId}")
    suspend fun updateGameIdentity(
        @Path("userId") userId: String,
        @Path("identityId") identityId: String,
        @Body body: AdminUpdateGameIdentityBody,
    ): com.lastasylum.alliance.data.users.MyProfileDto
}
