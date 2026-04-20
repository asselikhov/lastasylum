package com.lastasylum.alliance.data.users

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TeamMemberDto(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val allianceName: String,
    val alliancePublicId: String? = null,
    val overlayTabVisible: Boolean = false,
    val teamDisplayName: String? = null,
    val teamTag: String? = null,
    val membershipStatus: String,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
    val telegramUsername: String? = null,
)

@JsonClass(generateAdapter = true)
data class MyProfileDto(
    val id: String,
    val username: String,
    val email: String,
    val role: String,
    val allianceName: String,
    val alliancePublicId: String? = null,
    val overlayTabVisible: Boolean = false,
    val teamDisplayName: String? = null,
    val teamTag: String? = null,
    val membershipStatus: String,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
    val telegramUsername: String? = null,
    val playerTeamId: String? = null,
    val playerTeamTag: String? = null,
    val playerTeamDisplayName: String? = null,
    val playerTeamLeaderUserId: String? = null,
    val isPlayerTeamLeader: Boolean = false,
    val pendingPlayerTeamJoinRequests: Int = 0,
)

@JsonClass(generateAdapter = true)
data class UpdateTelegramBody(
    val username: String,
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
