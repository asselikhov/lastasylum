package com.lastasylum.alliance.data.teams

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TeamsApi {
    @POST("teams")
    suspend fun createTeam(@Body body: CreatePlayerTeamBody): CreatePlayerTeamResponse

    @GET("teams/search")
    suspend fun searchTeams(
        @Query("q") q: String,
        @Query("limit") limit: Int? = null,
    ): List<TeamSearchResultDto>

    @GET("teams/me/join-requests")
    suspend fun listMyJoinRequests(): List<TeamJoinRequestDto>

    @GET("teams/{teamId}")
    suspend fun getTeam(@Path("teamId") teamId: String): TeamDetailDto

    @PATCH("teams/{teamId}/display")
    suspend fun updateTeamDisplay(
        @Path("teamId") teamId: String,
        @Body body: UpdateTeamDisplayBody,
    ): OkResponse

    @PATCH("teams/{teamId}/members/{userId}/role")
    suspend fun updateMemberSquadRole(
        @Path("teamId") teamId: String,
        @Path("userId") userId: String,
        @Body body: UpdateSquadMemberRoleBody,
    ): OkResponse

    @POST("teams/{teamId}/join-requests")
    suspend fun submitJoinRequest(@Path("teamId") teamId: String): SubmitJoinResponse

    @POST("teams/join-requests/{requestId}/accept")
    suspend fun acceptJoinRequest(@Path("requestId") requestId: String): OkResponse

    @POST("teams/join-requests/{requestId}/reject")
    suspend fun rejectJoinRequest(@Path("requestId") requestId: String): OkResponse

    @POST("teams/{teamId}/members")
    suspend fun addMember(
        @Path("teamId") teamId: String,
        @Body body: AddTeamMemberBody,
    ): OkResponse

    @DELETE("teams/{teamId}/members/{userId}")
    suspend fun removeMember(
        @Path("teamId") teamId: String,
        @Path("userId") userId: String,
    ): OkResponse
}
