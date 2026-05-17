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
    val memberCount: Int = 0,
    val chatRoutingSummary: String = "",
)

@JsonClass(generateAdapter = true)
data class AdminTeamMemberDto(
    val userId: String,
    val username: String,
    val email: String,
    val isLeader: Boolean = false,
    val allianceRole: String,
    val teamRole: String,
    val membershipStatus: String,
    val allianceName: String,
    val telegramUsername: String? = null,
    val presenceStatus: String? = null,
    val lastPresenceAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class PlayerTeamDetailAdminDto(
    val id: String,
    val tag: String,
    val displayName: String,
    val leaderUserId: String,
    val members: List<AdminTeamMemberDto> = emptyList(),
)
