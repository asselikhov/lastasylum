package com.lastasylum.alliance.overlay

import android.content.res.Resources
import com.lastasylum.alliance.data.cache.LaunchDiskCache
import com.lastasylum.alliance.data.settings.UserSettingsPreferences
import com.lastasylum.alliance.data.teams.PlayerTeamMemberDto
import com.lastasylum.alliance.data.teams.TeamDetailDto
import com.lastasylum.alliance.data.teams.TeamPresenceSocketEvent
import com.lastasylum.alliance.data.teams.TeamPresenceSocketManager
import com.lastasylum.alliance.data.teams.TeamsRepository
import com.lastasylum.alliance.data.users.MyProfileDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.R
import com.lastasylum.alliance.ui.OVERLAY_PANEL_LOAD_MAX_MS
import com.lastasylum.alliance.ui.util.OVERLAY_ONLINE_DISPLAY_CLOCK_MS
import com.lastasylum.alliance.ui.util.OVERLAY_ONLINE_PANEL_POLL_MS
import com.lastasylum.alliance.ui.util.resolveOverlayOnlinePanelPollMs
import com.lastasylum.alliance.data.voice.TeamVoicePresenceStore
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class OverlayTeamOnlineUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val staleDataHint: String? = null,
    val team: TeamDetailDto? = null,
    val profile: MyProfileDto? = null,
    val baseSections: List<OverlayOnlinePresenceSection> = emptyList(),
    val ingameRaw: List<PlayerTeamMemberDto> = emptyList(),
    val recentRaw: List<PlayerTeamMemberDto> = emptyList(),
    val searchQuery: String = "",
    val activeFilterChip: OverlayOnlineFilterChip = OverlayOnlineFilterChip.IngameOnly,
    val ingameCount: Int = 0,
    val recentCount: Int = 0,
    val displayEpoch: Long = 0,
)

class OverlayTeamOnlineController(
    private val scope: CoroutineScope,
    private val teamsRepository: TeamsRepository,
    private val usersRepository: UsersRepository,
    private val teamPresenceSocket: TeamPresenceSocketManager,
    private val launchDiskCache: LaunchDiskCache?,
    private val userSettingsPreferences: UserSettingsPreferences,
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
    private var panelForeground = false
    private var socketConnected = false
    private var lastLoggedPollMode: String? = null
    private var localVoiceMicOn: Boolean? = null
    private var localVoiceSoundOn: Boolean? = null
    private var lastPollAtMs = 0L
    private val presenceMergeMutex = Mutex()
    private val bootstrapMutex = Mutex()

    private val presenceSocketListener: (TeamPresenceSocketEvent) -> Unit = { event ->
        scope.launch {
            presenceMergeMutex.withLock {
                val tid = teamId?.trim().orEmpty()
                if (tid.isEmpty()) return@withLock
                val current = _state.value
                val fallback = current.team?.members?.firstOrNull { it.userId == event.userId }
                    ?: OverlayTeamContextCache.memberDto(event.userId)
                OverlayTeamPresenceCache.applySocketEvent(tid, event, fallback)
                val updated = OverlayTeamPresenceCache.peek(tid) ?: return@withLock
                val knownUserIds = buildSet {
                    updated.ingame.forEach { add(it.userId) }
                    updated.recentlyActive.forEach { add(it.userId) }
                }
                val needsFullRefresh = event.userId !in knownUserIds && fallback == null
                if (needsFullRefresh) {
                    refreshPresenceOnly(showRefreshing = false)
                } else {
                    applyPresenceLists(updated.ingame, updated.recentlyActive)
                }
            }
        }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            hydrateFromDiskIfNeeded()
            applyCachedBootstrapIfAvailable()
            bootstrap(forceTeamRefresh = false, showBlockingSpinner = _state.value.team == null)
        }
        teamPresenceSocket.addPresenceListener(presenceSocketListener)
        pollJob = scope.launch {
            while (isActive && started) {
                val interval = currentPollIntervalMs()
                delay(interval)
                if (started) {
                    logPollIfNeeded(interval)
                    refreshPresenceOnly(showRefreshing = false)
                }
            }
        }
        freshnessJob = scope.launch {
            while (isActive && started) {
                delay(FRESHNESS_REBUILD_MS)
                if (started) refreshPresenceFreshness()
            }
        }
        displayClockJob = scope.launch {
            while (isActive && started) {
                delay(OVERLAY_ONLINE_DISPLAY_CLOCK_MS)
                if (started) tickDisplayClock()
            }
        }
    }

    private var displayClockJob: Job? = null

    fun setPanelForeground(active: Boolean) {
        panelForeground = active
    }

    fun setPresenceSocketConnected(connected: Boolean) {
        socketConnected = connected
    }

    fun currentPollIntervalMs(): Long = resolveOverlayOnlinePanelPollMs(
        socketConnected = socketConnected,
        panelForeground = panelForeground,
    )

    fun tickDisplayClock() {
        refreshPresenceFreshness()
        _state.update { it.copy(displayEpoch = it.displayEpoch + 1) }
    }

    fun reloadPinnedSort() {
        val current = _state.value
        applyPresenceLists(current.ingameRaw, current.recentRaw, forceRebuild = true)
    }

    fun updateLocalVoiceFlags(micOn: Boolean?, soundOn: Boolean?) {
        localVoiceMicOn = micOn
        localVoiceSoundOn = soundOn
    }

    fun rebuildSortFromVoice() {
        reloadPinnedSort()
    }

    private fun logPollIfNeeded(intervalMs: Long) {
        val mode = when (intervalMs) {
            resolveOverlayOnlinePanelPollMs(socketConnected = true, panelForeground = panelForeground) -> "socket"
            resolveOverlayOnlinePanelPollMs(socketConnected = false, panelForeground = true) -> "fast"
            else -> "normal"
        }
        val now = System.currentTimeMillis()
        if (mode != lastLoggedPollMode || now - lastPollAtMs >= POLL_LOG_THROTTLE_MS) {
            lastLoggedPollMode = mode
            lastPollAtMs = now
            OverlayRuntimeMetrics.logOnlinePanelPoll(mode)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        pollJob?.cancel()
        pollJob = null
        freshnessJob?.cancel()
        freshnessJob = null
        displayClockJob?.cancel()
        displayClockJob = null
        teamPresenceSocket.removePresenceListener(presenceSocketListener)
    }

    fun refresh(force: Boolean = false) {
        scope.launch {
            if (force) {
                OverlayTeamContextCache.invalidate()
                OverlayTeamPresenceCache.invalidate()
            }
            bootstrap(
                forceTeamRefresh = force,
                showBlockingSpinner = false,
            )
        }
    }

    fun onPresenceSocketConnected() {
        scope.launch {
            refreshPresenceOnly(showRefreshing = false)
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

    private suspend fun hydrateFromDiskIfNeeded() {
        withContext(Dispatchers.IO) {
            val uid = usersRepository.peekMyProfile()?.id?.trim().orEmpty()
                .ifEmpty { usersRepository.peekMyProfileDisk()?.id?.trim().orEmpty() }
            if (uid.isEmpty() || launchDiskCache == null) return@withContext
            OverlayTeamContextCache.hydrateFromDisk(
                userId = uid,
                usersRepository = usersRepository,
                launchDiskCache = launchDiskCache,
            )
            val teamId = OverlayTeamContextCache.peekForPanel()?.teamId?.trim().orEmpty()
            if (teamId.isEmpty()) return@withContext
            launchDiskCache.loadOverlayPresence(uid, teamId)?.let { presence ->
                OverlayTeamPresenceCache.seedFromDisk(teamId, presence)
            }
        }
    }

    private fun applyCachedBootstrapIfAvailable() {
        val ctx = OverlayTeamContextCache.peekForPanel() ?: return
        val team = OverlayTeamContextCache.peekCachedTeam()?.takeIf { it.id == ctx.teamId } ?: return
        val profile = usersRepository.peekMyProfile()
            ?: usersRepository.peekMyProfileDisk()
            ?: return
        teamId = team.id.trim().ifEmpty { ctx.teamId }
        connectPresenceSocket(teamId!!)
        val presence = OverlayTeamPresenceCache.peek(ctx.teamId)
        _state.update {
            it.copy(
                profile = profile,
                team = team,
                loading = presence == null && it.baseSections.isEmpty(),
                error = null,
            )
        }
        if (presence != null) {
            applyPresenceLists(presence.ingame, presence.recentlyActive)
        }
    }

    private suspend fun bootstrap(forceTeamRefresh: Boolean, showBlockingSpinner: Boolean) {
        bootstrapMutex.withLock {
            val bootstrapStartedAt = android.os.SystemClock.elapsedRealtime()
            val hadCachedContent = _state.value.team != null || _state.value.baseSections.isNotEmpty()
            if (showBlockingSpinner && !hadCachedContent) {
                _state.update { it.copy(loading = true) }
            }
            if (!hadCachedContent) {
                _state.update { it.copy(error = null) }
            } else if (forceTeamRefresh) {
                _state.update { it.copy(refreshing = true, error = null) }
            }
            val loaded = withContext(Dispatchers.IO) {
                withTimeoutOrNull(OVERLAY_PANEL_LOAD_MAX_MS) {
                    runCatching { loadBootstrapFromNetwork(forceTeamRefresh) }
                }
            }
            when {
                loaded == null -> {
                    if (!hadCachedContent && _state.value.team == null && _state.value.baseSections.isEmpty()) {
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                error = resources.getString(R.string.overlay_panel_load_timeout),
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                loading = false,
                                refreshing = false,
                                staleDataHint = resources.getString(R.string.overlay_online_stale_data),
                            )
                        }
                    }
                    return@withLock
                }
            }
            loaded.onSuccess { result ->
                teamId = result.teamId
                connectPresenceSocket(result.teamId)
                _state.update {
                    it.copy(
                        profile = result.profile,
                        team = result.team,
                        loading = false,
                        refreshing = false,
                        error = null,
                        staleDataHint = null,
                    )
                }
                applyPresenceLists(result.ingame, result.recentlyActive)
                val paintMs = android.os.SystemClock.elapsedRealtime() - bootstrapStartedAt
                OverlayRuntimeMetrics.logOnlinePanelPaint(
                    source = if (hadCachedContent && !forceTeamRefresh) "cache" else "network",
                    durationMs = paintMs,
                    hadError = false,
                )
            }.onFailure { e ->
                if (!hadCachedContent && _state.value.team == null && _state.value.baseSections.isEmpty()) {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = resources.getString(R.string.overlay_online_error),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            staleDataHint = resources.getString(R.string.overlay_online_stale_data),
                            error = if (it.baseSections.isEmpty()) {
                                e.toUserMessageRu(resources)
                            } else {
                                null
                            },
                        )
                    }
                }
                val paintMs = android.os.SystemClock.elapsedRealtime() - bootstrapStartedAt
                OverlayRuntimeMetrics.logOnlinePanelPaint(
                    source = if (hadCachedContent) "cache" else "network",
                    durationMs = paintMs,
                    hadError = true,
                )
            }
        }
    }

    private suspend fun loadBootstrapFromNetwork(forceTeamRefresh: Boolean): BootstrapResult =
        coroutineScope {
            val userId = currentUserId()
            val peekCtx = if (!forceTeamRefresh) OverlayTeamContextCache.peekForPanel() else null
            val ctxDeferred = async(Dispatchers.IO) {
                peekCtx?.let { Result.success(it) }
                    ?: OverlayTeamContextCache.load(
                        usersRepository = usersRepository,
                        teamsRepository = teamsRepository,
                        forceRefresh = forceTeamRefresh,
                    )
            }
            val earlyTeamId = peekCtx?.teamId?.trim()?.takeIf { it.isNotEmpty() }
            if (forceTeamRefresh) {
                OverlayTeamPresenceCache.invalidate()
            }
            val earlyPresenceDeferred = earlyTeamId?.let { tid ->
                loadPresenceAsync(tid, userId, forceTeamRefresh)
            }
            val earlyTeamDeferred = earlyTeamId?.let { tid ->
                loadTeamDetailAsync(tid, forceTeamRefresh)
            }
            val profileDeferred = loadProfileAsync(forceTeamRefresh)
            val ctx = ctxDeferred.await().getOrThrow()
            val requestedTeamId = ctx.teamId.trim()
            val teamDeferred = earlyTeamDeferred
                ?: loadTeamDetailAsync(requestedTeamId, forceTeamRefresh)
            val presenceDeferred = earlyPresenceDeferred
                ?: loadPresenceAsync(requestedTeamId, userId, forceTeamRefresh)
            val team = teamDeferred.await().getOrThrow()
            val authoritativeTeamId = team.id.trim().ifEmpty { requestedTeamId }
            val presence = presenceDeferred.await().getOrThrow()
            val profile = profileDeferred.await()
            BootstrapResult(
                profile = profile,
                team = team,
                teamId = authoritativeTeamId,
                ingame = presence.ingame,
                recentlyActive = presence.recentlyActive,
            )
        }

    private fun CoroutineScope.loadTeamDetailAsync(
        teamId: String,
        forceTeamRefresh: Boolean,
    ): Deferred<Result<TeamDetailDto>> = async(Dispatchers.IO) {
        withTimeoutOrNull(STEP_TIMEOUT_MS) {
            OverlayTeamContextCache.loadTeamDetail(
                teamId = teamId,
                teamsRepository = teamsRepository,
                forceRefresh = forceTeamRefresh,
            )
        } ?: Result.failure(IllegalStateException("team_timeout"))
    }

    private fun CoroutineScope.loadPresenceAsync(
        teamId: String,
        userId: String?,
        forceTeamRefresh: Boolean,
    ): Deferred<Result<com.lastasylum.alliance.data.teams.TeamOverlayPresenceDto>> =
        async(Dispatchers.IO) {
            withTimeoutOrNull(STEP_TIMEOUT_MS) {
                OverlayTeamPresenceCache.load(
                    teamId = teamId,
                    teamsRepository = teamsRepository,
                    launchDiskCache = launchDiskCache,
                    userId = userId,
                    forceRefresh = forceTeamRefresh,
                )
            } ?: Result.failure(IllegalStateException("presence_timeout"))
        }

    private fun CoroutineScope.loadProfileAsync(
        forceTeamRefresh: Boolean,
    ): Deferred<MyProfileDto> = async(Dispatchers.IO) {
        withTimeoutOrNull(STEP_TIMEOUT_MS) {
            usersRepository.resolveMyProfilePreferCache()
                ?: usersRepository.getMyProfile(forceRefresh = forceTeamRefresh).getOrNull()
        } ?: usersRepository.peekMyProfile()
            ?: usersRepository.peekMyProfileDisk()
            ?: throw IllegalStateException("profile_timeout")
    }

    private suspend fun refreshPresenceOnly(
        showRefreshing: Boolean,
        forceRefresh: Boolean = false,
    ) {
        val tid = teamId?.trim().orEmpty()
        if (tid.isEmpty()) return
        if (showRefreshing) {
            _state.update { it.copy(refreshing = true) }
        }
        val loaded = withContext(Dispatchers.IO) {
            OverlayTeamPresenceCache.load(
                teamId = tid,
                teamsRepository = teamsRepository,
                launchDiskCache = launchDiskCache,
                userId = currentUserId(),
                forceRefresh = forceRefresh,
            )
        }
        loaded.onSuccess { presence ->
            applyPresenceLists(presence.ingame, presence.recentlyActive)
            _state.update { it.copy(error = null, staleDataHint = null, refreshing = false) }
        }.onFailure {
            _state.update { state ->
                state.copy(
                    refreshing = false,
                    staleDataHint = state.staleDataHint
                        ?: resources.getString(R.string.overlay_online_stale_data),
                    error = if (state.baseSections.isEmpty()) {
                        resources.getString(R.string.overlay_online_error)
                    } else {
                        state.error
                    },
                )
            }
        }
    }

    private fun refreshPresenceFreshness() {
        val s = _state.value
        if (s.ingameRaw.isEmpty() && s.recentRaw.isEmpty()) return
        val reconciled = reconcilePresenceLists(s.ingameRaw, s.recentRaw)
        applyPresenceLists(reconciled.ingame, reconciled.recentlyActive, forceRebuild = true)
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
        val tid = teamId?.trim().orEmpty()
        val pinned = if (tid.isNotEmpty()) {
            userSettingsPreferences.getOverlayPinnedMemberIdsOrdered(tid)
        } else {
            emptyList()
        }
        val memberIds = (ingame + recentlyActive).map { it.userId }
        val voiceFlags = buildVoiceFlagsMap(
            memberUserIds = memberIds,
            voicePeers = TeamVoicePresenceStore.peers.value,
            selfUserId = selfId,
            localMicOn = localVoiceMicOn,
            localSoundOn = localVoiceSoundOn,
        )
        val sections = buildPresenceSections(
            ingame = ingame,
            recentlyActive = recentlyActive,
            selfUserId = selfId,
            pinnedUserIds = pinned,
            voiceFlagsByUserId = voiceFlags,
        )
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
        if (tid.isNotEmpty()) {
            OverlayTeamPresenceCache.storeMergedLists(tid, ingame, recentlyActive)
        }
    }

    private fun connectPresenceSocket(teamId: String) {
        val tid = teamId.trim()
        if (tid.isEmpty()) return
        teamPresenceSocket.connect(
            baseUrl = baseUrl,
            teamId = tid,
            tokenProvider = tokenProvider,
        )
    }

    private fun currentUserId(): String? =
        _state.value.profile?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: usersRepository.peekMyProfile()?.id?.trim()?.takeIf { it.isNotEmpty() }
            ?: usersRepository.peekMyProfileDisk()?.id?.trim()?.takeIf { it.isNotEmpty() }

    private data class BootstrapResult(
        val profile: MyProfileDto,
        val team: TeamDetailDto,
        val teamId: String,
        val ingame: List<PlayerTeamMemberDto>,
        val recentlyActive: List<PlayerTeamMemberDto>,
    )

    private companion object {
        private const val FRESHNESS_REBUILD_MS = 30_000L
        private const val STEP_TIMEOUT_MS = 8_000L
        private const val POLL_LOG_THROTTLE_MS = 120_000L
    }
}
