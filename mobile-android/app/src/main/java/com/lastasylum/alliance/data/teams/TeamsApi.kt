package com.lastasylum.alliance.data.teams

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface TeamsApi {
    @POST("teams")
    suspend fun createTeam(@Body body: CreatePlayerTeamBody): CreatePlayerTeamResponse

    @GET("teams/search")
    suspend fun searchTeams(
        @Query("q") q: String,
        @Query("limit") limit: Int? = null,
    ): List<TeamSearchResultDto>

    @GET("teams/me/join-requests")
    suspend fun listMyJoinRequests(): List<TeamJoinRequestDto>

    @GET("teams/{teamId}")
    suspend fun getTeam(@Path("teamId") teamId: String): TeamDetailDto

    @PATCH("teams/{teamId}/display")
    suspend fun updateTeamDisplay(
        @Path("teamId") teamId: String,
        @Body body: UpdateTeamDisplayBody,
    ): OkResponse

    @PATCH("teams/{teamId}/members/{userId}/role")
    suspend fun updateMemberSquadRole(
        @Path("teamId") teamId: String,
        @Path("userId") userId: String,
        @Body body: UpdateSquadMemberRoleBody,
    ): OkResponse

    @POST("teams/{teamId}/join-requests")
    suspend fun submitJoinRequest(@Path("teamId") teamId: String): SubmitJoinResponse

    @POST("teams/join-requests/{requestId}/accept")
    suspend fun acceptJoinRequest(@Path("requestId") requestId: String): OkResponse

    @POST("teams/join-requests/{requestId}/reject")
    suspend fun rejectJoinRequest(@Path("requestId") requestId: String): OkResponse

    @POST("teams/{teamId}/members")
    suspend fun addMember(
        @Path("teamId") teamId: String,
        @Body body: AddTeamMemberBody,
    ): OkResponse

    @DELETE("teams/{teamId}/members/{userId}")
    suspend fun removeMember(
        @Path("teamId") teamId: String,
        @Path("userId") userId: String,
    ): OkResponse

    @GET("teams/{teamId}/news")
    suspend fun listTeamNews(
        @Path("teamId") teamId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): TeamNewsListPageDto

    @GET("teams/{teamId}/news/{newsId}")
    suspend fun getTeamNews(
        @Path("teamId") teamId: String,
        @Path("newsId") newsId: String,
    ): TeamNewsDetailDto

    @POST("teams/{teamId}/news")
    suspend fun createTeamNews(
        @Path("teamId") teamId: String,
        @Body body: CreateTeamNewsBody,
    ): TeamNewsDetailDto

    @PATCH("teams/{teamId}/news/{newsId}")
    suspend fun updateTeamNews(
        @Path("teamId") teamId: String,
        @Path("newsId") newsId: String,
        @Body body: UpdateTeamNewsBody,
    ): TeamNewsDetailDto

    @DELETE("teams/{teamId}/news/{newsId}")
    suspend fun deleteTeamNews(
        @Path("teamId") teamId: String,
        @Path("newsId") newsId: String,
    ): OkResponse

    @POST("teams/{teamId}/news/{newsId}/vote")
    suspend fun voteTeamNews(
        @Path("teamId") teamId: String,
        @Path("newsId") newsId: String,
        @Body body: VoteTeamNewsBody,
    ): TeamNewsDetailDto

    @Multipart
    @POST("teams/{teamId}/news/attachments")
    suspend fun uploadTeamNewsAttachment(
        @Path("teamId") teamId: String,
        @Part file: MultipartBody.Part,
    ): UploadedTeamNewsImageDto

    @Multipart
    @POST("teams/{teamId}/forum/attachments")
    suspend fun uploadForumAttachment(
        @Path("teamId") teamId: String,
        @Part file: MultipartBody.Part,
    ): UploadedTeamNewsImageDto

    @Multipart
    @POST("teams/{teamId}/forum/attachments/file")
    suspend fun uploadForumFileAttachment(
        @Path("teamId") teamId: String,
        @Part file: MultipartBody.Part,
    ): UploadedTeamNewsImageDto

    @GET("teams/{teamId}/forum/topics")
    suspend fun listForumTopics(@Path("teamId") teamId: String): List<TeamForumTopicDto>

    @POST("teams/{teamId}/forum/topics")
    suspend fun createForumTopic(
        @Path("teamId") teamId: String,
        @Body body: CreateTeamForumTopicBody,
    ): TeamForumTopicDto

    @PATCH("teams/{teamId}/forum/topics/{topicId}")
    suspend fun updateForumTopic(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
        @Body body: UpdateTeamForumTopicBody,
    ): TeamForumTopicDto

    @DELETE("teams/{teamId}/forum/topics/{topicId}")
    suspend fun deleteForumTopic(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
    ): OkResponse

    @GET("teams/{teamId}/forum/topics/{topicId}/messages")
    suspend fun listForumMessages(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
        @Query("before") before: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<TeamForumMessageDto>

    @POST("teams/{teamId}/forum/topics/{topicId}/messages")
    suspend fun postForumMessage(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
        @Body body: CreateTeamForumMessageBody,
    ): TeamForumMessageDto

    @PATCH("teams/{teamId}/forum/topics/{topicId}/messages/{messageId}")
    suspend fun patchForumMessage(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
        @Path("messageId") messageId: String,
        @Body body: UpdateTeamForumMessageBody,
    ): TeamForumMessageDto

    @DELETE("teams/{teamId}/forum/topics/{topicId}/messages/{messageId}")
    suspend fun deleteForumMessage(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
        @Path("messageId") messageId: String,
    ): OkResponse

    @POST("teams/{teamId}/forum/topics/{topicId}/messages/bulk-delete")
    suspend fun bulkDeleteForumMessages(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
        @Body body: BulkDeleteForumMessagesBody,
    ): OkResponse

    @POST("teams/{teamId}/forum/topics/{topicId}/messages/{messageId}/forward")
    suspend fun forwardForumMessage(
        @Path("teamId") teamId: String,
        @Path("topicId") topicId: String,
        @Path("messageId") messageId: String,
    ): TeamForumMessageDto
}
