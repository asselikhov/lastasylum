package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.chat.ToggleReactionRequest
import okhttp3.MultipartBody
import retrofit2.Response

/** Throws for every [TeamsApi] call; override only what a test needs via delegation. */
open class TeamsApiUnusedStub : TeamsApi {
    override suspend fun createTeam(body: CreatePlayerTeamBody): CreatePlayerTeamResponse = unused()
    override suspend fun searchTeams(q: String, limit: Int?): List<TeamSearchResultDto> = unused()
    override suspend fun listMyJoinRequests(): List<TeamJoinRequestDto> = unused()
    override suspend fun getTeam(teamId: String): TeamDetailDto = unused()
    override suspend fun getTeamOverlayPresence(teamId: String): TeamOverlayPresenceDto = unused()
    override suspend fun updateTeamDisplay(teamId: String, body: UpdateTeamDisplayBody): OkResponse = unused()
    override suspend fun updateMemberSquadRole(
        teamId: String,
        userId: String,
        body: UpdateSquadMemberRoleBody,
    ): OkResponse = unused()
    override suspend fun submitJoinRequest(teamId: String): Response<SubmitJoinResponse> = unused()
    override suspend fun acceptJoinRequest(requestId: String): Response<OkResponse> = unused()
    override suspend fun rejectJoinRequest(requestId: String): Response<OkResponse> = unused()
    override suspend fun addMember(teamId: String, body: AddTeamMemberBody): OkResponse = unused()
    override suspend fun removeMember(teamId: String, userId: String): OkResponse = unused()
    override suspend fun getTeamInboxBadges(teamId: String, newsAfter: String?): TeamInboxBadgesDto = unused()
    override suspend fun getTeamNewsReadCursor(teamId: String): TeamNewsReadCursorDto = unused()
    override suspend fun advanceTeamNewsReadCursor(
        teamId: String,
        body: AdvanceTeamNewsReadCursorBody,
    ): TeamNewsReadCursorDto = unused()
    override suspend fun listTeamNews(teamId: String, cursor: String?, limit: Int?): TeamNewsListPageDto = unused()
    override suspend fun getTeamNews(teamId: String, newsId: String): TeamNewsDetailDto = unused()
    override suspend fun createTeamNews(teamId: String, body: CreateTeamNewsBody): TeamNewsDetailDto = unused()
    override suspend fun updateTeamNews(
        teamId: String,
        newsId: String,
        body: UpdateTeamNewsBody,
    ): TeamNewsDetailDto = unused()
    override suspend fun deleteTeamNews(teamId: String, newsId: String): OkResponse = unused()
    override suspend fun voteTeamNews(
        teamId: String,
        newsId: String,
        body: VoteTeamNewsBody,
    ): TeamNewsDetailDto = unused()
    override suspend fun uploadTeamNewsAttachment(
        teamId: String,
        file: MultipartBody.Part,
    ): UploadedTeamNewsImageDto = unused()
    override suspend fun uploadForumAttachment(teamId: String, file: MultipartBody.Part): UploadedTeamNewsImageDto =
        unused()
    override suspend fun uploadForumFileAttachment(
        teamId: String,
        file: MultipartBody.Part,
    ): UploadedTeamNewsImageDto = unused()
    override suspend fun listForumTopics(teamId: String, view: String): List<TeamForumTopicDto> = unused()
    override suspend fun createForumTopic(teamId: String, body: CreateTeamForumTopicBody): TeamForumTopicDto = unused()
    override suspend fun pinForumTopicMessage(
        teamId: String,
        topicId: String,
        body: PinTeamForumTopicRequest,
    ): TeamForumTopicDto = unused()
    override suspend fun unpinOneForumTopicMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): TeamForumTopicDto = unused()
    override suspend fun updateForumTopic(
        teamId: String,
        topicId: String,
        body: UpdateTeamForumTopicBody,
    ): TeamForumTopicDto = unused()
    override suspend fun deleteForumTopic(teamId: String, topicId: String): OkResponse = unused()
    override suspend fun markForumTopicRead(
        teamId: String,
        topicId: String,
        body: MarkTeamForumTopicReadBody,
    ): Map<String, String?> = unused()
    override suspend fun getForumPeerReadCursor(teamId: String, topicId: String): ForumPeerReadCursorResponse =
        unused()
    override suspend fun listForumMessages(
        teamId: String,
        topicId: String,
        before: String?,
        limit: Int?,
    ): List<TeamForumMessageDto> = unused()
    override suspend fun postForumMessage(
        teamId: String,
        topicId: String,
        body: CreateTeamForumMessageBody,
    ): TeamForumMessageDto = unused()
    override suspend fun patchForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
        body: UpdateTeamForumMessageBody,
    ): TeamForumMessageDto = unused()
    override suspend fun deleteForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): ForumMessageMutationResponse = unused()
    override suspend fun bulkDeleteForumMessages(
        teamId: String,
        topicId: String,
        body: BulkDeleteForumMessagesBody,
    ): ForumBulkDeleteResponse = unused()
    override suspend fun forwardForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): TeamForumMessageDto = unused()
    override suspend fun toggleForumMessageReaction(
        teamId: String,
        topicId: String,
        messageId: String,
        body: ToggleReactionRequest,
    ): TeamForumMessageDto = unused()

    private fun unused(): Nothing = error("unused")
}
