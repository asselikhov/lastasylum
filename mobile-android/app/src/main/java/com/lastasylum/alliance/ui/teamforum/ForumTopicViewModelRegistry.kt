package com.lastasylum.alliance.ui.teamforum

import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/** Bridges background forum outbox success to the bound [ForumTopicViewModel] for a topic. */
internal object ForumTopicViewModelRegistry {
    private data class TopicKey(val teamId: String, val topicId: String)

    private val byTopic = ConcurrentHashMap<TopicKey, WeakReference<ForumTopicViewModel>>()

    fun register(teamId: String, topicId: String, vm: ForumTopicViewModel) {
        val key = TopicKey(teamId.trim(), topicId.trim())
        if (key.teamId.isEmpty() || key.topicId.isEmpty()) return
        byTopic[key] = WeakReference(vm)
    }

    fun unregister(teamId: String, topicId: String, vm: ForumTopicViewModel) {
        val key = TopicKey(teamId.trim(), topicId.trim())
        byTopic.computeIfPresent(key) { _, ref ->
            if (ref.get() === vm) null else ref
        }
    }

    fun onOutboxSendSuccess(
        teamId: String,
        topicId: String,
        clientMessageId: String,
        pendingMessageId: String,
        sent: TeamForumMessageDto,
    ) {
        val key = TopicKey(teamId.trim(), topicId.trim())
        byTopic[key]?.get()?.onBackgroundOutboxConfirmed(
            clientMessageId = clientMessageId,
            pendingMessageId = pendingMessageId,
            sent = sent,
        )
    }

    suspend fun flushAllPendingMarkRead() {
        byTopic.values.mapNotNull { it.get() }.forEach { vm ->
            vm.markReadCoalescer.flushAndAwait()
        }
    }
}
