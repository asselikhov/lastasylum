package com.lastasylum.alliance.ui.teamforum

import com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent
import com.lastasylum.alliance.data.teams.TeamForumTopicUnreadEvent
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/** Bridges overlay socket inbox events to the bound [ForumListViewModel] for a team. */
internal object ForumListViewModelRegistry {
    private val byTeam = ConcurrentHashMap<String, WeakReference<ForumListViewModel>>()

    fun register(teamId: String, vm: ForumListViewModel) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        byTeam[tid] = WeakReference(vm)
    }

    fun unregister(teamId: String, vm: ForumListViewModel) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        byTeam.computeIfPresent(tid) { _, ref ->
            if (ref.get() === vm) null else ref
        }
    }

    fun dispatchTopicActivity(event: TeamForumTopicActivityEvent) {
        byTeam[event.teamId.trim()]?.get()?.applyTopicActivity(event)
    }

    fun dispatchTopicUnread(event: TeamForumTopicUnreadEvent) {
        byTeam[event.teamId.trim()]?.get()?.applyTopicUnreadSnapshot(
            topicId = event.topicId,
            unreadCount = event.unreadCount,
            lastReadMessageId = event.lastReadMessageId,
        )
    }

    fun dispatchTopicReadLocal(teamId: String, topicId: String, messageId: String) {
        byTeam[teamId.trim()]?.get()?.applyTopicReadLocal(topicId, messageId)
    }
}
