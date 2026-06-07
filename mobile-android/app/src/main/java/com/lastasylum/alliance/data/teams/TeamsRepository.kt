package com.lastasylum.alliance.data.teams

import android.util.Log
import com.lastasylum.alliance.data.chat.ToggleReactionRequest
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class TeamsRepository(
    private val teamsApi: TeamsApi,
) {
    private val forumTopicsDedup = InFlightDedup<String, List<TeamForumTopicDto>>()
    private val forumMessagesDedup = InFlightDedup<String, List<TeamForumMessageDto>>()
    private val teamNewsDedup = InFlightDedup<String, TeamNewsListPageDto>()
    private val overlayPresenceDedup = InFlightDedup<String, TeamOverlayPresenceDto>()
    private val forumTopicsResultCache = mutableMapOf<String, ForumTopicsCacheEntry>()
    private val forumMessagesResultCache = mutableMapOf<String, ForumMessagesCacheEntry>()
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

    suspend fun getTeamOverlayPresence(teamId: String): Result<TeamOverlayPresenceDto> {
        val tid = teamId.trim()
        if (tid.isEmpty()) return Result.failure(IllegalArgumentException("team_required"))
        return overlayPresenceDedup.run(tid) {
            runCatching { teamsApi.getTeamOverlayPresence(tid) }
        }
    }

    suspend fun submitJoinRequest(teamId: String): Result<SubmitJoinResponse> =
        runCatching {
            teamsApi.submitJoinRequest(teamId).requireTeamsSuccess()
                ?: SubmitJoinResponse()
        }

    suspend fun acceptJoinRequest(requestId: String): Result<Unit> =
        runCatching {
            teamsApi.acceptJoinRequest(requestId).requireTeamsSuccess()
        }

    suspend fun rejectJoinRequest(requestId: String): Result<Unit> =
        runCatching {
            teamsApi.rejectJoinRequest(requestId).requireTeamsSuccess()
        }

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

    suspend fun getTeamInboxBadges(
        teamId: String,
        newsAfter: String? = null,
    ): Result<TeamInboxBadgesDto> =
        runCatching { teamsApi.getTeamInboxBadges(teamId, newsAfter) }

    suspend fun getTeamNewsReadCursor(teamId: String): Result<TeamNewsReadCursorDto> =
        runCatching { teamsApi.getTeamNewsReadCursor(teamId) }

    suspend fun advanceTeamNewsReadCursor(
        teamId: String,
        createdAt: String,
    ): Result<TeamNewsReadCursorDto> =
        runCatching {
            teamsApi.advanceTeamNewsReadCursor(
                teamId,
                AdvanceTeamNewsReadCursorBody(createdAt = createdAt),
            )
        }

    suspend fun listTeamNews(
        teamId: String,
        cursor: String? = null,
        limit: Int = 30,
    ): Result<TeamNewsListPageDto> {
        val key = "${teamId.trim()}|${cursor?.trim().orEmpty()}|$limit"
        return teamNewsDedup.run(key) {
            Log.d(PERF_TAG, "listTeamNews network key=$key")
            runCatching { teamsApi.listTeamNews(teamId, cursor, limit) }
        }
    }

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

    suspend fun uploadForumImage(
        teamId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> =
        runCatching {
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            teamsApi.uploadForumAttachment(teamId, part)
        }

    suspend fun uploadForumImageFromFile(
        teamId: String,
        file: File,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> =
        runCatching {
            val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            teamsApi.uploadForumAttachment(teamId, part)
        }

    suspend fun uploadForumFile(
        teamId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> =
        runCatching {
            val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            teamsApi.uploadForumFileAttachment(teamId, part)
        }

    suspend fun uploadForumFileFromFile(
        teamId: String,
        file: File,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> =
        runCatching {
            val body = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", fileName, body)
            teamsApi.uploadForumFileAttachment(teamId, part)
        }

    fun invalidateForumTopicsCache(teamId: String) {
        val key = teamId.trim()
        synchronized(forumTopicsResultCache) {
            forumTopicsResultCache.remove(key)
        }
    }

    fun patchForumTopicReadInCache(teamId: String, topicId: String, messageId: String) {
        val key = teamId.trim()
        val tpid = topicId.trim()
        val mid = messageId.trim()
        if (key.isEmpty() || tpid.isEmpty() || mid.isEmpty()) return
        synchronized(forumTopicsResultCache) {
            val entry = forumTopicsResultCache[key] ?: return
            val idx = entry.topics.indexOfFirst { it.id == tpid }
            if (idx < 0) return
            val updated = entry.topics.toMutableList()
            updated[idx] = updated[idx].copy(unreadCount = 0, lastReadMessageId = mid)
            forumTopicsResultCache[key] = entry.copy(
                topics = updated,
                fetchedAtMs = System.currentTimeMillis(),
            )
        }
    }

    fun invalidateForumMessagesCache(teamId: String, topicId: String) {
        val prefix = "${teamId.trim()}|${topicId.trim()}|"
        synchronized(forumMessagesResultCache) {
            forumMessagesResultCache.keys.removeAll { it.startsWith(prefix) }
        }
    }

    suspend fun listForumTopics(
        teamId: String,
        bypassCache: Boolean = false,
        view: String = "list",
    ): Result<List<TeamForumTopicDto>> {
        val key = teamId.trim()
        val now = System.currentTimeMillis()
        if (!bypassCache) {
            synchronized(forumTopicsResultCache) {
                forumTopicsResultCache[key]?.let { entry ->
                    if (now - entry.fetchedAtMs < FORUM_TOPICS_TTL_MS) {
                        Log.d(PERF_TAG, "listForumTopics cache hit teamId=$key")
                        return Result.success(entry.topics)
                    }
                }
            }
        }
        return forumTopicsDedup.run("$key|$view") {
            Log.d(PERF_TAG, "listForumTopics network teamId=$key view=$view")
            runCatching { teamsApi.listForumTopics(key, view = view) }
                .also { result ->
                    result.onSuccess { topics ->
                        synchronized(forumTopicsResultCache) {
                            forumTopicsResultCache[key] = ForumTopicsCacheEntry(
                                topics = topics,
                                fetchedAtMs = System.currentTimeMillis(),
                            )
                        }
                    }
                }
        }
    }

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

    suspend fun pinForumTopicMessage(
        teamId: String,
        topicId: String,
        messageId: String?,
    ): Result<TeamForumTopicDto> =
        runCatching {
            teamsApi.pinForumTopicMessage(
                teamId,
                topicId,
                PinTeamForumTopicRequest(messageId = messageId),
            )
        }.also { result ->
            if (result.isSuccess) invalidateForumTopicsCache(teamId)
        }

    suspend fun markForumTopicRead(
        teamId: String,
        topicId: String,
        messageId: String,
    ): Result<Unit> =
        runCatching {
            teamsApi.markForumTopicRead(
                teamId,
                topicId,
                MarkTeamForumTopicReadBody(messageId = messageId),
            )
            Unit
        }.also { result ->
            if (result.isSuccess) patchForumTopicReadInCache(teamId, topicId, messageId)
        }

    suspend fun getForumPeerReadCursor(
        teamId: String,
        topicId: String,
    ): Result<String?> =
        runCatching {
            teamsApi.getForumPeerReadCursor(teamId, topicId).messageId?.trim()?.takeIf { it.isNotEmpty() }
        }

    suspend fun listForumMessages(
        teamId: String,
        topicId: String,
        before: String? = null,
        limit: Int = 50,
        bypassCache: Boolean = false,
    ): Result<List<TeamForumMessageDto>> {
        val key = "${teamId.trim()}|${topicId.trim()}|${before?.trim().orEmpty()}|$limit"
        val now = System.currentTimeMillis()
        if (!bypassCache && before.isNullOrBlank()) {
            synchronized(forumMessagesResultCache) {
                forumMessagesResultCache[key]?.let { entry ->
                    if (now - entry.fetchedAtMs < FORUM_MESSAGES_TTL_MS) {
                        Log.d(PERF_TAG, "listForumMessages cache hit key=$key")
                        return Result.success(entry.messages)
                    }
                }
            }
        }
        return forumMessagesDedup.run(key) {
            Log.d(PERF_TAG, "listForumMessages network key=$key")
            runCatching { teamsApi.listForumMessages(teamId, topicId, before, limit) }
                .also { result ->
                    if (before.isNullOrBlank()) {
                        result.onSuccess { messages ->
                            synchronized(forumMessagesResultCache) {
                                forumMessagesResultCache[key] = ForumMessagesCacheEntry(
                                    messages = messages,
                                    fetchedAtMs = System.currentTimeMillis(),
                                )
                            }
                        }
                    }
                }
        }
    }

    fun peekCachedForumTopics(teamId: String): List<TeamForumTopicDto>? {
        val key = teamId.trim()
        val now = System.currentTimeMillis()
        synchronized(forumTopicsResultCache) {
            forumTopicsResultCache[key]?.let { entry ->
                if (now - entry.fetchedAtMs < FORUM_TOPICS_TTL_MS) {
                    return entry.topics
                }
            }
        }
        return null
    }

    suspend fun postForumMessage(
        teamId: String,
        topicId: String,
        text: String,
        replyToMessageId: String? = null,
        imageFileId: String? = null,
        imageFileIds: List<String>? = null,
        fileFileId: String? = null,
        clientMessageId: String? = null,
    ): Result<TeamForumMessageDto> =
        postForumMessageWithRetries(
            teamId = teamId,
            topicId = topicId,
            text = text,
            replyToMessageId = replyToMessageId,
            imageFileId = imageFileId,
            imageFileIds = imageFileIds,
            fileFileId = fileFileId,
            clientMessageId = clientMessageId ?: java.util.UUID.randomUUID().toString(),
        )

    suspend fun postForumMessageWithRetries(
        teamId: String,
        topicId: String,
        text: String,
        replyToMessageId: String? = null,
        imageFileId: String? = null,
        imageFileIds: List<String>? = null,
        fileFileId: String? = null,
        clientMessageId: String = java.util.UUID.randomUUID().toString(),
    ): Result<TeamForumMessageDto> {
        var last: Throwable? = null
        repeat(3) { attempt ->
            val result = runCatching {
                val trimmed = text.trim()
                val img = imageFileId?.trim()?.takeIf { it.isNotEmpty() }
                val replyId = replyToMessageId?.trim()?.takeIf { it.isNotEmpty() }
                val imgs = imageFileIds?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct()
                val fileId = fileFileId?.trim()?.takeIf { it.isNotEmpty() }
                teamsApi.postForumMessage(
                    teamId,
                    topicId,
                    CreateTeamForumMessageBody(
                        text = trimmed,
                        replyToMessageId = replyId,
                        imageFileIds = imgs?.takeIf { it.isNotEmpty() },
                        imageFileId = img,
                        fileFileId = fileId,
                        clientMessageId = clientMessageId,
                    ),
                )
            }
            if (result.isSuccess) {
                invalidateForumMessagesCache(teamId, topicId)
                return result
            }
            last = result.exceptionOrNull()
            if (attempt < 2) {
                delay(listOf(120L, 350L)[attempt])
            }
        }
        return Result.failure(last ?: IllegalStateException("forum_send_failed"))
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
        }.also { result ->
            if (result.isSuccess) invalidateForumMessagesCache(teamId, topicId)
        }

    suspend fun deleteForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): Result<Unit> =
        runCatching {
            teamsApi.deleteForumMessage(teamId, topicId, messageId).let { }
        }.also { result ->
            if (result.isSuccess) invalidateForumMessagesCache(teamId, topicId)
        }

    suspend fun bulkDeleteForumMessages(
        teamId: String,
        topicId: String,
        messageIds: List<String>,
    ): Result<ForumBulkDeleteResponse> =
        runCatching {
            val ids = messageIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (ids.isEmpty()) {
                ForumBulkDeleteResponse(ok = true, deletedIds = emptyList(), pinChanged = null)
            } else {
                teamsApi.bulkDeleteForumMessages(
                    teamId,
                    topicId,
                    BulkDeleteForumMessagesBody(messageIds = ids),
                )
            }
        }.also { result ->
            if (result.isSuccess) invalidateForumMessagesCache(teamId, topicId)
        }

    suspend fun unpinOneForumTopicMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): Result<TeamForumTopicDto> =
        runCatching {
            teamsApi.unpinOneForumTopicMessage(teamId, topicId, messageId)
        }.also { result ->
            if (result.isSuccess) invalidateForumTopicsCache(teamId)
        }

    suspend fun forwardForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): Result<TeamForumMessageDto> =
        runCatching {
            teamsApi.forwardForumMessage(
                teamId,
                topicId,
                messageId,
            )
        }

    suspend fun toggleForumMessageReaction(
        teamId: String,
        topicId: String,
        messageId: String,
        emoji: String,
    ): Result<TeamForumMessageDto> =
        runCatching {
            teamsApi.toggleForumMessageReaction(
                teamId,
                topicId,
                messageId,
                ToggleReactionRequest(emoji = emoji),
            )
        }

    private data class ForumTopicsCacheEntry(
        val topics: List<TeamForumTopicDto>,
        val fetchedAtMs: Long,
    )

    private data class ForumMessagesCacheEntry(
        val messages: List<TeamForumMessageDto>,
        val fetchedAtMs: Long,
    )

    private companion object {
        const val PERF_TAG = "PerfDiag"
        const val FORUM_TOPICS_TTL_MS = 15_000L
        const val FORUM_MESSAGES_TTL_MS = 20_000L
    }
}
