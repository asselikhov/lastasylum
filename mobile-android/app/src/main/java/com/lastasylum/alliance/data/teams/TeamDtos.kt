package com.lastasylum.alliance.data.teams

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreatePlayerTeamBody(
    val displayName: String,
    val tag: String,
)

@JsonClass(generateAdapter = true)
data class CreatePlayerTeamResponse(
    val teamId: String,
)

@JsonClass(generateAdapter = true)
data class PlayerTeamMemberDto(
    val userId: String,
    val username: String,
    val isLeader: Boolean,
    val role: String,
    val telegramUsername: String?,
)

@JsonClass(generateAdapter = true)
data class TeamDetailDto(
    val id: String,
    val tag: String,
    val displayName: String,
    val leaderUserId: String,
    val members: List<PlayerTeamMemberDto>,
)

@JsonClass(generateAdapter = true)
data class TeamSearchResultDto(
    val id: String,
    val tag: String,
    val displayName: String,
)

@JsonClass(generateAdapter = true)
data class TeamJoinRequestDto(
    val id: String,
    val requesterUserId: String,
    val requesterUsername: String,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class AddTeamMemberBody(
    val username: String,
)

@JsonClass(generateAdapter = true)
data class OkResponse(
    val ok: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class SubmitJoinResponse(
    val id: String,
)
