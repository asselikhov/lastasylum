package com.lastasylum.alliance.data.teams.forum

import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.ui.teamforum.ForumTopicViewModelRegistry

/** Updates forum topic UI after [ForumOutboxSendWorker] / background outbox resume succeeds. */
object ForumOutboxUiBridge {
    fun onSendSuccess(entry: ForumOutboxEntry, sent: TeamForumMessageDto) {
        ForumTopicViewModelRegistry.onOutboxSendSuccess(
            teamId = entry.teamId,
            topicId = entry.topicId,
            clientMessageId = entry.clientMessageId,
            pendingMessageId = entry.pendingMessageId,
            sent = sent,
        )
    }
}
