package com.lastasylum.alliance.data.users

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface UsersApi {
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
