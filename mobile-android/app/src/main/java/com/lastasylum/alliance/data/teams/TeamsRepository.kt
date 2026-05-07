package com.lastasylum.alliance.data.teams

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class TeamsRepository(
    private val teamsApi: TeamsApi,
) {
    suspend fun createTeam(displayName: String, tag: String): Result<CreatePlayerTeamResponse> =
        runCatching {
            teamsApi.createTeam(CreatePlayerTeamBody(displayName = displayName, tag = tag))
        }

    suspend fun searchTeams(q: String): Result<List<TeamSearchResultDto>> =
        runCatching { teamsApi.searchTeams(q = q, limit = 30) }

    suspend fun listPendingJoinRequests(): Result<List<TeamJoinRequestDto>> =
        runCatching { teamsApi.listMyJoinRequests() }

    suspend fun getTeam(teamId: String): Result<TeamDetailDto> =
        runCatching { teamsApi.getTeam(teamId) }

    suspend fun submitJoinRequest(teamId: String): Result<SubmitJoinResponse> =
        runCatching { teamsApi.submitJoinRequest(teamId) }

    suspend fun acceptJoinRequest(requestId: String): Result<Unit> =
        runCatching { teamsApi.acceptJoinRequest(requestId).let { } }

    suspend fun rejectJoinRequest(requestId: String): Result<Unit> =
        runCatching { teamsApi.rejectJoinRequest(requestId).let { } }

    suspend fun addMember(teamId: String, username: String): Result<Unit> =
        runCatching {
            teamsApi.addMember(teamId, AddTeamMemberBody(username = username.trim())).let { }
        }

    suspend fun removeMember(teamId: String, userId: String): Result<Unit> =
        runCatching { teamsApi.removeMember(teamId, userId).let { } }

    suspend fun updateTeamBranding(
        teamId: String,
        displayName: String,
        tag: String,
    ): Result<Unit> =
        runCatching {
            teamsApi.updateTeamDisplay(
                teamId,
                UpdateTeamDisplayBody(
                    displayName = displayName.trim(),
                    tag = tag.trim(),
                ),
            ).let { }
        }

    suspend fun updateMemberSquadRole(
        teamId: String,
        memberUserId: String,
        role: String,
    ): Result<Unit> =
        runCatching {
            teamsApi.updateMemberSquadRole(
                teamId,
                memberUserId,
                UpdateSquadMemberRoleBody(role = role.trim()),
            ).let { }
        }

    suspend fun listTeamNews(
        teamId: String,
        cursor: String? = null,
        limit: Int = 30,
    ): Result<TeamNewsListPageDto> =
        runCatching { teamsApi.listTeamNews(teamId, cursor, limit) }

    suspend fun getTeamNews(teamId: String, newsId: String): Result<TeamNewsDetailDto> =
        runCatching { teamsApi.getTeamNews(teamId, newsId) }

    suspend fun createTeamNews(
        teamId: String,
        body: CreateTeamNewsBody,
    ): Result<TeamNewsDetailDto> =
        runCatching { teamsApi.createTeamNews(teamId, body) }

    suspend fun updateTeamNews(
        teamId: String,
        newsId: String,
        body: UpdateTeamNewsBody,
    ): Result<TeamNewsDetailDto> =
        runCatching { teamsApi.updateTeamNews(teamId, newsId, body) }

    suspend fun deleteTeamNews(teamId: String, newsId: String): Result<Unit> =
        runCatching { teamsApi.deleteTeamNews(teamId, newsId).let { } }

    suspend fun voteTeamNews(
        teamId: String,
        newsId: String,
        optionId: String,
    ): Result<TeamNewsDetailDto> =
        runCatching {
            teamsApi.voteTeamNews(
                teamId,
                newsId,
                VoteTeamNewsBody(optionId = optionId),
            )
        }

    suspend fun uploadTeamNewsImage(
        teamId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> =
        runCatching {
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            teamsApi.uploadTeamNewsAttachment(teamId, part)
        }

    suspend fun listForumTopics(teamId: String): Result<List<TeamForumTopicDto>> =
        runCatching { teamsApi.listForumTopics(teamId) }

    suspend fun createForumTopic(teamId: String, title: String): Result<TeamForumTopicDto> =
        runCatching {
            teamsApi.createForumTopic(teamId, CreateTeamForumTopicBody(title = title.trim()))
        }

    suspend fun updateForumTopic(
        teamId: String,
        topicId: String,
        title: String,
    ): Result<TeamForumTopicDto> =
        runCatching {
            teamsApi.updateForumTopic(
                teamId,
                topicId,
                UpdateTeamForumTopicBody(title = title.trim()),
            )
        }

    suspend fun deleteForumTopic(teamId: String, topicId: String): Result<Unit> =
        runCatching { teamsApi.deleteForumTopic(teamId, topicId).let { } }

    suspend fun listForumMessages(
        teamId: String,
        topicId: String,
        before: String? = null,
        limit: Int = 50,
    ): Result<List<TeamForumMessageDto>> =
        runCatching {
            teamsApi.listForumMessages(teamId, topicId, before, limit)
        }

    suspend fun postForumMessage(
        teamId: String,
        topicId: String,
        text: String,
    ): Result<TeamForumMessageDto> =
        runCatching {
            teamsApi.postForumMessage(
                teamId,
                topicId,
                CreateTeamForumMessageBody(text = text.trim()),
            )
        }

    suspend fun patchForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
        text: String,
    ): Result<TeamForumMessageDto> =
        runCatching {
            teamsApi.patchForumMessage(
                teamId,
                topicId,
                messageId,
                UpdateTeamForumMessageBody(text = text.trim()),
            )
        }

    suspend fun deleteForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): Result<Unit> =
        runCatching {
            teamsApi.deleteForumMessage(teamId, topicId, messageId).let { }
        }
}
