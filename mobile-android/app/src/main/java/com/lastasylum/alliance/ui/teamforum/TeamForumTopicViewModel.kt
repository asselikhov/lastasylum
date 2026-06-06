package com.lastasylum.alliance.ui.teamforum

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.forum.ForumRepository
import com.lastasylum.alliance.di.AppContainer
import com.lastasylum.alliance.ui.chat.ForumMessagesListDerived
import com.lastasylum.alliance.ui.chat.buildForumMessagesListDerived
import com.lastasylum.alliance.ui.chat.capForumMessagesOldestFirst
import com.lastasylum.alliance.ui.chat.mergePreservingForumMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TeamForumTopicUiState(
    val teamId: String = "",
    val topicId: String = "",
    val messages: List<TeamForumMessageDto> = emptyList(),
    val listDerived: ForumMessagesListDerived = ForumMessagesListDerived.Empty,
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hasMoreOlder: Boolean = false,
    val error: String? = null,
)

/**
 * Forum topic chat state + derived timeline (shared list engine).
 * Route may still host composer/socket locally; VM centralizes paging + merge + derive.
 */
class TeamForumTopicViewModel(
    application: Application,
    private val forumRepository: ForumRepository,
    private val currentUserId: String,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TeamForumTopicUiState())
    val uiState: StateFlow<TeamForumTopicUiState> = _uiState.asStateFlow()

    private val messages = mutableListOf<TeamForumMessageDto>()
    private var deriveJob: Job? = null

    fun bindTopic(teamId: String, topicId: String) {
        if (_uiState.value.teamId == teamId && _uiState.value.topicId == topicId) return
        messages.clear()
        _uiState.value = TeamForumTopicUiState(teamId = teamId, topicId = topicId, loading = true)
        loadInitial()
    }

    fun loadInitial() {
        val teamId = _uiState.value.teamId
        val topicId = _uiState.value.topicId
        if (teamId.isBlank() || topicId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            loadPage(teamId, topicId, before = null, appendOlder = false)
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun loadOlder() {
        val teamId = _uiState.value.teamId
        val topicId = _uiState.value.topicId
        val oldestId = messages.firstOrNull()?.id ?: return
        if (_uiState.value.loadingOlder) return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingOlder = true) }
            loadPage(teamId, topicId, before = oldestId, appendOlder = true)
            _uiState.update { it.copy(loadingOlder = false) }
        }
    }

    fun mergeIncoming(msg: TeamForumMessageDto) {
        val i = messages.indexOfFirst { it.id == msg.id }
        if (i >= 0) {
            messages[i] = messages[i].mergePreservingForumMedia(msg)
        } else {
            messages.add(msg)
            capForumMessagesOldestFirst(messages)
        }
        publishMessages()
    }

    fun removeMessage(messageId: String) {
        messages.removeAll { it.id == messageId }
        publishMessages()
    }

    private suspend fun loadPage(
        teamId: String,
        topicId: String,
        before: String?,
        appendOlder: Boolean,
    ) {
        forumRepository.listForumMessages(
            userId = currentUserId,
            teamId = teamId,
            topicId = topicId,
            before = before,
            limit = 50,
        )
            .onSuccess { page ->
                val visible = page.filter { m ->
                    m.deletedAt.isNullOrBlank() || m.deletedAt.equals("null", ignoreCase = true)
                }
                if (appendOlder) {
                    val existing = messages.map { it.id }.toSet()
                    val older = visible.filter { it.id !in existing }
                    messages.addAll(0, older)
                } else {
                    messages.clear()
                    messages.addAll(visible)
                }
                capForumMessagesOldestFirst(messages)
                _uiState.update {
                    it.copy(
                        hasMoreOlder = page.size >= 50 && messages.isNotEmpty(),
                        error = if (appendOlder) it.error else null,
                    )
                }
                publishMessages()
            }
            .onFailure { e ->
                if (!appendOlder) {
                    _uiState.update { it.copy(error = e.message) }
                }
            }
    }

    private fun publishMessages() {
        val snapshot = messages.toList()
        _uiState.update { it.copy(messages = snapshot) }
        deriveJob?.cancel()
        deriveJob = viewModelScope.launch {
            delay(DERIVE_DEBOUNCE_MS)
            val derived = withContext(Dispatchers.Default) {
                buildForumMessagesListDerived(snapshot)
            }
            _uiState.update { it.copy(listDerived = derived) }
        }
    }

    companion object {
        private const val DERIVE_DEBOUNCE_MS = 32L

        fun create(application: Application, teamId: String, topicId: String): TeamForumTopicViewModel {
            val container = AppContainer.from(application)
            val userId = JwtAccessTokenClaims.sub(container.tokenStore.getAccessToken()).orEmpty()
            return TeamForumTopicViewModel(
                application = application,
                forumRepository = container.forumRepository,
                currentUserId = userId,
            ).also { it.bindTopic(teamId, topicId) }
        }
    }
}
