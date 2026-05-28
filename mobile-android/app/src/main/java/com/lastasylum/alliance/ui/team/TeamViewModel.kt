package com.lastasylum.alliance.ui.team

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamForumPreferences
import com.lastasylum.alliance.data.teams.TeamInboxUnread
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamSectionBadges(
    val newsUnread: Int = 0,
    val forumUnread: Int = 0,
)

data class TeamScreenData(
    val profile: MyProfileDto? = null,
    val teamDetail: TeamDetailDto? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val sectionBadges: TeamSectionBadges = TeamSectionBadges(),
)

class TeamViewModel(
    private val usersRepository: UsersRepository,
    private val teamsRepository: TeamsRepository,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val teamForumPreferences: TeamForumPreferences,
    private val launchDiskCache: LaunchDiskCache,
    private val currentUserId: String,
) : ViewModel() {
    private val _data = MutableStateFlow(TeamScreenData())
    val data: StateFlow<TeamScreenData> = _data.asStateFlow()

    /** Tab badges only — avoids recomposing roster/news/forum on badge tick. */
    val sectionBadges: StateFlow<TeamSectionBadges> = _data
        .map { it.sectionBadges }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TeamSectionBadges())

    fun setError(message: String?) {
        _data.update { it.copy(error = message) }
    }

    fun reloadProfileAndTeam(resources: Resources) {
        viewModelScope.launch {
            val cachedProfile = currentUserId.takeIf { it.isNotBlank() }
                ?.let { launchDiskCache.loadProfile(it) }
            val cachedTeam = if (
                cachedProfile != null &&
                currentUserId.isNotBlank() &&
                !cachedProfile.playerTeamId.isNullOrBlank()
            ) {
                launchDiskCache.loadTeam(currentUserId)
            } else {
                null
            }
            if (cachedProfile != null) {
                _data.update {
                    it.copy(
                        profile = cachedProfile,
                        teamDetail = cachedTeam,
                        loading = cachedTeam == null && !cachedProfile.playerTeamId.isNullOrBlank(),
                        error = null,
                    )
                }
            } else {
                _data.update { it.copy(loading = true, error = null) }
            }
            usersRepository.getMyProfile()
                .onSuccess { my ->
                    if (currentUserId.isNotBlank()) {
                        launchDiskCache.saveProfile(currentUserId, my)
                    }
                    val teamId = my.playerTeamId
                    if (teamId.isNullOrBlank()) {
                        _data.update {
                            it.copy(profile = my, teamDetail = null, loading = false)
                        }
                    } else {
                        teamsRepository.getTeam(teamId)
                            .onSuccess { detail ->
                                if (currentUserId.isNotBlank()) {
                                    launchDiskCache.saveTeam(currentUserId, detail)
                                }
                                _data.update {
                                    it.copy(profile = my, teamDetail = detail, loading = false)
                                }
                            }
                            .onFailure { e ->
                                _data.update {
                                    it.copy(
                                        profile = my,
                                        teamDetail = cachedTeam,
                                        loading = false,
                                        error = e.toUserMessageRu(resources),
                                    )
                                }
                            }
                    }
                }
                .onFailure {
                    if (cachedProfile == null) {
                        _data.update {
                            it.copy(
                                profile = null,
                                teamDetail = null,
                                loading = false,
                                error = resources.getString(R.string.profile_load_error),
                            )
                        }
                    } else {
                        _data.update { it.copy(loading = false) }
                    }
                }
        }
    }

    fun refreshSectionBadges() {
        val teamId = _data.value.profile?.playerTeamId?.trim().orEmpty()
        if (teamId.isEmpty()) {
            _data.update { it.copy(sectionBadges = TeamSectionBadges()) }
            return
        }
        viewModelScope.launch {
            val newsAfter = userSettingsPreferences.getLastSeenTeamNewsCreatedAt()
            val localForumRead = teamForumPreferences.loadAllLastReadMessageIds(teamId)
            val diskTopics = if (currentUserId.isNotBlank()) {
                launchDiskCache.loadForumTopics(currentUserId, teamId)
            } else {
                null
            }
            val networkTopics = teamsRepository.listForumTopics(teamId).getOrNull()?.also { loaded ->
                if (currentUserId.isNotBlank()) {
                    launchDiskCache.saveForumTopics(currentUserId, teamId, loaded)
                }
            }
            val topics = networkTopics ?: diskTopics
            val profileId = _data.value.profile?.id?.trim().orEmpty()
            val forumUnread = topics?.let { TeamInboxUnread.sumForumUnread(it, localForumRead) }
            val newsFallback = teamsRepository.listTeamNews(teamId, cursor = null, limit = 40)
                .getOrNull()
                ?.items
                ?.let {
                    TeamInboxUnread.countUnreadNews(it, userSettingsPreferences, profileId)
                }
            teamsRepository.getTeamInboxBadges(teamId, newsAfter)
                .onSuccess { badges ->
                    _data.update {
                        it.copy(
                            sectionBadges = TeamSectionBadges(
                                newsUnread = badges.newsUnread.coerceAtLeast(0),
                                forumUnread = forumUnread ?: badges.forumUnread.coerceAtLeast(0),
                            ),
                        )
                    }
                    OverlayGameStatusHudRefresh.invalidateNewsForumCache()
                }
                .onFailure {
                    _data.update {
                        it.copy(
                            sectionBadges = TeamSectionBadges(
                                newsUnread = newsFallback ?: it.sectionBadges.newsUnread,
                                forumUnread = forumUnread ?: it.sectionBadges.forumUnread,
                            ),
                        )
                    }
                }
        }
    }
}

class TeamViewModelFactory(
    private val usersRepository: UsersRepository,
    private val teamsRepository: TeamsRepository,
    private val userSettingsPreferences: UserSettingsPreferences,
    private val teamForumPreferences: TeamForumPreferences,
    private val launchDiskCache: LaunchDiskCache,
    private val currentUserId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TeamViewModel::class.java)) {
            return TeamViewModel(
                usersRepository,
                teamsRepository,
                userSettingsPreferences,
                teamForumPreferences,
                launchDiskCache,
                currentUserId,
            ) as T
        }
        error("Unsupported ViewModel: ${modelClass.name}")
    }
}
