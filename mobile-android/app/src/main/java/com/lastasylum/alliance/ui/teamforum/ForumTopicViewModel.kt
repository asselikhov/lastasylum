package com.lastasylum.alliance.ui.teamforum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.forum.ForumOutboxEntry
import com.lastasylum.alliance.data.teams.forum.ForumOutboxResumeScheduler
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Forum topic send/outbox/mark-read coordination extracted from [TeamForumTopicScreen].
 */
class ForumTopicViewModel(app: Application) : AndroidViewModel(app) {
    private val container = AppContainer.from(app)
    val forumRepository get() = container.forumRepository
    val forumOutbox get() = container.forumOutbox

    val markReadCoalescer = ForumMarkReadCoalescer(viewModelScope)

    private val _outboxConfirmed = MutableSharedFlow<TeamForumMessageDto>(extraBufferCapacity = 8)
    val outboxConfirmed: SharedFlow<TeamForumMessageDto> = _outboxConfirmed.asSharedFlow()

    private var registeredTeamId: String? = null
    private var registeredTopicId: String? = null
    private var outboxResumeJob: Job? = null
    private var outboxConfirmedCollectJob: Job? = null

    fun bindTopicRegistry(teamId: String, topicId: String) {
        val tid = teamId.trim()
        val tpid = topicId.trim()
        if (tid.isEmpty() || tpid.isEmpty()) return
        registeredTeamId?.let { prevTeam ->
            registeredTopicId?.let { prevTopic ->
                ForumTopicViewModelRegistry.unregister(prevTeam, prevTopic, this)
            }
        }
        registeredTeamId = tid
        registeredTopicId = tpid
        ForumTopicViewModelRegistry.register(tid, tpid, this)
    }

    fun onBackgroundOutboxConfirmed(
        clientMessageId: String,
        pendingMessageId: String,
        sent: TeamForumMessageDto,
    ) {
        viewModelScope.launch {
            _outboxConfirmed.emit(sent.withClientMessageId(clientMessageId))
        }
    }

    private fun TeamForumMessageDto.withClientMessageId(clientMessageId: String): TeamForumMessageDto =
        if (this.clientMessageId?.trim() == clientMessageId.trim()) this
        else copy(clientMessageId = clientMessageId)

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
        outboxResumeJob?.cancel()
        outboxConfirmedCollectJob?.cancel()
        outboxResumeJob = viewModelScope.launch {
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
                )
            }
        }
        outboxConfirmedCollectJob = viewModelScope.launch {
            outboxConfirmed.collect { onConfirmed(it) }
        }
    }

    override fun onCleared() {
        outboxResumeJob?.cancel()
        outboxConfirmedCollectJob?.cancel()
        outboxResumeJob = null
        outboxConfirmedCollectJob = null
        registeredTeamId?.let { teamId ->
            registeredTopicId?.let { topicId ->
                ForumTopicViewModelRegistry.unregister(teamId, topicId, this)
            }
        }
        registeredTeamId = null
        registeredTopicId = null
        markReadCoalescer.clear()
        super.onCleared()
    }
}
