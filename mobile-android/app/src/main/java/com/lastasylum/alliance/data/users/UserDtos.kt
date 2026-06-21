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
    val overlayGameSearchEnabled: Boolean = false,
    val teamDisplayName: String? = null,
    val teamTag: String? = null,
    val membershipStatus: String,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
    val lastAppActiveAt: String? = null,
    val telegramUsername: String? = null,
    val avatarRelativeUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class GameIdentityDto(
    val id: String,
    val serverNumber: Int,
    val gameNickname: String,
    val playerTeamId: String? = null,
    val playerTeamTag: String? = null,
    val playerTeamDisplayName: String? = null,
)

@JsonClass(generateAdapter = true)
data class CreateGameIdentityBody(
    val serverNumber: Int,
    val gameNickname: String,
)

@JsonClass(generateAdapter = true)
data class UpdateGameIdentityBody(
    val serverNumber: Int? = null,
    val gameNickname: String? = null,
)

@JsonClass(generateAdapter = true)
data class SwitchActiveGameIdentityBody(
    val gameIdentityId: String,
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
    val overlayGameSearchEnabled: Boolean = false,
    val teamDisplayName: String? = null,
    val teamTag: String? = null,
    val membershipStatus: String,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
    val lastAppActiveAt: String? = null,
    val telegramUsername: String? = null,
    val avatarRelativeUrl: String? = null,
    val playerTeamId: String? = null,
    val playerTeamTag: String? = null,
    val playerTeamDisplayName: String? = null,
    val playerTeamLeaderUserId: String? = null,
    val isPlayerTeamLeader: Boolean = false,
    val pendingPlayerTeamJoinRequests: Int = 0,
    val playerTeamSquadRole: String? = null,
    val enabledStickerPacks: List<String> = emptyList(),
    val excavationPushEnabled: Boolean = true,
    val gameEventPushEnabled: Map<String, Boolean> = emptyMap(),
    val pushNotificationsRegistered: Boolean = false,
    val gameIdentities: List<GameIdentityDto> = emptyList(),
    val activeGameIdentityId: String? = null,
    val activeGameNickname: String? = null,
    val activeServerNumber: Int? = null,
)

@JsonClass(generateAdapter = true)
data class UploadedUserAvatarDto(
    val avatarRelativeUrl: String,
    val avatarUpdatedAt: String,
)

@JsonClass(generateAdapter = true)
data class UpdateNotificationPreferencesBody(
    val gameEventId: String? = null,
    val enabled: Boolean? = null,
    val excavationPushEnabled: Boolean? = null,
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
data class PushHealthDto(
    val pushTokenCount: Int = 0,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
    val overlayIngameNow: Boolean = false,
    val gameEventPushExcludedNow: Boolean = false,
    val gameEventPushExcludeStaleMs: Long = 20_000L,
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
