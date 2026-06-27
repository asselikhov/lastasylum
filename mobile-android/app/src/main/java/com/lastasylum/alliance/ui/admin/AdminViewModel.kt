package com.lastasylum.alliance.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.admin.AdminTeamMemberDto
import com.lastasylum.alliance.data.admin.AdminUserOnServerDto
import com.lastasylum.alliance.data.admin.AllianceAdminDto
import com.lastasylum.alliance.data.admin.PlayerTeamAdminDto
import com.lastasylum.alliance.data.admin.AllianceStickerAccessDto
import com.lastasylum.alliance.data.admin.PutAllianceStickerAccessBody
import com.lastasylum.alliance.data.admin.StickerPackCatalogItemDto
import com.lastasylum.alliance.data.chat.ChatMessage
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.admin.AdminRepository
import com.lastasylum.alliance.data.teams.TeamForumMessageDto
import com.lastasylum.alliance.data.teams.TeamForumTopicDto
import com.lastasylum.alliance.data.teams.TeamNewsListItemDto
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.R
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.overlay.CombatOverlayService
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AdminPlayersSegment {
    ALL,
    WITHOUT_TEAM,
}

enum class AdminTeamDetailTab {
    MEMBERS,
    CHAT_ROOMS,
    NEWS,
    FORUM,
}

sealed interface AdminRoute {
    data object Hub : AdminRoute
    data object PlayerTeams : AdminRoute
    data class PlayerTeamDetail(
        val teamId: String,
        val title: String,
    ) : AdminRoute
    data object Players : AdminRoute
    data object ChatRouting : AdminRoute
    data class ChatRoomViewer(
        val teamId: String,
        val roomId: String,
        val roomTitle: String,
    ) : AdminRoute
    data class ForumTopicViewer(
        val teamId: String,
        val topicId: String,
        val topicTitle: String,
    ) : AdminRoute
}

data class AdminUiState(
    val route: AdminRoute = AdminRoute.Hub,
    val overviewLoading: Boolean = true,
    val playerTeamCount: Int = 0,
    val usersWithoutTeamCount: Int = 0,
    val overviewError: String? = null,
    val playerTeams: List<PlayerTeamAdminDto> = emptyList(),
    val playerTeamsLoading: Boolean = false,
    val playerTeamsError: String? = null,
    val teamSearchQuery: String = "",
    val teamsServerFilter: Int? = null,
    val selectedTeam: PlayerTeamAdminDto? = null,
    val teamDetailTab: AdminTeamDetailTab = AdminTeamDetailTab.MEMBERS,
    val teamChatRooms: List<ChatRoomDto> = emptyList(),
    val teamChatRoomsLoading: Boolean = false,
    val teamChatRoomsError: String? = null,
    val teamNews: List<TeamNewsListItemDto> = emptyList(),
    val teamNewsLoading: Boolean = false,
    val teamNewsError: String? = null,
    val teamForumTopics: List<TeamForumTopicDto> = emptyList(),
    val teamForumLoading: Boolean = false,
    val teamForumError: String? = null,
    val chatRoomMessages: List<ChatMessage> = emptyList(),
    val chatRoomMessagesLoading: Boolean = false,
    val chatRoomMessagesError: String? = null,
    val forumTopicMessages: List<TeamForumMessageDto> = emptyList(),
    val forumTopicMessagesLoading: Boolean = false,
    val forumTopicMessagesError: String? = null,
    val teamMembers: List<AdminTeamMemberDto> = emptyList(),
    val teamMembersLoading: Boolean = false,
    val teamMembersError: String? = null,
    val playersSegment: AdminPlayersSegment = AdminPlayersSegment.ALL,
    val playersSearchQuery: String = "",
    val alliances: List<AllianceAdminDto> = emptyList(),
    val alliancesLoading: Boolean = false,
    val alliancesError: String? = null,
    val stickerAllianceCode: String? = null,
    val stickerAccessLoading: Boolean = false,
    val stickerAccessError: String? = null,
    val stickerCatalog: List<com.lastasylum.alliance.data.admin.StickerPackCatalogItemDto> = emptyList(),
    val stickerRoleGrants: Map<String, Set<String>> = emptyMap(),
    val stickerUserGrants: Map<String, Set<String>> = emptyMap(),
    val stickerMembers: List<com.lastasylum.alliance.data.admin.StickerAllianceMemberDto> = emptyList(),
    val stickerSelectedPackKey: String? = null,
    val stickerMemberSearchQuery: String = "",
    val playerStickerAllianceCode: String? = null,
    val playerStickerUserId: String? = null,
    val playerStickerPackKeys: Set<String> = emptySet(),
    val playerStickerLoading: Boolean = false,
    val snackMessage: String? = null,
    val actionError: String? = null,
    val gameServers: List<com.lastasylum.alliance.data.admin.AdminServerSummaryDto> = emptyList(),
    val gameServersLoading: Boolean = false,
    val gameServersError: String? = null,
    val gameServerFilter: Int? = null,
    val usersOnServers: List<com.lastasylum.alliance.data.admin.AdminUserOnServerDto> = emptyList(),
    val usersOnServersLoading: Boolean = false,
    val usersOnServersLoadingMore: Boolean = false,
    val usersOnServersHasMore: Boolean = false,
    val usersOnServersError: String? = null,
    val playerTeamsHasMore: Boolean = false,
    val playerTeamsLoadingMore: Boolean = false,
    val confirmClearAllChatHistory: Boolean = false,
    val clearAllChatHistoryLoading: Boolean = false,
)

/** Top-bar refresh spinner scoped to the visible admin route (not every in-flight request). */
fun AdminUiState.routeRefreshing(): Boolean = when (route) {
    AdminRoute.Hub -> overviewLoading || clearAllChatHistoryLoading
    AdminRoute.PlayerTeams -> playerTeamsLoading
    AdminRoute.Players -> gameServersLoading || usersOnServersLoading
    AdminRoute.ChatRouting -> alliancesLoading
    is AdminRoute.PlayerTeamDetail -> when (teamDetailTab) {
        AdminTeamDetailTab.MEMBERS -> teamMembersLoading
        AdminTeamDetailTab.CHAT_ROOMS -> teamChatRoomsLoading
        AdminTeamDetailTab.NEWS -> teamNewsLoading
        AdminTeamDetailTab.FORUM -> teamForumLoading
    }
    is AdminRoute.ChatRoomViewer -> chatRoomMessagesLoading
    is AdminRoute.ForumTopicViewer -> forumTopicMessagesLoading
}

/** Unified player row for admin lists and edit sheet. */
typealias AdminPlayerRow = com.lastasylum.alliance.data.admin.AdminUserOnServerDto

class AdminViewModel(
    application: Application,
    private val usersRepository: UsersRepository,
    private val adminRepository: AdminRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources
    private var teamSearchJob: Job? = null

    init {
        refreshOverview()
    }

    fun navigateBack() {
        when (val route = _state.value.route) {
            is AdminRoute.ChatRoomViewer -> {
                _state.value = _state.value.copy(
                    route = AdminRoute.PlayerTeamDetail(route.teamId, teamDetailTitle(route.teamId)),
                    teamDetailTab = AdminTeamDetailTab.CHAT_ROOMS,
                )
            }
            is AdminRoute.ForumTopicViewer -> {
                _state.value = _state.value.copy(
                    route = AdminRoute.PlayerTeamDetail(route.teamId, teamDetailTitle(route.teamId)),
                    teamDetailTab = AdminTeamDetailTab.FORUM,
                )
            }
            is AdminRoute.PlayerTeamDetail -> {
                _state.value = _state.value.copy(route = AdminRoute.PlayerTeams)
            }
            AdminRoute.Hub -> Unit
            else -> _state.value = _state.value.copy(route = AdminRoute.Hub)
        }
    }

    private fun teamDetailTitle(teamId: String): String {
        val team = _state.value.selectedTeam
        if (team != null && team.id == teamId) {
            val tagLabel = com.lastasylum.alliance.ui.util.teamTagWithServerPrefix(
                team.tag.uppercase(),
                team.leaderServerNumber,
            )
            return "$tagLabel ${team.displayName}"
        }
        return teamId
    }

    fun openRoute(route: AdminRoute) {
        _state.value = _state.value.copy(route = route, actionError = null)
        when (route) {
            AdminRoute.PlayerTeams -> {
                refreshGameServerSummaries()
                refreshPlayerTeams()
            }
            AdminRoute.Players -> refreshPlayersScreen()
            AdminRoute.ChatRouting -> refreshAlliances()
            else -> Unit
        }
    }

    fun setTeamsServerFilter(serverNumber: Int?) {
        _state.value = _state.value.copy(teamsServerFilter = serverNumber)
        refreshPlayerTeams()
    }

    fun setPlayersSegment(segment: AdminPlayersSegment) {
        _state.value = _state.value.copy(playersSegment = segment)
        refreshPlayersList()
    }

    fun setPlayersServerFilter(serverNumber: Int?) {
        _state.value = _state.value.copy(gameServerFilter = serverNumber)
        refreshPlayersList()
    }

    fun setPlayersSearch(query: String) {
        _state.value = _state.value.copy(playersSearchQuery = query)
        playersSearchJob?.cancel()
        playersSearchJob = viewModelScope.launch {
            delay(300)
            refreshPlayersList()
        }
    }

    private var playersSearchJob: Job? = null

    fun refreshPlayersScreen() {
        refreshGameServerSummaries()
        refreshPlayersList()
    }

    private fun refreshGameServerSummaries() {
        viewModelScope.launch {
            _state.value = _state.value.copy(gameServersLoading = true, gameServersError = null)
            try {
                adminRepository.listGameServers()
                    .onSuccess { list ->
                        _state.value = _state.value.copy(
                            gameServers = list,
                            gameServersError = null,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            gameServersError = e.toUserMessageRu(res),
                        )
                    }
            } finally {
                _state.value = _state.value.copy(gameServersLoading = false)
            }
        }
    }

    fun refreshPlayersList() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                usersOnServersLoading = true,
                usersOnServersError = null,
            )
            val withoutTeam = _state.value.playersSegment == AdminPlayersSegment.WITHOUT_TEAM
            try {
                adminRepository.listUsersOnServers(
                    serverNumber = _state.value.gameServerFilter,
                    q = _state.value.playersSearchQuery,
                    withoutTeam = withoutTeam,
                    skip = 0,
                )
                    .onSuccess { page ->
                        _state.value = _state.value.copy(
                            usersOnServers = page.items,
                            usersOnServersHasMore = page.hasMore,
                            usersOnServersError = null,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            usersOnServersError = e.toUserMessageRu(res),
                        )
                    }
            } finally {
                _state.value = _state.value.copy(usersOnServersLoading = false)
            }
        }
    }

    fun loadMorePlayersList() {
        val s = _state.value
        if (!s.usersOnServersHasMore || s.usersOnServersLoading || s.usersOnServersLoadingMore) {
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(usersOnServersLoadingMore = true)
            val withoutTeam = s.playersSegment == AdminPlayersSegment.WITHOUT_TEAM
            adminRepository.listUsersOnServers(
                serverNumber = s.gameServerFilter,
                q = s.playersSearchQuery,
                withoutTeam = withoutTeam,
                skip = s.usersOnServers.size,
            )
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        usersOnServersLoadingMore = false,
                        usersOnServers = _state.value.usersOnServers + page.items,
                        usersOnServersHasMore = page.hasMore,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        usersOnServersLoadingMore = false,
                        usersOnServersError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun updateGameIdentityAdmin(
        userId: String,
        identityId: String,
        gameNickname: String,
        serverNumber: Int,
        successMessage: String,
    ) {
        viewModelScope.launch {
            val op = if (identityId.isBlank()) {
                adminRepository.createGameIdentity(
                    userId,
                    gameNickname.trim(),
                    serverNumber,
                )
            } else {
                adminRepository.updateGameIdentity(
                    userId,
                    identityId,
                    gameNickname.trim(),
                    serverNumber = serverNumber,
                )
            }
            op
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = successMessage, actionError = null)
                    reloadCurrentList()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(actionError = e.toUserMessageRu(res))
                }
        }
    }

    fun updatePlayerTeamBranding(
        teamId: String,
        displayName: String,
        tag: String,
        successMessage: String,
    ) {
        viewModelScope.launch {
            adminRepository.updatePlayerTeam(
                teamId = teamId,
                displayName = displayName.trim(),
                tag = tag.trim().uppercase(),
            )
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = successMessage, actionError = null)
                    refreshPlayerTeams()
                    val route = _state.value.route
                    if (route is AdminRoute.PlayerTeamDetail && route.teamId == teamId) {
                        refreshTeamMembers(teamId)
                        _state.value.selectedTeam?.let { team ->
                            val tagLabel = com.lastasylum.alliance.ui.util.teamTagWithServerPrefix(
                                tag.trim().uppercase(),
                                team.leaderServerNumber,
                            )
                            _state.value = _state.value.copy(
                                route = AdminRoute.PlayerTeamDetail(teamId, "$tagLabel ${displayName.trim()}"),
                                selectedTeam = team.copy(
                                    tag = tag.trim().uppercase(),
                                    displayName = displayName.trim(),
                                ),
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(actionError = e.toUserMessageRu(res))
                }
        }
    }

    fun openPlayerTeam(team: PlayerTeamAdminDto) {
        val tagLabel = com.lastasylum.alliance.ui.util.teamTagWithServerPrefix(
            team.tag.uppercase(),
            team.leaderServerNumber,
        )
        _state.value = _state.value.copy(
            route = AdminRoute.PlayerTeamDetail(team.id, "$tagLabel ${team.displayName}"),
            selectedTeam = team,
            teamDetailTab = AdminTeamDetailTab.MEMBERS,
            teamMembers = emptyList(),
            teamMembersError = null,
            teamChatRooms = emptyList(),
            teamNews = emptyList(),
            teamForumTopics = emptyList(),
        )
        refreshTeamDetailTab(team.id, AdminTeamDetailTab.MEMBERS)
    }

    fun setTeamDetailTab(tab: AdminTeamDetailTab) {
        val teamId = (_state.value.route as? AdminRoute.PlayerTeamDetail)?.teamId ?: return
        _state.value = _state.value.copy(teamDetailTab = tab)
        refreshTeamDetailTab(teamId, tab)
    }

    fun openChatRoomViewer(teamId: String, room: ChatRoomDto) {
        _state.value = _state.value.copy(
            route = AdminRoute.ChatRoomViewer(teamId, room.id, room.title),
            chatRoomMessages = emptyList(),
            chatRoomMessagesError = null,
        )
        refreshChatRoomMessages(room.id)
    }

    fun openForumTopicViewer(teamId: String, topic: TeamForumTopicDto) {
        _state.value = _state.value.copy(
            route = AdminRoute.ForumTopicViewer(teamId, topic.id, topic.title),
            forumTopicMessages = emptyList(),
            forumTopicMessagesError = null,
        )
        refreshForumTopicMessages(teamId, topic.id)
    }

    private fun refreshTeamDetailTab(teamId: String, tab: AdminTeamDetailTab) {
        when (tab) {
            AdminTeamDetailTab.MEMBERS -> refreshTeamMembers(teamId)
            AdminTeamDetailTab.CHAT_ROOMS -> refreshTeamChatRooms(teamId)
            AdminTeamDetailTab.NEWS -> refreshTeamNews(teamId)
            AdminTeamDetailTab.FORUM -> refreshTeamForumTopics(teamId)
        }
    }

    fun refreshTeamChatRooms(teamId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(teamChatRoomsLoading = true, teamChatRoomsError = null)
            adminRepository.listTeamChatRooms(teamId)
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        teamChatRoomsLoading = false,
                        teamChatRooms = list,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        teamChatRoomsLoading = false,
                        teamChatRoomsError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun refreshTeamNews(teamId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(teamNewsLoading = true, teamNewsError = null)
            adminRepository.listTeamNews(teamId)
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        teamNewsLoading = false,
                        teamNews = page.items,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        teamNewsLoading = false,
                        teamNewsError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun refreshTeamForumTopics(teamId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(teamForumLoading = true, teamForumError = null)
            adminRepository.listTeamForumTopics(teamId)
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        teamForumLoading = false,
                        teamForumTopics = list,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        teamForumLoading = false,
                        teamForumError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun refreshChatRoomMessages(roomId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                chatRoomMessagesLoading = true,
                chatRoomMessagesError = null,
            )
            adminRepository.listChatRoomMessages(roomId)
                .onSuccess { msgs ->
                    _state.value = _state.value.copy(
                        chatRoomMessagesLoading = false,
                        chatRoomMessages = msgs.reversed(),
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        chatRoomMessagesLoading = false,
                        chatRoomMessagesError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun refreshForumTopicMessages(teamId: String, topicId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                forumTopicMessagesLoading = true,
                forumTopicMessagesError = null,
            )
            adminRepository.listTeamForumMessages(teamId, topicId)
                .onSuccess { msgs ->
                    _state.value = _state.value.copy(
                        forumTopicMessagesLoading = false,
                        forumTopicMessages = msgs,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        forumTopicMessagesLoading = false,
                        forumTopicMessagesError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun clearSnack() {
        _state.value = _state.value.copy(snackMessage = null)
    }

    fun clearActionError() {
        _state.value = _state.value.copy(actionError = null)
    }

    fun requestClearAllChatHistoryConfirm() {
        _state.value = _state.value.copy(confirmClearAllChatHistory = true, actionError = null)
    }

    fun dismissClearAllChatHistoryConfirm() {
        if (!_state.value.confirmClearAllChatHistory) return
        _state.value = _state.value.copy(confirmClearAllChatHistory = false)
    }

    fun confirmClearAllChatHistory() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                confirmClearAllChatHistory = false,
                clearAllChatHistoryLoading = true,
                actionError = null,
            )
            adminRepository.clearAllChatMessages()
                .onSuccess { result ->
                    val chatVm = CombatOverlayService.resolveChatViewModel()
                    if (chatVm != null) {
                        chatVm.applyChatHistoryClearedFromServer()
                    } else {
                        // Живого ChatViewModel нет (админ не открывал чат в этой сессии) —
                        // чистим локальный кэш чата напрямую, чтобы сообщения исчезли сразу,
                        // не дожидаясь переоткрытия вкладки «Чат».
                        wipeLocalChatHistoryStandalone()
                    }
                    _state.value = _state.value.copy(
                        clearAllChatHistoryLoading = false,
                        chatRoomMessages = emptyList(),
                        snackMessage = res.getString(
                            R.string.admin_clear_all_chat_messages_ok,
                            result.messagesDeleted,
                            result.readStatesDeleted,
                            result.attachmentsDeleted,
                        ),
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        clearAllChatHistoryLoading = false,
                        actionError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    /**
     * Прямая очистка локального кэша чата на устройстве админа без участия ChatViewModel.
     * Использует те же зависимости из [AppContainer], что и обычная сверка после
     * серверного [chat:history:cleared], плюс фиксирует ack-вотермарк, чтобы при
     * следующем открытии чата сверка не сработала повторно.
     */
    private suspend fun wipeLocalChatHistoryStandalone() {
        val container = com.lastasylum.alliance.di.AppContainer.from(getApplication())
        val uid = com.lastasylum.alliance.data.auth.JwtAccessTokenClaims
            .sub(container.tokenStore.getAccessToken())?.trim().orEmpty()
        if (uid.isEmpty()) return
        runCatching {
            com.lastasylum.alliance.data.chat.ChatHistoryWipe.wipeAllLocalChatData(
                userId = uid,
                messageStore = container.messageStore,
                chatOutbox = container.chatOutbox,
                launchDiskCache = container.launchDiskCache,
                chatRoomPreferences = container.chatRoomPreferences,
            )
        }
        runCatching {
            container.chatRepository.getChatSyncState().getOrNull()
                ?.historyClearedAt?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { container.chatRoomPreferences.setAcknowledgedHistoryClearedAt(it) }
        }
    }

    fun refreshOverview() {
        viewModelScope.launch {
            _state.value = _state.value.copy(overviewLoading = true, overviewError = null)
            try {
                adminRepository.getOverview()
                    .onSuccess { o ->
                        _state.value = _state.value.copy(
                            playerTeamCount = o.playerTeamCount,
                            usersWithoutTeamCount = o.usersWithoutTeamCount,
                            overviewError = null,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            overviewError = e.toUserMessageRu(res),
                        )
                    }
            } finally {
                _state.value = _state.value.copy(overviewLoading = false)
            }
        }
    }

    fun refreshPlayerTeams() {
        viewModelScope.launch {
            _state.value = _state.value.copy(playerTeamsLoading = true, playerTeamsError = null)
            try {
                adminRepository.listPlayerTeams(
                    serverNumber = _state.value.teamsServerFilter,
                    skip = 0,
                )
                    .onSuccess { page ->
                        _state.value = _state.value.copy(
                            playerTeams = page.items.sortedBy { it.displayName.lowercase() },
                            playerTeamsHasMore = page.hasMore,
                            playerTeamsError = null,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            playerTeamsError = e.toUserMessageRu(res),
                        )
                    }
            } finally {
                _state.value = _state.value.copy(playerTeamsLoading = false)
            }
        }
    }

    fun loadMorePlayerTeams() {
        val s = _state.value
        if (!s.playerTeamsHasMore || s.playerTeamsLoading || s.playerTeamsLoadingMore) return
        viewModelScope.launch {
            _state.value = s.copy(playerTeamsLoadingMore = true)
            adminRepository.listPlayerTeams(
                serverNumber = s.teamsServerFilter,
                skip = s.playerTeams.size,
            )
                .onSuccess { page ->
                    val merged = (s.playerTeams + page.items)
                        .sortedBy { it.displayName.lowercase() }
                    _state.value = _state.value.copy(
                        playerTeamsLoadingMore = false,
                        playerTeams = merged,
                        playerTeamsHasMore = page.hasMore,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        playerTeamsLoadingMore = false,
                        playerTeamsError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun setTeamSearchQuery(raw: String) {
        _state.value = _state.value.copy(teamSearchQuery = raw)
    }

    fun refreshTeamDetail(teamId: String) {
        refreshTeamDetailTab(teamId, _state.value.teamDetailTab)
    }

    fun refreshTeamMembers(teamId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(teamMembersLoading = true, teamMembersError = null)
            adminRepository.getPlayerTeam(teamId)
                .onSuccess { detail ->
                    _state.value = _state.value.copy(
                        teamMembersLoading = false,
                        teamMembers = detail.members,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        teamMembersLoading = false,
                        teamMembersError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun refreshAlliances() {
        viewModelScope.launch {
            _state.value = _state.value.copy(alliancesLoading = true, alliancesError = null)
            try {
                adminRepository.listAlliances()
                    .onSuccess { list ->
                        _state.value = _state.value.copy(
                            alliances = list.sortedBy { it.allianceCode.lowercase() },
                            alliancesError = null,
                        )
                    }
                    .onFailure { e ->
                        _state.value = _state.value.copy(
                            alliancesError = e.toUserMessageRu(res),
                        )
                    }
            } finally {
                _state.value = _state.value.copy(alliancesLoading = false)
            }
        }
    }

    fun setAllianceOverlay(publicId: String, enabled: Boolean, okMessage: String) {
        viewModelScope.launch {
            adminRepository.setOverlayEnabled(publicId, enabled)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refreshAlliances()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(alliancesError = e.toUserMessageRu(res))
                }
        }
    }

    fun openStickerSettings(allianceCode: String) {
        _state.value = _state.value.copy(
            stickerAllianceCode = allianceCode,
            stickerAccessError = null,
        )
        refreshStickerAccess()
    }

    fun closeStickerSettings() {
        _state.value = _state.value.copy(
            stickerAllianceCode = null,
            stickerCatalog = emptyList(),
            stickerRoleGrants = emptyMap(),
            stickerUserGrants = emptyMap(),
            stickerMembers = emptyList(),
            stickerSelectedPackKey = null,
            stickerMemberSearchQuery = "",
        )
    }

    fun setStickerSelectedPack(packKey: String) {
        _state.value = _state.value.copy(stickerSelectedPackKey = packKey)
    }

    fun setStickerMemberSearch(query: String) {
        _state.value = _state.value.copy(stickerMemberSearchQuery = query)
    }

    fun refreshStickerAccess() {
        val code = _state.value.stickerAllianceCode?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(stickerAccessLoading = true, stickerAccessError = null)
            adminRepository.getStickerAccess(code)
                .onSuccess { dto -> applyStickerAccessDto(dto) }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        stickerAccessLoading = false,
                        stickerAccessError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    private fun applyStickerAccessDto(dto: AllianceStickerAccessDto) {
        val roleGrants = dto.roleGrants.mapValues { (_, roles) ->
            roles.map { com.lastasylum.alliance.data.auth.AccountRoles.normalize(it) }.toSet()
        }
        val userGrants = dto.userGrants.mapValues { (_, ids) -> ids.toSet() }
        val selected = _state.value.stickerSelectedPackKey
            ?: dto.catalog.firstOrNull()?.key
        _state.value = _state.value.copy(
            stickerAccessLoading = false,
            stickerCatalog = dto.catalog,
            stickerRoleGrants = roleGrants,
            stickerUserGrants = userGrants,
            stickerMembers = dto.members,
            stickerSelectedPackKey = selected,
        )
    }

    fun toggleStickerAllianceRole(packKey: String, role: String, enabled: Boolean) {
        val next = _state.value.stickerRoleGrants.toMutableMap()
        val set = next[packKey]?.toMutableSet() ?: mutableSetOf()
        if (enabled) set.add(role) else set.remove(role)
        next[packKey] = set
        _state.value = _state.value.copy(stickerRoleGrants = next)
    }

    fun toggleStickerUserGrant(packKey: String, userId: String, enabled: Boolean) {
        val next = _state.value.stickerUserGrants.toMutableMap()
        val set = next[packKey]?.toMutableSet() ?: mutableSetOf()
        if (enabled) set.add(userId) else set.remove(userId)
        next[packKey] = set
        _state.value = _state.value.copy(stickerUserGrants = next)
    }

    fun saveStickerAccess(okMessage: String) {
        val code = _state.value.stickerAllianceCode ?: return
        val catalog = _state.value.stickerCatalog
        viewModelScope.launch {
            _state.value = _state.value.copy(stickerAccessLoading = true, stickerAccessError = null)
            val roleGrants = catalog.associate { item ->
                item.key to (_state.value.stickerRoleGrants[item.key]?.sorted() ?: emptyList())
            }
            val userGrants = catalog.associate { item ->
                item.key to (_state.value.stickerUserGrants[item.key]?.sorted() ?: emptyList())
            }
            adminRepository.putStickerAccess(
                code,
                PutAllianceStickerAccessBody(roleGrants = roleGrants, userGrants = userGrants),
            )
                .onSuccess { dto ->
                    applyStickerAccessDto(dto)
                    _state.value = _state.value.copy(
                        snackMessage = okMessage,
                        stickerAccessLoading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        stickerAccessLoading = false,
                        stickerAccessError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun clearStickerAccessError() {
        _state.value = _state.value.copy(stickerAccessError = null)
    }

    fun openPlayerStickerEditor(allianceCode: String, userId: String) {
        val code = allianceCode.trim()
        if (code.isEmpty() || userId.isBlank()) return
        _state.value = _state.value.copy(
            playerStickerAllianceCode = code,
            playerStickerUserId = userId,
            playerStickerLoading = true,
        )
        viewModelScope.launch {
            adminRepository.getStickerAccess(code)
                .onSuccess { dto ->
                    val packs = dto.catalog.mapNotNull { item ->
                        item.key.takeIf { dto.userGrants[item.key]?.contains(userId) == true }
                    }.toSet()
                    applyStickerAccessDto(dto)
                    _state.value = _state.value.copy(
                        playerStickerPackKeys = packs,
                        playerStickerLoading = false,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        playerStickerLoading = false,
                        actionError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun closePlayerStickerEditor() {
        _state.value = _state.value.copy(
            playerStickerAllianceCode = null,
            playerStickerUserId = null,
            playerStickerPackKeys = emptySet(),
            playerStickerLoading = false,
        )
    }

    fun togglePlayerStickerPack(packKey: String, enabled: Boolean) {
        val next = _state.value.playerStickerPackKeys.toMutableSet()
        if (enabled) next.add(packKey) else next.remove(packKey)
        _state.value = _state.value.copy(playerStickerPackKeys = next)
    }

    fun savePlayerStickerAccess(okMessage: String) {
        val code = _state.value.playerStickerAllianceCode ?: return
        val userId = _state.value.playerStickerUserId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(playerStickerLoading = true)
            adminRepository.patchUserStickerAccess(
                code,
                userId,
                _state.value.playerStickerPackKeys.sorted(),
            )
                .onSuccess { dto ->
                    applyStickerAccessDto(dto)
                    _state.value = _state.value.copy(
                        playerStickerLoading = false,
                        snackMessage = okMessage,
                    )
                    closePlayerStickerEditor()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        playerStickerLoading = false,
                        actionError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun setMembership(userId: String, status: String, okMessage: String) {
        viewModelScope.launch {
            usersRepository.updateMembership(userId, status)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refreshOverview()
                    reloadCurrentList()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(actionError = e.toUserMessageRu(res))
                }
        }
    }

    fun setRole(userId: String, role: String, okMessage: String) {
        viewModelScope.launch {
            usersRepository.updateRole(userId, role)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    reloadCurrentList()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(actionError = e.toUserMessageRu(res))
                }
        }
    }

    fun setUsername(userId: String, username: String, okMessage: String) {
        viewModelScope.launch {
            usersRepository.updateUsername(userId, username)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    reloadCurrentList()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(actionError = e.toUserMessageRu(res))
                }
        }
    }

    fun deleteUser(userId: String, okMessage: String) {
        viewModelScope.launch {
            usersRepository.deleteUser(userId)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refreshOverview()
                    reloadCurrentList()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(actionError = e.toUserMessageRu(res))
                }
        }
    }

    private fun reloadCurrentList() {
        when (val route = _state.value.route) {
            AdminRoute.PlayerTeams -> refreshPlayerTeams()
            is AdminRoute.PlayerTeamDetail ->
                refreshTeamDetailTab(route.teamId, _state.value.teamDetailTab)
            is AdminRoute.ChatRoomViewer -> refreshChatRoomMessages(route.roomId)
            is AdminRoute.ForumTopicViewer ->
                refreshForumTopicMessages(route.teamId, route.topicId)
            AdminRoute.Players -> refreshPlayersScreen()
            else -> Unit
        }
    }
}

fun AdminUserOnServerDto.toTeamMemberDto(): TeamMemberDto =
    TeamMemberDto(
        id = userId,
        username = gameNickname,
        email = email,
        role = accountRole,
        allianceName = "—",
        teamDisplayName = playerTeamDisplayName,
        teamTag = playerTeamTag,
        membershipStatus = membershipStatus,
    )

fun AdminTeamMemberDto.toAdminPlayerRow(team: PlayerTeamAdminDto?): AdminPlayerRow? {
    val identity = identityId?.takeIf { it.isNotBlank() } ?: return null
    return AdminPlayerRow(
        userId = userId,
        identityId = identity,
        accountUsername = accountUsername,
        email = email,
        serverNumber = serverNumber ?: 0,
        gameNickname = gameNickname,
        playerTeamId = team?.id,
        playerTeamTag = team?.tag,
        playerTeamDisplayName = team?.displayName,
        isActiveIdentity = true,
        accountRole = accountRole,
        membershipStatus = membershipStatus,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        appVersionReportedAt = appVersionReportedAt,
    )
}

fun AdminTeamMemberDto.toTeamMemberDto(team: PlayerTeamAdminDto?): TeamMemberDto =
    TeamMemberDto(
        id = userId,
        username = username,
        email = email,
        role = accountRole,
        allianceName = allianceName,
        alliancePublicId = team?.id,
        teamDisplayName = team?.displayName,
        teamTag = team?.tag,
        membershipStatus = membershipStatus,
        presenceStatus = presenceStatus,
        lastPresenceAt = lastPresenceAt,
        lastAppActiveAt = lastAppActiveAt,
        telegramUsername = telegramUsername,
    )
