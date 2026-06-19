package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.teams.forum.ForumRepository
import com.lastasylum.alliance.overlay.OverlayInboxBadgeCoordinator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Bulk forum read cursors for overlay / team inbox. */
object TeamForumMarkRead {
    private const val CONCURRENCY = 5

    data class MarkAllTopicsReadResult(
        val markedTopics: Map<String, String>,
    )

    suspend fun markAllTopicsRead(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        optimisticFloorByTopic: Map<String, Int> = emptyMap(),
    ): MarkAllTopicsReadResult {
        return markAllTopicsReadViaTeams(teamsRepository, forumPrefs, teamId, optimisticFloorByTopic)
    }

    suspend fun markAllTopicsRead(
        forumRepository: ForumRepository,
        userId: String,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        optimisticFloorByTopic: Map<String, Int> = emptyMap(),
    ): MarkAllTopicsReadResult {
        val tid = teamId.trim()
        val uid = userId.trim()
        if (tid.isEmpty()) return MarkAllTopicsReadResult(emptyMap())
        val topics = forumRepository.syncTopics(uid, tid).getOrElse { return MarkAllTopicsReadResult(emptyMap()) }
        return markAllTopicsReadFromList(
            forumRepository,
            uid,
            forumPrefs,
            tid,
            topics,
            optimisticFloorByTopic,
        )
    }

    private suspend fun markAllTopicsReadViaTeams(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        optimisticFloorByTopic: Map<String, Int>,
    ): MarkAllTopicsReadResult {
        val tid = teamId.trim()
        if (tid.isEmpty()) return MarkAllTopicsReadResult(emptyMap())
        val topics = teamsRepository.listForumTopics(tid).getOrElse { return MarkAllTopicsReadResult(emptyMap()) }
        return markAllTopicsReadFromTeams(teamsRepository, forumPrefs, tid, topics, optimisticFloorByTopic)
    }

    private fun displayedTopicUnread(
        topic: TeamForumTopicDto,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        optimisticFloorByTopic: Map<String, Int>,
    ): Int {
        val local = forumPrefs.getLastReadMessageId(teamId, topic.id)
        val floor = optimisticFloorByTopic[topic.id] ?: 0
        return TeamInboxUnread.displayedForumTopicUnread(
            topic = topic,
            localLastReadMessageId = local,
            optimisticFloor = floor,
        )
    }

    private suspend fun markAllTopicsReadFromList(
        forumRepository: ForumRepository,
        userId: String,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        topics: List<TeamForumTopicDto>,
        optimisticFloorByTopic: Map<String, Int>,
    ): MarkAllTopicsReadResult {
        val unreadTopics = topics.filter { topic ->
            displayedTopicUnread(topic, forumPrefs, teamId, optimisticFloorByTopic) > 0
        }
        if (unreadTopics.isEmpty()) return MarkAllTopicsReadResult(emptyMap())
        val marked = mutableMapOf<String, String>()
        val gate = Semaphore(CONCURRENCY)
        coroutineScope {
            unreadTopics.map { topic ->
                async {
                    gate.withPermit {
                        val messageId = markTopicReadToLatest(
                            forumRepository = forumRepository,
                            userId = userId,
                            forumPrefs = forumPrefs,
                            teamId = teamId,
                            topicId = topic.id,
                        )
                        if (messageId.isNotEmpty()) {
                            synchronized(marked) { marked[topic.id] = messageId }
                        }
                    }
                }
            }.awaitAll()
        }
        return MarkAllTopicsReadResult(marked.toMap())
    }

    private suspend fun markAllTopicsReadFromTeams(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        topics: List<TeamForumTopicDto>,
        optimisticFloorByTopic: Map<String, Int>,
    ): MarkAllTopicsReadResult {
        val unreadTopics = topics.filter { topic ->
            displayedTopicUnread(topic, forumPrefs, teamId, optimisticFloorByTopic) > 0
        }
        if (unreadTopics.isEmpty()) return MarkAllTopicsReadResult(emptyMap())
        val marked = mutableMapOf<String, String>()
        val gate = Semaphore(CONCURRENCY)
        coroutineScope {
            unreadTopics.map { topic ->
                async {
                    gate.withPermit {
                        val messageId = markTopicReadToLatest(
                            teamsRepository = teamsRepository,
                            forumPrefs = forumPrefs,
                            teamId = teamId,
                            topicId = topic.id,
                        )
                        if (messageId.isNotEmpty()) {
                            synchronized(marked) { marked[topic.id] = messageId }
                        }
                    }
                }
            }.awaitAll()
        }
        return MarkAllTopicsReadResult(marked.toMap())
    }

    suspend fun markTopicReadToLatest(
        forumRepository: ForumRepository,
        userId: String,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        topicId: String,
    ): String {
        val tid = teamId.trim()
        val tpid = topicId.trim()
        if (tid.isEmpty() || tpid.isEmpty()) return ""
        val newestId = forumRepository.listForumMessages(userId, tid, tpid, before = null, limit = 1)
            .getOrNull()
            ?.firstOrNull()
            ?.id
            ?.trim()
            .orEmpty()
        if (newestId.isEmpty()) return ""
        forumRepository.markForumTopicRead(tid, tpid, newestId)
        forumPrefs.setLastReadMessageId(tid, tpid, newestId)
        return newestId
    }

    suspend fun afterTopicMarkedRead(
        forumRepository: ForumRepository,
        userId: String,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        topicId: String,
        messageId: String,
        topicFallback: TeamForumTopicDto? = null,
        onInboxChanged: () -> Unit = {},
        inboxBadgeCoordinator: OverlayInboxBadgeCoordinator? = null,
    ) {
        val tid = teamId.trim()
        val tpid = topicId.trim()
        val mid = messageId.trim()
        if (tid.isEmpty() || tpid.isEmpty() || mid.isEmpty()) return
        forumRepository.patchTopicReadLocally(userId, tid, tpid, mid, topicFallback)
        inboxBadgeCoordinator?.onForumMarkedReadLocally()
        onInboxChanged()
        com.lastasylum.alliance.overlay.CombatOverlayService.refreshOverlayForumBadgeFromApp()
    }

    suspend fun afterAllTopicsMarkedRead(
        forumRepository: ForumRepository,
        userId: String,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        markedTopics: Map<String, String>,
        topicFallbackById: Map<String, TeamForumTopicDto> = emptyMap(),
        onInboxChanged: () -> Unit = {},
        inboxBadgeCoordinator: OverlayInboxBadgeCoordinator? = null,
    ) {
        if (markedTopics.isEmpty()) return
        markedTopics.forEach { (topicId, messageId) ->
            afterTopicMarkedRead(
                forumRepository = forumRepository,
                userId = userId,
                forumPrefs = forumPrefs,
                teamId = teamId,
                topicId = topicId,
                messageId = messageId,
                topicFallback = topicFallbackById[topicId],
                onInboxChanged = onInboxChanged,
                inboxBadgeCoordinator = inboxBadgeCoordinator,
            )
        }
    }

    suspend fun markTopicReadToLatest(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        topicId: String,
    ): String {
        val tid = teamId.trim()
        val tpid = topicId.trim()
        if (tid.isEmpty() || tpid.isEmpty()) return ""
        val newestId = teamsRepository.listForumMessages(tid, tpid, before = null, limit = 1)
            .getOrNull()
            ?.firstOrNull()
            ?.id
            ?.trim()
            .orEmpty()
        if (newestId.isEmpty()) return ""
        teamsRepository.markForumTopicRead(tid, tpid, newestId)
        forumPrefs.setLastReadMessageId(tid, tpid, newestId)
        return newestId
    }
}
