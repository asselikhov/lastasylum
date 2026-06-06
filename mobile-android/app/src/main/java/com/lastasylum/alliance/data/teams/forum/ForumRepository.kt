package com.lastasylum.alliance.data.teams.forum

import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.chat.store.ChatStoreJson
import com.lastasylum.alliance.data.chat.store.ForumMessageEntity
import com.lastasylum.alliance.data.chat.store.ForumTopicEntity
import com.lastasylum.alliance.data.chat.store.SquadRelayDatabase
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.telemetry.DeliveryLatencyTracker
import com.lastasylum.alliance.data.telemetry.LatencySpanType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
            }

            val result = teamsRepository.listForumTopics(tid).map { topics ->
                upsertTopics(uid, tid, topics)
                launchDiskCache.saveForumTopics(uid, tid, topics)
                topics
            }
            result
        }

    suspend fun onForumSocketMessage(
        userId: String,
        teamId: String,
        topicId: String,
        message: TeamForumMessageDto,
        correlationId: String = message.id,
    ) {
        latencyTracker?.endSpanByCorrelation(LatencySpanType.ForumSendToSocket, correlationId, "ok")
        upsertMessages(userId, teamId, topicId, listOf(message))
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

    fun teams(): TeamsRepository = teamsRepository
}
