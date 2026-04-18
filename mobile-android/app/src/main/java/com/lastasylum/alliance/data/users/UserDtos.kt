package com.lastasylum.alliance.data.users

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TeamMemberDto(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val allianceName: String,
    val membershipStatus: String,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class PushTokenBody(
    val token: String,
)

@JsonClass(generateAdapter = true)
data class PresenceBody(
    val status: String,
)

@JsonClass(generateAdapter = true)
data class UpdateRoleBody(
    val userId: String,
    val role: String,
)

@JsonClass(generateAdapter = true)
data class UpdateMembershipBody(
    val status: String,
)

@JsonClass(generateAdapter = true)
data class UpdateUsernameBody(
    val username: String,
)

@JsonClass(generateAdapter = true)
data class DeleteUserResponse(
    val success: Boolean = false,
)
