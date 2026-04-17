package com.lastasylum.alliance.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lastasylum.alliance.data.chat.ChatRoomDto
import com.lastasylum.alliance.data.chat.ChatRoomsRepository
import com.lastasylum.alliance.data.users.TeamMemberDto
import com.lastasylum.alliance.data.users.UsersRepository
import com.lastasylum.alliance.ui.util.toUserMessageRu
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
)

class AdminViewModel(
    application: Application,
    private val usersRepository: UsersRepository,
    private val chatRoomsRepository: ChatRoomsRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AdminUiState(isLoading = true))
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources

    init {
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

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            usersRepository.listMembers()
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

    fun deleteUser(userId: String, okMessage: String) {
        viewModelScope.launch {
            usersRepository.deleteUser(userId)
                .onSuccess {
                    _state.value = _state.value.copy(snackMessage = okMessage)
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.toUserMessageRu(res))
                }
        }
    }
}
