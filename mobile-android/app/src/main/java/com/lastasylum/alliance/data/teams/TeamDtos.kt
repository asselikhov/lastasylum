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
    /** Alliance / app role (R2–R5 in backend). */
    val allianceRole: String = "R2",
    /** Squad role R1–R5 (R5 = leader). */
    val teamRole: String = "R1",
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
data class UpdateTeamDisplayBody(
    val displayName: String,
    val tag: String,
)

@JsonClass(generateAdapter = true)
data class UpdateSquadMemberRoleBody(
    val role: String,
)

@JsonClass(generateAdapter = true)
data class SubmitJoinResponse(
    val id: String,
)

@JsonClass(generateAdapter = true)
data class TeamNewsPollTallyDto(
    val optionId: String,
    val count: Int,
)

@JsonClass(generateAdapter = true)
data class TeamNewsListItemDto(
    val id: String,
    val teamId: String,
    val title: String,
    val excerpt: String,
    val authorUserId: String,
    val authorUsername: String,
    val createdAt: String,
    val updatedAt: String,
    val hasPoll: Boolean,
    val firstImageRelativeUrl: String?,
    val pollTallies: List<TeamNewsPollTallyDto> = emptyList(),
    val myVoteOptionId: String? = null,
)

@JsonClass(generateAdapter = true)
data class TeamNewsListPageDto(
    val items: List<TeamNewsListItemDto>,
    val nextCursor: String?,
)

@JsonClass(generateAdapter = true)
data class TeamNewsPollOptionDto(
    val id: String,
    val text: String,
)

@JsonClass(generateAdapter = true)
data class TeamNewsPollVoteDto(
    val userId: String,
    val optionId: String,
    /** Отображаемый ник; с бэкенда с версии poll voter enrichment. */
    val username: String? = null,
)

@JsonClass(generateAdapter = true)
data class TeamNewsPollDetailDto(
    val question: String,
    val options: List<TeamNewsPollOptionDto>,
    val votes: List<TeamNewsPollVoteDto> = emptyList(),
    val tallies: List<TeamNewsPollTallyDto>,
    val myVoteOptionId: String?,
)

@JsonClass(generateAdapter = true)
data class TeamNewsDetailDto(
    val id: String,
    val teamId: String,
    val title: String,
    val excerpt: String,
    val authorUserId: String,
    val authorUsername: String,
    val createdAt: String,
    val updatedAt: String,
    val hasPoll: Boolean,
    val firstImageRelativeUrl: String?,
    val pollTallies: List<TeamNewsPollTallyDto> = emptyList(),
    val myVoteOptionId: String? = null,
    val body: String,
    val imageRelativeUrls: List<String> = emptyList(),
    val poll: TeamNewsPollDetailDto? = null,
)

@JsonClass(generateAdapter = true)
data class TeamNewsPollCreateBody(
    val question: String,
    val optionTexts: List<String>,
)

@JsonClass(generateAdapter = true)
data class CreateTeamNewsBody(
    val title: String,
    val body: String,
    val imageFileIds: List<String> = emptyList(),
    val poll: TeamNewsPollCreateBody? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateTeamNewsBody(
    val title: String? = null,
    val body: String? = null,
    val imageFileIds: List<String>? = null,
    val poll: TeamNewsPollCreateBody? = null,
    val clearPoll: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class VoteTeamNewsBody(
    val optionId: String,
)

@JsonClass(generateAdapter = true)
data class UploadedTeamNewsImageDto(
    val fileId: String,
    val url: String,
    val mimeType: String,
    val size: Int,
)

@JsonClass(generateAdapter = true)
data class TeamForumTopicDto(
    val id: String,
    val teamId: String,
    val title: String,
    val createdByUserId: String,
    val messageCount: Int,
    val lastMessageAt: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class CreateTeamForumTopicBody(
    val title: String,
)

@JsonClass(generateAdapter = true)
data class UpdateTeamForumTopicBody(
    val title: String,
)

@JsonClass(generateAdapter = true)
data class TeamForumMessageDto(
    val id: String,
    val topicId: String,
    val teamId: String,
    val senderUserId: String,
    val senderUsername: String,
    val text: String,
    val replyToMessageId: String? = null,
    val replyTo: TeamForumReplyPreviewDto? = null,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val deletedByUserId: String? = null,
    val imageRelativeUrl: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@JsonClass(generateAdapter = true)
data class TeamForumReplyPreviewDto(
    val id: String,
    val senderUsername: String,
    val text: String,
)

@JsonClass(generateAdapter = true)
data class CreateTeamForumMessageBody(
    val text: String = "",
    val replyToMessageId: String? = null,
    val imageFileId: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateTeamForumMessageBody(
    val text: String,
)

@JsonClass(generateAdapter = true)
data class BulkDeleteForumMessagesBody(
    val messageIds: List<String>,
)
