package com.lastasylum.alliance.overlay

import android.content.res.Resources
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamPresenceSocketEvent
import com.lastasylum.alliance.data.teams.TeamPresenceSocketManager
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.util.OVERLAY_ONLINE_PANEL_POLL_MS
import com.lastasylum.alliance.ui.util.isOverlayIngameNow
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OverlayTeamOnlineUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val team: TeamDetailDto? = null,
    val profile: MyProfileDto? = null,
    val baseSections: List<OverlayOnlinePresenceSection> = emptyList(),
    val ingameRaw: List<PlayerTeamMemberDto> = emptyList(),
    val recentRaw: List<PlayerTeamMemberDto> = emptyList(),
    val searchQuery: String = "",
    val activeFilterChip: OverlayOnlineFilterChip = OverlayOnlineFilterChip.IngameOnly,
    val ingameCount: Int = 0,
    val recentCount: Int = 0,
)

class OverlayTeamOnlineController(
    private val scope: CoroutineScope,
    private val teamsRepository: TeamsRepository,
    private val usersRepository: UsersRepository,
    private val teamPresenceSocket: TeamPresenceSocketManager,
    private val tokenProvider: () -> String?,
    private val baseUrl: String,
    private val resources: Resources,
    private val onIngameCountChanged: (Int) -> Unit = {},
) {
    private val _state = MutableStateFlow(OverlayTeamOnlineUiState())
    val state: StateFlow<OverlayTeamOnlineUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var freshnessJob: Job? = null
    private var teamId: String? = null
    private var started = false

    private val presenceSocketListener: (TeamPresenceSocketEvent) -> Unit = { event ->
        scope.launch {
            val current = _state.value
            val fallback = current.team?.members?.firstOrNull { it.userId == event.userId }
            val merged = mergePresenceSocketEvent(
                lists = OverlayOnlinePresenceLists(current.ingameRaw, current.recentRaw),
                event = event,
                fallbackMember = fallback,
            )
            val knownUserIds = buildSet {
                merged.ingame.forEach { add(it.userId) }
                merged.recentlyActive.forEach { add(it.userId) }
            }
            val needsFullRefresh = event.userId !in knownUserIds &&
                fallback == null &&
                isOverlayIngameNow(event.presenceStatus, event.lastPresenceAt)
            if (needsFullRefresh) {
                refreshPresenceOnly(showRefreshing = false)
            } else {
                applyPresenceLists(merged.ingame, merged.recentlyActive)
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        teamPresenceSocket.addPresenceListener(presenceSocketListener)
        scope.launch {
            bootstrap(forceTeamRefresh = false, showBlockingSpinner = _state.value.team == null)
        }
        pollJob = scope.launch {
            while (isActive && started) {
                delay(OVERLAY_ONLINE_PANEL_POLL_MS)
                if (started) refreshPresenceOnly(showRefreshing = false)
            }
        }
        freshnessJob = scope.launch {
            while (isActive && started) {
                delay(FRESHNESS_REBUILD_MS)
                if (started) refreshPresenceFreshness()
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        pollJob?.cancel()
        pollJob = null
        freshnessJob?.cancel()
        freshnessJob = null
        teamPresenceSocket.removePresenceListener(presenceSocketListener)
        teamPresenceSocket.disconnect()
    }

    fun refresh(force: Boolean = false) {
        scope.launch {
            if (force) {
                OverlayTeamContextCache.invalidate()
                OverlayTeamPresenceCache.invalidate()
            }
            bootstrap(forceTeamRefresh = force, showBlockingSpinner = false)
            refreshPresenceOnly(showRefreshing = true)
        }
    }

    fun onSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun onFilterChip(chip: OverlayOnlineFilterChip) {
        _state.update { it.copy(activeFilterChip = chip) }
    }

    fun displaySections(voiceFlagsByUserId: Map<String, VoiceMemberFlags>): List<OverlayOnlinePresenceSection> {
        val s = _state.value
        return applyOnlinePanelFilters(
            baseSections = s.baseSections,
            query = s.searchQuery,
            chip = s.activeFilterChip,
            voiceFlagsByUserId = voiceFlagsByUserId,
        )
    }

    private suspend fun bootstrap(forceTeamRefresh: Boolean, showBlockingSpinner: Boolean) {
        if (showBlockingSpinner || _state.value.team == null) {
            _state.update { it.copy(loading = true) }
        }
        _state.update { it.copy(error = null) }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val ctx = OverlayTeamContextCache.load(
                    usersRepository = usersRepository,
                    teamsRepository = teamsRepository,
                    forceRefresh = forceTeamRefresh,
                ).getOrThrow()
                if (forceTeamRefresh) {
                    OverlayTeamPresenceCache.invalidate()
                }
                val t = OverlayTeamContextCache.loadTeamDetail(
                    teamId = ctx.teamId,
                    teamsRepository = teamsRepository,
                    forceRefresh = forceTeamRefresh,
                ).getOrThrow()
                val presence = OverlayTeamPresenceCache.load(
                    teamId = ctx.teamId,
                    teamsRepository = teamsRepository,
                    forceRefresh = forceTeamRefresh,
                ).getOrThrow()
                val p = usersRepository.getMyProfile().getOrThrow()
                BootstrapResult(
                    profile = p,
                    team = t,
                    teamId = ctx.teamId,
                    ingame = presence.ingame,
                    recentlyActive = presence.recentlyActive,
                )
            }
        }
        loaded.onSuccess { result ->
            teamId = result.teamId
            teamPresenceSocket.connect(
                baseUrl = baseUrl,
                teamId = result.teamId,
                tokenProvider = tokenProvider,
            )
            _state.update {
                it.copy(
                    profile = result.profile,
                    team = result.team,
                    loading = false,
                    refreshing = false,
                )
            }
            applyPresenceLists(result.ingame, result.recentlyActive)
        }.onFailure { e ->
            _state.update {
                val cleared = if (it.team == null) {
                    it.copy(
                        profile = null,
                        ingameRaw = emptyList(),
                        recentRaw = emptyList(),
                        baseSections = emptyList(),
                        ingameCount = 0,
                        recentCount = 0,
                    )
                } else {
                    it
                }
                cleared.copy(
                    loading = false,
                    refreshing = false,
                    error = e.toUserMessageRu(resources),
                )
            }
        }
    }

    private suspend fun refreshPresenceOnly(showRefreshing: Boolean) {
        val tid = teamId?.trim().orEmpty()
        if (tid.isEmpty()) return
        if (showRefreshing) {
            _state.update { it.copy(refreshing = true) }
        }
        val loaded = withContext(Dispatchers.IO) {
            OverlayTeamPresenceCache.load(
                teamId = tid,
                teamsRepository = teamsRepository,
                forceRefresh = false,
            )
        }
        loaded.onSuccess { presence ->
            applyPresenceLists(presence.ingame, presence.recentlyActive)
            _state.update { it.copy(error = null, refreshing = false) }
        }.onFailure {
            _state.update { it.copy(refreshing = false) }
        }
    }

    private fun refreshPresenceFreshness() {
        val s = _state.value
        if (s.ingameRaw.isEmpty() && s.recentRaw.isEmpty()) return
        applyPresenceLists(s.ingameRaw, s.recentRaw, forceRebuild = true)
    }

    private fun applyPresenceLists(
        ingame: List<PlayerTeamMemberDto>,
        recentlyActive: List<PlayerTeamMemberDto>,
        forceRebuild: Boolean = false,
    ) {
        val current = _state.value
        if (!forceRebuild &&
            overlayPresenceMemberListsEqual(current.ingameRaw, ingame) &&
            overlayPresenceMemberListsEqual(current.recentRaw, recentlyActive)
        ) {
            return
        }
        val selfId = current.profile?.id
        val sections = buildPresenceSections(ingame, recentlyActive, selfId)
        val count = rawIngameCount(ingame)
        val recentCount = rawRecentCount(ingame, recentlyActive)
        _state.update {
            it.copy(
                ingameRaw = ingame,
                recentRaw = recentlyActive,
                baseSections = sections,
                ingameCount = count,
                recentCount = recentCount,
                loading = false,
            )
        }
        if (count != current.ingameCount) {
            onIngameCountChanged(count)
        }
    }

    private data class BootstrapResult(
        val profile: MyProfileDto,
        val team: TeamDetailDto,
        val teamId: String,
        val ingame: List<PlayerTeamMemberDto>,
        val recentlyActive: List<PlayerTeamMemberDto>,
    )

    private companion object {
        private const val FRESHNESS_REBUILD_MS = 30_000L
    }
}
