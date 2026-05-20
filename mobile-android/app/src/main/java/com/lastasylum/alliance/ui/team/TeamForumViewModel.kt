package com.lastasylum.alliance.ui.team

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.effectiveUnreadCount
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamForumTopicsState(
    val topics: List<TeamForumTopicDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class TeamForumViewModel(
    private val teamsRepository: TeamsRepository,
    private val teamId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamForumTopicsState())
    val state: StateFlow<TeamForumTopicsState> = _state.asStateFlow()

    fun reload(
        resources: Resources,
        lastReadByTopic: Map<String, String>,
        onHydrateRead: (List<TeamForumTopicDto>) -> Unit,
        onTopicTitles: (List<TeamForumTopicDto>) -> Unit,
        markReadStale: (TeamForumTopicDto, String) -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            teamsRepository.listForumTopics(teamId)
                .onSuccess { rows ->
                    onHydrateRead(rows)
                    val withUnread = rows.map { topic ->
                        val effective = effectiveUnreadCount(
                            serverUnread = topic.unreadCount,
                            lastReadMessageId = topic.lastReadMessageId,
                            localLastReadMessageId = lastReadByTopic[topic.id],
                        )
                        topic.copy(unreadCount = effective)
                    }
                    onTopicTitles(withUnread)
                    rows.filter { server ->
                        server.unreadCount > 0 &&
                            effectiveUnreadCount(
                                serverUnread = server.unreadCount,
                                lastReadMessageId = server.lastReadMessageId,
                                localLastReadMessageId = lastReadByTopic[server.id],
                            ) == 0
                    }.forEach { server ->
                        val localLast = lastReadByTopic[server.id]
                            ?: server.lastReadMessageId?.trim().orEmpty().takeIf { it.isNotBlank() }
                            ?: return@forEach
                        markReadStale(server, localLast)
                    }
                    _state.update { it.copy(topics = withUnread, loading = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.toUserMessageRu(resources))
                    }
                }
        }
    }
}

class TeamForumViewModelFactory(
    private val teamsRepository: TeamsRepository,
    private val teamId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TeamForumViewModel::class.java)) {
            return TeamForumViewModel(teamsRepository, teamId) as T
        }
        error("Unsupported ViewModel: ${modelClass.name}")
    }
}
