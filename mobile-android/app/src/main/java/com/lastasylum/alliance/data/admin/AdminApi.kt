package com.lastasylum.alliance.data.admin

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
    suspend fun listPlayerTeams(): List<PlayerTeamAdminDto>

    @GET("admin/player-teams/{teamId}")
    suspend fun getPlayerTeam(
        @Path("teamId") teamId: String,
    ): PlayerTeamDetailAdminDto

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
}
