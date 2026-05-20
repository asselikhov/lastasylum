package com.lastasylum.alliance.data.admin

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AllianceAdminDto(
    val allianceCode: String,
    val publicId: String,
    val overlayEnabled: Boolean,
    val memberCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class UpdateAllianceOverlayBody(
    val overlayEnabled: Boolean,
)

@JsonClass(generateAdapter = true)
data class StickerPackCatalogItemDto(
    val key: String,
    val title: String,
)

@JsonClass(generateAdapter = true)
data class AllianceStickerAccessDto(
    val catalog: List<StickerPackCatalogItemDto>,
    val roleGrants: Map<String, List<String>>,
    val userGrants: Map<String, List<String>>,
)

@JsonClass(generateAdapter = true)
data class PutAllianceStickerAccessBody(
    val roleGrants: Map<String, List<String>>,
    val userGrants: Map<String, List<String>>,
)

@JsonClass(generateAdapter = true)
data class AdminUsersPageDto(
    val items: List<AdminUserOnServerDto>,
    val total: Int,
    val skip: Int,
    val limit: Int,
    val hasMore: Boolean,
)

@JsonClass(generateAdapter = true)
data class AdminTeamsPageDto(
    val items: List<PlayerTeamAdminDto>,
    val total: Int,
    val skip: Int,
    val limit: Int,
    val hasMore: Boolean,
)

@JsonClass(generateAdapter = true)
data class AdminOverviewDto(
    val playerTeamCount: Int = 0,
    val usersWithoutTeamCount: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PlayerTeamAdminDto(
    val id: String,
    val tag: String,
    val displayName: String,
    val leaderUserId: String,
    val leaderUsername: String,
    val leaderServerNumber: Int? = null,
    val serverNumbers: List<Int> = emptyList(),
    val memberCount: Int = 0,
    val chatRoutingSummary: String = "",
)

@JsonClass(generateAdapter = true)
data class AdminServerSummaryDto(
    val serverNumber: Int,
    val userCount: Int,
)

@JsonClass(generateAdapter = true)
data class AdminCreateGameIdentityBody(
    val serverNumber: Int,
    val gameNickname: String,
)

@JsonClass(generateAdapter = true)
data class AdminUpdateGameIdentityBody(
    val serverNumber: Int? = null,
    val gameNickname: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminUpdatePlayerTeamBody(
    val displayName: String? = null,
    val tag: String? = null,
)

@JsonClass(generateAdapter = true)
data class AdminUserOnServerDto(
    val userId: String,
    val identityId: String,
    val accountUsername: String,
    val email: String,
    val serverNumber: Int,
    val gameNickname: String,
    val playerTeamId: String? = null,
    val playerTeamTag: String? = null,
    val playerTeamDisplayName: String? = null,
    val isActiveIdentity: Boolean = false,
    val accountRole: String = "MEMBER",
    val membershipStatus: String = "active",
)

@JsonClass(generateAdapter = true)
data class AdminTeamMemberDto(
    val userId: String,
    val username: String,
    val accountUsername: String,
    val gameNickname: String,
    val serverNumber: Int? = null,
    val email: String,
    val isLeader: Boolean = false,
    val accountRole: String,
    val teamRole: String,
    val membershipStatus: String,
    val allianceName: String,
    val identityId: String? = null,
    val telegramUsername: String? = null,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
    val lastAppActiveAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class PlayerTeamDetailAdminDto(
    val id: String,
    val tag: String,
    val displayName: String,
    val leaderUserId: String,
    val members: List<AdminTeamMemberDto> = emptyList(),
)
