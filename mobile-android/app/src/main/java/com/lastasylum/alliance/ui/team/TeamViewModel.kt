package com.lastasylum.alliance.ui.team

import android.content.res.Resources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.overlay.OverlayGameStatusHudRefresh
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val _data = MutableStateFlow(TeamScreenData())
    val data: StateFlow<TeamScreenData> = _data.asStateFlow()

    fun setError(message: String?) {
        _data.update { it.copy(error = message) }
    }

    fun reloadProfileAndTeam(resources: Resources) {
        viewModelScope.launch {
            _data.update { it.copy(loading = true, error = null) }
            usersRepository.getMyProfile()
                .onSuccess { my ->
                    val teamId = my.playerTeamId
                    if (teamId.isNullOrBlank()) {
                        _data.update {
                            it.copy(profile = my, teamDetail = null, loading = false)
                        }
                    } else {
                        teamsRepository.getTeam(teamId)
                            .onSuccess { detail ->
                                _data.update {
                                    it.copy(profile = my, teamDetail = detail, loading = false)
                                }
                            }
                            .onFailure { e ->
                                _data.update {
                                    it.copy(
                                        profile = my,
                                        teamDetail = null,
                                        loading = false,
                                        error = e.toUserMessageRu(resources),
                                    )
                                }
                            }
                    }
                }
                .onFailure {
                    _data.update {
                        it.copy(
                            profile = null,
                            teamDetail = null,
                            loading = false,
                            error = resources.getString(R.string.profile_load_error),
                        )
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
            teamsRepository.getTeamInboxBadges(teamId, newsAfter)
                .onSuccess { badges ->
                    _data.update {
                        it.copy(
                            sectionBadges = TeamSectionBadges(
                                newsUnread = badges.newsUnread.coerceAtLeast(0),
                                forumUnread = badges.forumUnread.coerceAtLeast(0),
                            ),
                        )
                    }
                    OverlayGameStatusHudRefresh.invalidateNewsForumCache()
                }
        }
    }
}

class TeamViewModelFactory(
    private val usersRepository: UsersRepository,
    private val teamsRepository: TeamsRepository,
    private val userSettingsPreferences: UserSettingsPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TeamViewModel::class.java)) {
            return TeamViewModel(
                usersRepository,
                teamsRepository,
                userSettingsPreferences,
            ) as T
        }
        error("Unsupported ViewModel: ${modelClass.name}")
    }
}
