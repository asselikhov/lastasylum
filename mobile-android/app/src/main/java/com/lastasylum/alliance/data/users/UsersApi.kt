package com.lastasylum.alliance.data.users

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Multipart

interface UsersApi {
    @GET("users/me")
    suspend fun getMyProfile(): MyProfileDto

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadMyAvatar(@Part file: MultipartBody.Part): UploadedUserAvatarDto

    @DELETE("users/me/avatar")
    suspend fun deleteMyAvatar(): Map<String, Boolean?>

    @PATCH("users/me/telegram")
    suspend fun updateMyTelegram(@Body body: UpdateTelegramBody): MyProfileDto

    @PATCH("users/me/username")
    suspend fun updateMyUsername(@Body body: UpdateUsernameBody): MyProfileDto

    @POST("users/me/game-identities")
    suspend fun addGameIdentity(@Body body: CreateGameIdentityBody): MyProfileDto

    @PATCH("users/me/game-identities/{identityId}")
    suspend fun updateGameIdentity(
        @Path("identityId") identityId: String,
        @Body body: UpdateGameIdentityBody,
    ): MyProfileDto

    @DELETE("users/me/game-identities/{identityId}")
    suspend fun deleteGameIdentity(@Path("identityId") identityId: String): MyProfileDto

    @POST("users/me/active-game-identity")
    suspend fun switchActiveGameIdentity(
        @Body body: SwitchActiveGameIdentityBody,
    ): MyProfileDto

    @PATCH("users/me/notification-preferences")
    suspend fun updateNotificationPreferences(
        @Body body: UpdateNotificationPreferencesBody,
    ): MyProfileDto

    @GET("users/me/push-health")
    suspend fun getPushHealth(): PushHealthDto

    @POST("users/me/push-token")
    suspend fun registerPushToken(@Body body: PushTokenBody): Map<String, Boolean?>

    @DELETE("users/me/push-tokens")
    suspend fun clearPushTokens(): Map<String, Boolean?>

    @POST("users/me/presence")
    suspend fun updatePresence(@Body body: PresenceBody): Map<String, Boolean?>

    @GET("users")
    suspend fun listMembers(
        @Query("allianceCode") allianceCode: String? = null,
        @Query("q") q: String? = null,
        @Query("skip") skip: Int? = null,
        @Query("limit") limit: Int? = null,
    ): List<TeamMemberDto>

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
