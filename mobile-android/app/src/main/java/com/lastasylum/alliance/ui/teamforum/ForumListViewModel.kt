package com.lastasylum.alliance.ui.teamforum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.teams.TeamForumTopicActivityEvent
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamForumTopicPinChangedEvent
import com.lastasylum.alliance.data.teams.forum.ForumRepository
import com.lastasylum.alliance.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForumListUiState(
    val teamId: String = "",
    val topics: List<TeamForumTopicDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val optimisticUnreadFloorByTopic: Map<String, Int> = emptyMap(),
)

class ForumListViewModel(
    application: Application,
    private val forumRepository: ForumRepository,
    private val currentUserId: String,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ForumListUiState())
    val state: StateFlow<ForumListUiState> = _state.asStateFlow()

    private var reloadJob: Job? = null
    private var topicActivityJob: Job? = null
    private var openTopicId: String? = null
    private val lastActivityMessageIdByTopic = mutableMapOf<String, String>()

    fun setOpenTopicId(topicId: String?) {
        openTopicId = topicId?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun bindTeam(teamId: String) {
        val tid = teamId.trim()
        if (tid.isEmpty() || _state.value.teamId == tid) return
        _state.value = ForumListUiState(teamId = tid, loading = true)
        viewModelScope.launch {
            forumRepository.observeTopics(currentUserId, tid).collect { topics ->
                _state.update { it.copy(topics = topics, loading = false) }
            }
        }
        reload(force = false)
    }

    fun reload(force: Boolean = false) {
        val teamId = _state.value.teamId.trim()
        if (teamId.isEmpty()) return
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            forumRepository.syncTopics(currentUserId, teamId, bypassCache = force)
                .onSuccess { topics ->
                    _state.update {
                        it.copy(
                            topics = topics,
                            loading = false,
                            optimisticUnreadFloorByTopic = emptyMap(),
                        )
                    }
                }
                .onFailure { err ->
                    _state.update {
                        it.copy(loading = false, error = err.message ?: "reload_failed")
                    }
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun applyTopicActivity(event: TeamForumTopicActivityEvent) {
        if (event.senderUserId.trim() == currentUserId.trim()) return
        if (event.topicId == openTopicId) return
        val messageId = event.messageId.trim()
        if (messageId.isNotEmpty()) {
            val prev = lastActivityMessageIdByTopic[event.topicId]
            if (prev == messageId) return
            lastActivityMessageIdByTopic[event.topicId] = messageId
        }
        topicActivityJob?.cancel()
        topicActivityJob = viewModelScope.launch {
            delay(300)
            _state.update { st ->
                val idx = st.topics.indexOfFirst { it.id == event.topicId }
                if (idx < 0) return@update st
                val row = st.topics[idx]
                val nextFloor = ((st.optimisticUnreadFloorByTopic[event.topicId] ?: 0) + 1)
                    .coerceAtMost(999)
                val bumped = row.copy(
                    unreadCount = row.unreadCount + 1,
                    messageCount = row.messageCount + 1,
                    lastMessageAt = java.time.Instant.now().toString(),
                )
                st.copy(
                    topics = st.topics.toMutableList().apply { this[idx] = bumped },
                    optimisticUnreadFloorByTopic = st.optimisticUnreadFloorByTopic + (event.topicId to nextFloor),
                )
            }
        }
    }

    fun applyTopicPin(event: TeamForumTopicPinChangedEvent) {
        _state.update { st ->
            val idx = st.topics.indexOfFirst { it.id == event.topicId }
            if (idx < 0) return@update st
            val row = st.topics[idx]
            val updated = row.copy(
                pinnedMessageId = event.pinnedMessageId,
                pinnedAt = event.pinnedAt,
                pinnedByUserId = event.pinnedByUserId,
                pinnedMessage = event.pinnedMessage,
                pinnedMessages = event.pinnedMessages,
            )
            st.copy(topics = st.topics.toMutableList().apply { this[idx] = updated })
        }
    }

    fun applyTopicReadLocal(topicId: String, messageId: String) {
        val tpid = topicId.trim()
        val mid = messageId.trim()
        if (tpid.isEmpty() || mid.isEmpty()) return
        _state.update { st ->
            val idx = st.topics.indexOfFirst { it.id == tpid }
            if (idx < 0) return@update st
            val row = st.topics[idx]
            st.copy(
                topics = st.topics.toMutableList().apply {
                    this[idx] = row.copy(unreadCount = 0, lastReadMessageId = mid)
                },
                optimisticUnreadFloorByTopic = st.optimisticUnreadFloorByTopic - tpid,
            )
        }
    }

    companion object {
        fun create(application: Application, teamId: String): ForumListViewModel {
            val container = AppContainer.from(application)
            val userId = JwtAccessTokenClaims.sub(container.tokenStore.getAccessToken()).orEmpty()
            return ForumListViewModel(
                application = application,
                forumRepository = container.forumRepository,
                currentUserId = userId,
            ).also { it.bindTeam(teamId) }
        }
    }
}
