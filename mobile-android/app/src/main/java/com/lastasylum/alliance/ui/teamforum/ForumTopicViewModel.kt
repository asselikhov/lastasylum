package com.lastasylum.alliance.ui.teamforum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.forum.ForumOutboxEntry
import com.lastasylum.alliance.data.teams.forum.ForumOutboxResumeScheduler
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.launch

/**
 * Forum topic send/outbox/mark-read coordination extracted from [TeamForumTopicScreen].
 */
class ForumTopicViewModel(app: Application) : AndroidViewModel(app) {
    private val container = AppContainer.from(app)
    val forumRepository get() = container.forumRepository
    val forumOutbox get() = container.forumOutbox

    val markReadCoalescer = ForumMarkReadCoalescer(viewModelScope)

    suspend fun persistOutbox(entry: ForumOutboxEntry) {
        forumOutbox.persist(entry)
    }

    suspend fun markOutboxSent(clientMessageId: String) {
        forumOutbox.markSent(clientMessageId)
    }

    suspend fun markOutboxFailed(clientMessageId: String, error: String, userId: String) {
        forumOutbox.markFailed(clientMessageId, error)
        scheduleOutboxResume(userId)
    }

    fun scheduleOutboxResume(userId: String) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        ForumOutboxResumeScheduler.schedule(getApplication(), uid)
    }

    fun resumePendingOutbox(
        userId: String,
        onConfirmed: (TeamForumMessageDto) -> Unit,
    ) {
        val uid = userId.trim()
        if (uid.isEmpty()) return
        viewModelScope.launch {
            forumOutbox.resumePendingSync(uid) { entry ->
                forumRepository.postForumMessageWithRetries(
                    userId = uid,
                    teamId = entry.teamId,
                    topicId = entry.topicId,
                    text = entry.text,
                    replyToMessageId = entry.replyToMessageId,
                    imageFileId = null,
                    imageFileIds = entry.imageFileIds,
                    fileFileId = entry.fileFileId,
                    clientMessageId = entry.clientMessageId,
                ).onSuccess { onConfirmed(it) }
                    .map { Unit }
            }
        }
    }

    override fun onCleared() {
        markReadCoalescer.clear()
        super.onCleared()
    }
}
