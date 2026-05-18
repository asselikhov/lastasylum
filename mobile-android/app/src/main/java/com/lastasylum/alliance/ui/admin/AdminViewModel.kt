package com.lastasylum.alliance.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.admin.AdminTeamMemberDto
import com.lastasylum.alliance.data.admin.AllianceAdminDto
import com.lastasylum.alliance.data.admin.PlayerTeamAdminDto
import com.lastasylum.alliance.data.admin.PutAllianceStickerAccessBody
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomsRepository
import com.lastasylum.alliance.data.admin.AdminRepository
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminRoute {
    data object Hub : AdminRoute
    data object PlayerTeams : AdminRoute
    data class PlayerTeamDetail(
        val teamId: String,
        val title: String,
    ) : AdminRoute
    data object UsersWithoutTeam : AdminRoute
    data object ChatRouting : AdminRoute
    data object ChatRooms : AdminRoute
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
    val selectedTeam: PlayerTeamAdminDto? = null,
    val teamMembers: List<AdminTeamMemberDto> = emptyList(),
    val teamMembersLoading: Boolean = false,
    val teamMembersError: String? = null,
    val usersWithoutTeam: List<TeamMemberDto> = emptyList(),
    val usersWithoutTeamLoading: Boolean = false,
    val usersWithoutTeamError: String? = null,
    val userSearchQuery: String = "",
    val alliances: List<AllianceAdminDto> = emptyList(),
    val alliancesLoading: Boolean = false,
    val alliancesError: String? = null,
    val stickerAllianceCode: String? = null,
    val stickerAccessLoading: Boolean = false,
    val stickerAccessError: String? = null,
    val stickerRolesZlobyaka: Set<String> = emptySet(),
    val stickerUsersZlobyaka: Set<String> = emptySet(),
    val rooms: List<ChatRoomDto> = emptyList(),
    val roomsLoading: Boolean = false,
    val roomError: String? = null,
    val snackMessage: String? = null,
    val actionError: String? = null,
)

class AdminViewModel(
    application: Application,
    private val usersRepository: UsersRepository,
    private val chatRoomsRepository: ChatRoomsRepository,
    private val adminRepository: AdminRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources
    private var userSearchJob: Job? = null
    private var teamSearchJob: Job? = null

    init {
        refreshOverview()
    }

    fun navigateBack() {
        when (_state.value.route) {
            is AdminRoute.PlayerTeamDetail -> {
                _state.value = _state.value.copy(route = AdminRoute.PlayerTeams)
            }
            AdminRoute.Hub -> Unit
            else -> _state.value = _state.value.copy(route = AdminRoute.Hub)
        }
    }

    fun openRoute(route: AdminRoute) {
        _state.value = _state.value.copy(route = route, actionError = null)
        when (route) {
            AdminRoute.PlayerTeams -> refreshPlayerTeams()
            AdminRoute.UsersWithoutTeam -> refreshUsersWithoutTeam()
            AdminRoute.ChatRouting -> refreshAlliances()
            AdminRoute.ChatRooms -> refreshRooms()
            else -> Unit
        }
    }

    fun openPlayerTeam(team: PlayerTeamAdminDto) {
        _state.value = _state.value.copy(
            route = AdminRoute.PlayerTeamDetail(team.id, "[${team.tag}] ${team.displayName}"),
            selectedTeam = team,
            teamMembers = emptyList(),
            teamMembersError = null,
        )
        refreshTeamMembers(team.id)
    }

    fun clearSnack() {
        _state.value = _state.value.copy(snackMessage = null)
    }

    fun clearActionError() {
        _state.value = _state.value.copy(actionError = null)
    }

    fun clearRoomError() {
        _state.value = _state.value.copy(roomError = null)
    }

    fun refreshOverview() {
        viewModelScope.launch {
            _state.value = _state.value.copy(overviewLoading = true, overviewError = null)
            adminRepository.getOverview()
                .onSuccess { o ->
                    _state.value = _state.value.copy(
                        overviewLoading = false,
                        playerTeamCount = o.playerTeamCount,
                        usersWithoutTeamCount = o.usersWithoutTeamCount,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        overviewLoading = false,
                        overviewError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun refreshPlayerTeams() {
        viewModelScope.launch {
            _state.value = _state.value.copy(playerTeamsLoading = true, playerTeamsError = null)
            adminRepository.listPlayerTeams()
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        playerTeamsLoading = false,
                        playerTeams = list.sortedBy { it.displayName.lowercase() },
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        playerTeamsLoading = false,
                        playerTeamsError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun setTeamSearchQuery(raw: String) {
        _state.value = _state.value.copy(teamSearchQuery = raw)
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

    fun setUserSearchQuery(raw: String) {
        _state.value = _state.value.copy(userSearchQuery = raw)
        userSearchJob?.cancel()
        userSearchJob = viewModelScope.launch {
            delay(350)
            when (_state.value.route) {
                AdminRoute.UsersWithoutTeam -> refreshUsersWithoutTeam()
                else -> Unit
            }
        }
    }

    fun refreshUsersWithoutTeam() {
        viewModelScope.launch {
            val q = _state.value.userSearchQuery.trim().takeIf { it.isNotEmpty() }
            _state.value = _state.value.copy(usersWithoutTeamLoading = true, usersWithoutTeamError = null)
            adminRepository.listUsersWithoutTeam(q = q)
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        usersWithoutTeamLoading = false,
                        usersWithoutTeam = list,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        usersWithoutTeamLoading = false,
                        usersWithoutTeamError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun refreshAlliances() {
        viewModelScope.launch {
            _state.value = _state.value.copy(alliancesLoading = true, alliancesError = null)
            adminRepository.listAlliances()
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        alliancesLoading = false,
                        alliances = list.sortedBy { it.allianceCode.lowercase() },
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        alliancesLoading = false,
                        alliancesError = e.toUserMessageRu(res),
                    )
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
            stickerRolesZlobyaka = emptySet(),
            stickerUsersZlobyaka = emptySet(),
        )
    }

    fun refreshStickerAccess() {
        val code = _state.value.stickerAllianceCode?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(stickerAccessLoading = true, stickerAccessError = null)
            adminRepository.getStickerAccess(code)
                .onSuccess { dto ->
                    _state.value = _state.value.copy(
                        stickerAccessLoading = false,
                        stickerRolesZlobyaka = dto.roleGrants["zlobyaka"]?.toSet() ?: emptySet(),
                        stickerUsersZlobyaka = dto.userGrants["zlobyaka"]?.toSet() ?: emptySet(),
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

    fun toggleStickerAllianceRole(role: String, enabled: Boolean) {
        val next = _state.value.stickerRolesZlobyaka.toMutableSet()
        if (enabled) next.add(role) else next.remove(role)
        _state.value = _state.value.copy(stickerRolesZlobyaka = next)
    }

    fun saveStickerAccess(okMessage: String) {
        val code = _state.value.stickerAllianceCode ?: return
        viewModelScope.launch {
            adminRepository.putStickerAccess(
                code,
                PutAllianceStickerAccessBody(
                    roleGrants = mapOf("zlobyaka" to _state.value.stickerRolesZlobyaka.sorted()),
                    userGrants = mapOf("zlobyaka" to _state.value.stickerUsersZlobyaka.sorted()),
                ),
            )
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refreshStickerAccess()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(stickerAccessError = e.toUserMessageRu(res))
                }
        }
    }

    fun clearStickerAccessError() {
        _state.value = _state.value.copy(stickerAccessError = null)
    }

    fun refreshRooms() {
        viewModelScope.launch {
            _state.value = _state.value.copy(roomsLoading = true, roomError = null)
            chatRoomsRepository.listRooms()
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        roomsLoading = false,
                        rooms = list.sortedWith(compareBy({ it.sortOrder }, { it.title })),
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        roomsLoading = false,
                        roomError = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun createChatRoom(title: String, okMessage: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            chatRoomsRepository.createRoom(title.trim())
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refreshRooms()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(roomError = e.toUserMessageRu(res))
                }
        }
    }

    fun renameChatRoom(roomId: String, title: String, okMessage: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            chatRoomsRepository.updateRoom(roomId, title = title.trim())
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refreshRooms()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(roomError = e.toUserMessageRu(res))
                }
        }
    }

    fun deleteChatRoom(roomId: String, okMessage: String) {
        viewModelScope.launch {
            chatRoomsRepository.deleteRoom(roomId)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refreshRooms()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(roomError = e.toUserMessageRu(res))
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
            is AdminRoute.PlayerTeamDetail -> refreshTeamMembers(route.teamId)
            AdminRoute.UsersWithoutTeam -> refreshUsersWithoutTeam()
            else -> Unit
        }
    }
}

fun AdminTeamMemberDto.toTeamMemberDto(team: PlayerTeamAdminDto?): TeamMemberDto =
    TeamMemberDto(
        id = userId,
        username = username,
        email = email,
        role = allianceRole,
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
