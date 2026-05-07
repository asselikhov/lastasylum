package com.lastasylum.alliance.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.admin.AllianceAdminDto
import com.lastasylum.alliance.data.admin.AdminRepository
import com.lastasylum.alliance.data.admin.PutAllianceStickerAccessBody
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomsRepository
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.util.toUserMessageRu
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AdminUiState(
    val isLoading: Boolean = false,
    val members: List<TeamMemberDto> = emptyList(),
    val error: String? = null,
    val snackMessage: String? = null,
    val rooms: List<ChatRoomDto> = emptyList(),
    val roomsLoading: Boolean = false,
    val roomError: String? = null,
    val roomSnack: String? = null,
    val alliances: List<AllianceAdminDto> = emptyList(),
    val alliancesLoading: Boolean = false,
    val alliancesError: String? = null,
    /** When null, R5 loads members across all alliances (paginated). */
    val filterAllianceCode: String? = null,
    val memberSearchQuery: String = "",
    val stickerAccessLoading: Boolean = false,
    val stickerAccessError: String? = null,
    /** Alliance roles (R2–R5) that may use pack «zlobyaka». */
    val stickerRolesZlobyaka: Set<String> = emptySet(),
    /** Explicit per-user grants for «zlobyaka». */
    val stickerUsersZlobyaka: Set<String> = emptySet(),
)

class AdminViewModel(
    application: Application,
    private val usersRepository: UsersRepository,
    private val chatRoomsRepository: ChatRoomsRepository,
    private val adminRepository: AdminRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AdminUiState(isLoading = true))
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources

    private var memberSearchJob: Job? = null

    init {
        refreshAlliances()
        refresh()
        refreshRooms()
    }

    fun clearSnack() {
        _state.value = _state.value.copy(snackMessage = null)
    }

    fun clearRoomSnack() {
        _state.value = _state.value.copy(roomSnack = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearAlliancesError() {
        _state.value = _state.value.copy(alliancesError = null)
    }

    fun refresh() {
        viewModelScope.launch {
            val snap = _state.value
            _state.value = snap.copy(isLoading = true, error = null)
            usersRepository.listMembers(
                allianceCode = snap.filterAllianceCode?.takeIf { it.isNotBlank() },
                q = snap.memberSearchQuery.trim().takeIf { it.isNotEmpty() },
                skip = 0,
                limit = 300,
            )
                .onSuccess { list ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        members = list,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.toUserMessageRu(res),
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

    fun setFilterAllianceCode(code: String?) {
        _state.value = _state.value.copy(filterAllianceCode = code)
        refresh()
        refreshStickerAccess()
    }

    fun setMemberSearchQuery(raw: String) {
        _state.value = _state.value.copy(memberSearchQuery = raw)
        memberSearchJob?.cancel()
        memberSearchJob = viewModelScope.launch {
            delay(400)
            refresh()
        }
    }

    fun setAllianceOverlayEnabled(publicId: String, enabled: Boolean, okMessage: String) {
        viewModelScope.launch {
            adminRepository.setOverlayEnabled(publicId, enabled)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refreshAlliances()
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        alliancesError = e.toUserMessageRu(res),
                    )
                }
        }
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
                    _state.value = _state.value.copy(roomSnack = okMessage)
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
                    _state.value = _state.value.copy(roomSnack = okMessage)
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
                    _state.value = _state.value.copy(roomSnack = okMessage)
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
                    refresh()
                    refreshAlliances()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = e.toUserMessageRu(res),
                    )
                }
        }
    }

    fun setRole(userId: String, role: String, okMessage: String) {
        viewModelScope.launch {
            usersRepository.updateRole(userId, role)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toUserMessageRu(res))
                }
        }
    }

    fun setUsername(userId: String, username: String, okMessage: String) {
        viewModelScope.launch {
            usersRepository.updateUsername(userId, username)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toUserMessageRu(res))
                }
        }
    }

    fun refreshStickerAccess() {
        viewModelScope.launch {
            val code = _state.value.filterAllianceCode?.trim()?.takeIf { it.isNotEmpty() }
            if (code == null) {
                _state.value = _state.value.copy(
                    stickerAccessLoading = false,
                    stickerAccessError = null,
                    stickerRolesZlobyaka = emptySet(),
                    stickerUsersZlobyaka = emptySet(),
                )
                return@launch
            }
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
                        stickerRolesZlobyaka = emptySet(),
                        stickerUsersZlobyaka = emptySet(),
                    )
                }
        }
    }

    fun clearStickerAccessError() {
        _state.value = _state.value.copy(stickerAccessError = null)
    }

    fun toggleStickerAllianceRole(role: String, enabled: Boolean) {
        val next = _state.value.stickerRolesZlobyaka.toMutableSet()
        if (enabled) next.add(role) else next.remove(role)
        _state.value = _state.value.copy(stickerRolesZlobyaka = next)
    }

    fun toggleStickerUserGrant(userId: String, enabled: Boolean) {
        val next = _state.value.stickerUsersZlobyaka.toMutableSet()
        if (enabled) next.add(userId) else next.remove(userId)
        _state.value = _state.value.copy(stickerUsersZlobyaka = next)
    }

    fun saveStickerAccess(okMessage: String) {
        val code = _state.value.filterAllianceCode?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            val roles = _state.value.stickerRolesZlobyaka.toList().sorted()
            val users = _state.value.stickerUsersZlobyaka.toList().sorted()
            adminRepository.putStickerAccess(
                code,
                PutAllianceStickerAccessBody(
                    roleGrants = mapOf("zlobyaka" to roles),
                    userGrants = mapOf("zlobyaka" to users),
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

    fun deleteUser(userId: String, okMessage: String) {
        viewModelScope.launch {
            usersRepository.deleteUser(userId)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refresh()
                    refreshAlliances()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toUserMessageRu(res))
                }
        }
    }
}
