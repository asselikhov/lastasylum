package com.lastasylum.alliance.ui.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
)

class AdminViewModel(
    application: Application,
    private val usersRepository: UsersRepository,
) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AdminUiState(isLoading = true))
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    private val res get() = getApplication<Application>().resources

    init {
        refresh()
    }

    fun clearSnack() {
        _state.value = _state.value.copy(snackMessage = null)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            usersRepository.listMembers()
                .onSuccess { list ->
                    _state.value = AdminUiState(
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
