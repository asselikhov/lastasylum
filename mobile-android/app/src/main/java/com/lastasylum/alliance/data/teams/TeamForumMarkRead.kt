package com.lastasylum.alliance.data.teams

import com.lastasylum.alliance.data.effectiveUnreadCount
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Bulk forum read cursors for overlay / team inbox. */
object TeamForumMarkRead {
    private const val CONCURRENCY = 5

    suspend fun markAllTopicsRead(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
    ) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        val topics = teamsRepository.listForumTopics(tid).getOrElse { return }
        val unreadTopics = topics.filter { topic ->
            val local = forumPrefs.getLastReadMessageId(tid, topic.id)
            effectiveUnreadCount(
                serverUnread = topic.unreadCount,
                lastReadMessageId = topic.lastReadMessageId,
                localLastReadMessageId = local,
            ) > 0
        }
        if (unreadTopics.isEmpty()) return
        val gate = Semaphore(CONCURRENCY)
        coroutineScope {
            unreadTopics.map { topic ->
                async {
                    gate.withPermit {
                        markTopicReadToLatest(
                            teamsRepository = teamsRepository,
                            forumPrefs = forumPrefs,
                            teamId = tid,
                            topicId = topic.id,
                        )
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun markTopicReadToLatest(
        teamsRepository: TeamsRepository,
        forumPrefs: TeamForumPreferences,
        teamId: String,
        topicId: String,
    ) {
        val tid = teamId.trim()
        val tpid = topicId.trim()
        if (tid.isEmpty() || tpid.isEmpty()) return
        val newestId = teamsRepository.listForumMessages(tid, tpid, before = null, limit = 1)
            .getOrNull()
            ?.firstOrNull()
            ?.id
            ?.trim()
            .orEmpty()
        if (newestId.isEmpty()) return
        teamsRepository.markForumTopicRead(tid, tpid, newestId)
        forumPrefs.setLastReadMessageId(tid, tpid, newestId)
    }
}
