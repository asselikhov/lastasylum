package com.lastasylum.alliance.data.users

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path

interface UsersApi {
    @GET("users/me")
    suspend fun getMyProfile(): MyProfileDto

    @PATCH("users/me/telegram")
    suspend fun updateMyTelegram(@Body body: UpdateTelegramBody): MyProfileDto

    @PATCH("users/me/username")
    suspend fun updateMyUsername(@Body body: UpdateUsernameBody): MyProfileDto

    @PATCH("users/me/team")
    suspend fun updateMyTeamDisplay(@Body body: UpdateTeamDisplayBody): MyProfileDto

    @POST("users/me/push-token")
    suspend fun registerPushToken(@Body body: PushTokenBody): Map<String, Boolean?>

    @DELETE("users/me/push-tokens")
    suspend fun clearPushTokens(): Map<String, Boolean?>

    @POST("users/me/presence")
    suspend fun updatePresence(@Body body: PresenceBody): Map<String, Boolean?>

    @GET("users")
    suspend fun listMembers(): List<TeamMemberDto>

    @PATCH("users/role")
    suspend fun updateRole(@Body body: UpdateRoleBody): TeamMemberDto

    @PATCH("users/{id}/membership")
    suspend fun updateMembership(
        @Path("id") id: String,
        @Body body: UpdateMembershipBody,
    ): TeamMemberDto

    @PATCH("users/{id}/username")
    suspend fun updateUsername(
        @Path("id") id: String,
        @Body body: UpdateUsernameBody,
    ): TeamMemberDto

    @DELETE("users/{id}")
    suspend fun deleteUser(@Path("id") id: String): DeleteUserResponse
}
