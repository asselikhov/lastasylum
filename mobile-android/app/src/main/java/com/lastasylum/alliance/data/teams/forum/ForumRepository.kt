package com.lastasylum.alliance.data.teams.forum

import android.net.Uri
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.store.ChatStoreJson
import com.lastasylum.alliance.data.chat.store.ForumMessageEntity
import com.lastasylum.alliance.data.chat.store.ForumTopicEntity
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.teams.ForumBulkDeleteResponse
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.teams.UploadedTeamNewsImageDto
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.data.telemetry.LatencySpanType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Single forum I/O facade: Room + disk + TeamsRepository HTTP + latency.
 */
class ForumRepository(
    private val db: SquadRelayDatabase,
    private val teamsRepository: TeamsRepository,
    private val launchDiskCache: LaunchDiskCache,
    private val forumPrefs: TeamForumPreferences,
    private val latencyTracker: DeliveryLatencyTracker? = null,
) {
    private val topicDao get() = db.forumTopicDao()
    private val messageDao get() = db.forumMessageDao()
    private val cursorDao get() = db.forumReadCursorDao()

    fun observeTopics(userId: String, teamId: String): Flow<List<TeamForumTopicDto>> =
        topicDao.observeTeam(userId, teamId.trim()).map { rows ->
            rows.mapNotNull { ChatStoreJson.forumTopicFromJson(it.payloadJson) }
        }

    fun observeMessages(userId: String, teamId: String, topicId: String): Flow<List<TeamForumMessageDto>> =
        messageDao.observeTopic(userId, teamId.trim(), topicId.trim()).map { rows ->
            rows.mapNotNull { ChatStoreJson.forumMessageFromJson(it.payloadJson) }
        }

    suspend fun syncTopics(userId: String, teamId: String, bypassCache: Boolean = false): Result<List<TeamForumTopicDto>> =
        withContext(Dispatchers.IO) {
            val uid = userId.trim()
            val tid = teamId.trim()
            if (uid.isEmpty() || tid.isEmpty()) return@withContext Result.success(emptyList())

            if (!bypassCache) {
                launchDiskCache.loadForumTopics(uid, tid)?.let { cached ->
                    upsertTopics(uid, tid, cached)
                    return@withContext Result.success(cached)
                }
                teamsRepository.peekCachedForumTopics(tid)?.let { cached ->
                    upsertTopics(uid, tid, cached)
                    return@withContext Result.success(cached)
                }
            }

            teamsRepository.listForumTopics(tid, bypassCache = bypassCache).map { topics ->
                upsertTopics(uid, tid, topics)
                launchDiskCache.saveForumTopics(uid, tid, topics)
                topics
            }
        }

    suspend fun onForumSocketMessage(
        userId: String,
        teamId: String,
        topicId: String,
        message: TeamForumMessageDto,
        correlationId: String = message.id,
    ) = withContext(Dispatchers.IO) {
        latencyTracker?.endSpanByCorrelation(LatencySpanType.ForumSendToSocket, correlationId, "ok")
        upsertMessages(userId, teamId, topicId, listOf(message))
        patchTopicOnSocketMessage(userId, teamId, topicId, message)
    }

    /** Keep topic row metadata in sync with realtime messages (messageCount / unread / last activity). */
    private suspend fun patchTopicOnSocketMessage(
        userId: String,
        teamId: String,
        topicId: String,
        message: TeamForumMessageDto,
    ) {
        val uid = userId.trim()
        val tid = teamId.trim()
        val tpid = topicId.trim()
        if (uid.isEmpty() || tid.isEmpty() || tpid.isEmpty()) return

        val existing = topicDao.getTopic(uid, tid, tpid)
            ?.let { ChatStoreJson.forumTopicFromJson(it.payloadJson) }
        val isOwn = message.senderUserId.trim() == uid
        val base = existing ?: return
        val patched = base.copy(
            messageCount = base.messageCount + 1,
            unreadCount = if (isOwn) base.unreadCount else base.unreadCount + 1,
            lastMessageAt = message.createdAt,
            lastMessageSenderUserId = message.senderUserId,
            lastMessageSenderUsername = message.senderUsername,
            lastMessageSenderAvatarRelativeUrl = message.senderAvatarRelativeUrl,
            updatedAt = message.updatedAt,
        )
        upsertTopics(uid, tid, listOf(patched))
        launchDiskCache.loadForumTopics(uid, tid)?.toMutableList()?.let { cached ->
            val idx = cached.indexOfFirst { it.id == tpid }
            if (idx >= 0) {
                cached[idx] = patched
            } else {
                cached.add(patched)
            }
            launchDiskCache.saveForumTopics(uid, tid, cached)
        }
    }

    suspend fun upsertTopics(userId: String, teamId: String, topics: List<TeamForumTopicDto>) {
        val now = System.currentTimeMillis()
        topicDao.upsertAll(
            topics.map { topic ->
                ForumTopicEntity(
                    teamId = teamId.trim(),
                    topicId = topic.id,
                    userId = userId.trim(),
                    payloadJson = ChatStoreJson.forumTopicToJson(topic),
                    syncedAtMs = now,
                )
            },
        )
    }

    suspend fun upsertMessages(
        userId: String,
        teamId: String,
        topicId: String,
        messages: List<TeamForumMessageDto>,
    ) {
        val uid = userId.trim()
        val tid = teamId.trim()
        val topic = topicId.trim()
        if (uid.isEmpty() || tid.isEmpty() || topic.isEmpty() || messages.isEmpty()) return
        messageDao.upsertAll(
            messages.map { msg ->
                ForumMessageEntity(
                    teamId = tid,
                    topicId = topic,
                    messageId = msg.id,
                    userId = uid,
                    payloadJson = ChatStoreJson.forumMessageToJson(msg),
                    createdAtMs = ChatStoreJson.forumMessageCreatedAtMs(msg),
                )
            },
        )
    }

    suspend fun loadMessagesFromDisk(
        userId: String,
        teamId: String,
        topicId: String,
    ): List<TeamForumMessageDto>? =
        launchDiskCache.loadForumMessages(userId, teamId, topicId)?.messages

    suspend fun setTopicReadCursor(userId: String, teamId: String, topicId: String, messageId: String) {
        forumPrefs.setLastReadMessageId(teamId, topicId, messageId)
        cursorDao.upsert(
            com.lastasylum.alliance.data.chat.store.ForumReadCursorEntity(
                teamId = teamId.trim(),
                topicId = topicId.trim(),
                userId = userId.trim(),
                lastReadMessageId = messageId.trim(),
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    /** Zero unread locally after mark-read so list tab + Room observe update immediately. */
    suspend fun patchTopicReadLocally(
        userId: String,
        teamId: String,
        topicId: String,
        messageId: String,
        topicFallback: TeamForumTopicDto? = null,
    ) = withContext(Dispatchers.IO) {
        val uid = userId.trim()
        val tid = teamId.trim()
        val tpid = topicId.trim()
        val mid = messageId.trim()
        if (uid.isEmpty() || tid.isEmpty() || tpid.isEmpty() || mid.isEmpty()) return@withContext

        setTopicReadCursor(uid, tid, tpid, mid)

        val cached = launchDiskCache.loadForumTopics(uid, tid)?.toMutableList()
        val patched = when {
            cached != null -> {
                val idx = cached.indexOfFirst { it.id == tpid }
                if (idx >= 0) {
                    cached[idx] = cached[idx].copy(unreadCount = 0, lastReadMessageId = mid)
                } else {
                    topicFallback?.copy(unreadCount = 0, lastReadMessageId = mid)?.also { cached.add(it) }
                }
                cached
            }
            topicFallback != null -> listOf(topicFallback.copy(unreadCount = 0, lastReadMessageId = mid))
            else -> null
        } ?: return@withContext

        launchDiskCache.saveForumTopics(uid, tid, patched)
        val row = patched.firstOrNull { it.id == tpid } ?: return@withContext
        upsertTopics(uid, tid, listOf(row))
    }

    suspend fun uploadForumImage(
        teamId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> = teamsRepository.uploadForumImage(teamId, bytes, fileName, mimeType)

    suspend fun uploadForumImageFromFile(
        teamId: String,
        file: java.io.File,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> =
        teamsRepository.uploadForumImageFromFile(teamId, file, fileName, mimeType)

    suspend fun uploadForumFile(
        teamId: String,
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> = teamsRepository.uploadForumFile(teamId, bytes, fileName, mimeType)

    suspend fun uploadForumFileFromFile(
        teamId: String,
        file: java.io.File,
        fileName: String,
        mimeType: String,
    ): Result<UploadedTeamNewsImageDto> =
        teamsRepository.uploadForumFileFromFile(teamId, file, fileName, mimeType)

    suspend fun createForumTopic(teamId: String, title: String): Result<TeamForumTopicDto> =
        teamsRepository.createForumTopic(teamId, title)

    suspend fun updateForumTopic(teamId: String, topicId: String, title: String): Result<TeamForumTopicDto> =
        teamsRepository.updateForumTopic(teamId, topicId, title)

    suspend fun deleteForumTopic(teamId: String, topicId: String): Result<Unit> =
        teamsRepository.deleteForumTopic(teamId, topicId)

    suspend fun pinForumTopicMessage(
        teamId: String,
        topicId: String,
        messageId: String?,
    ): Result<TeamForumTopicDto> = teamsRepository.pinForumTopicMessage(teamId, topicId, messageId)

    suspend fun unpinOneForumTopicMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): Result<TeamForumTopicDto> = teamsRepository.unpinOneForumTopicMessage(teamId, topicId, messageId)

    suspend fun markForumTopicRead(teamId: String, topicId: String, messageId: String): Result<Unit> =
        teamsRepository.markForumTopicRead(teamId, topicId, messageId)

    suspend fun getForumPeerReadCursor(teamId: String, topicId: String): Result<String?> =
        teamsRepository.getForumPeerReadCursor(teamId, topicId)

    suspend fun listForumMessages(
        userId: String,
        teamId: String,
        topicId: String,
        before: String? = null,
        limit: Int = 50,
        bypassCache: Boolean = false,
    ): Result<List<TeamForumMessageDto>> = withContext(Dispatchers.IO) {
        val result = teamsRepository.listForumMessages(teamId, topicId, before, limit, bypassCache)
        result.onSuccess { messages ->
            if (before.isNullOrBlank() && userId.isNotBlank() && messages.isNotEmpty()) {
                upsertMessages(userId, teamId, topicId, messages)
            }
        }
        result
    }

    suspend fun syncMessages(
        userId: String,
        teamId: String,
        topicId: String,
        force: Boolean = false,
    ): Result<List<TeamForumMessageDto>> =
        listForumMessages(userId, teamId, topicId, bypassCache = force)

    suspend fun postForumMessageWithRetries(
        userId: String,
        teamId: String,
        topicId: String,
        text: String,
        replyToMessageId: String? = null,
        imageFileId: String? = null,
        imageFileIds: List<String>? = null,
        fileFileId: String? = null,
        clientMessageId: String = java.util.UUID.randomUUID().toString(),
    ): Result<TeamForumMessageDto> {
        latencyTracker?.startSpan(LatencySpanType.ForumSendToSocket, clientMessageId)
        return teamsRepository.postForumMessageWithRetries(
            teamId = teamId,
            topicId = topicId,
            text = text,
            replyToMessageId = replyToMessageId,
            imageFileId = imageFileId,
            imageFileIds = imageFileIds,
            fileFileId = fileFileId,
            clientMessageId = clientMessageId,
        ).also { result ->
            result.onSuccess { msg ->
                if (userId.isNotBlank()) upsertMessages(userId, teamId, topicId, listOf(msg))
            }
        }
    }

    suspend fun patchForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
        text: String,
    ): Result<TeamForumMessageDto> = teamsRepository.patchForumMessage(teamId, topicId, messageId, text)

    suspend fun deleteForumMessage(teamId: String, topicId: String, messageId: String): Result<Unit> =
        teamsRepository.deleteForumMessage(teamId, topicId, messageId)

    suspend fun bulkDeleteForumMessages(
        teamId: String,
        topicId: String,
        messageIds: List<String>,
    ): Result<ForumBulkDeleteResponse> = teamsRepository.bulkDeleteForumMessages(teamId, topicId, messageIds)

    suspend fun forwardForumMessage(
        teamId: String,
        topicId: String,
        messageId: String,
    ): Result<TeamForumMessageDto> = teamsRepository.forwardForumMessage(teamId, topicId, messageId)

    suspend fun toggleForumMessageReaction(
        teamId: String,
        topicId: String,
        messageId: String,
        emoji: String,
    ): Result<TeamForumMessageDto> = teamsRepository.toggleForumMessageReaction(teamId, topicId, messageId, emoji)

    fun invalidateForumTopicsCache(teamId: String) = teamsRepository.invalidateForumTopicsCache(teamId)

    fun invalidateForumMessagesCache(teamId: String, topicId: String) =
        teamsRepository.invalidateForumMessagesCache(teamId, topicId)

    fun teams(): TeamsRepository = teamsRepository
}
